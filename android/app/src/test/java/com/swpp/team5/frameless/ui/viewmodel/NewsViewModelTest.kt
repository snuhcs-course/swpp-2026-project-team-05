package com.swpp.team5.frameless.ui.viewmodel

import android.os.Looper
import com.swpp.team5.frameless.data.ArticleAnalysisUi
import com.swpp.team5.frameless.data.ArticleDetail
import com.swpp.team5.frameless.data.ArticlePage
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.NewsApiException
import com.swpp.team5.frameless.data.NewsCategory
import com.swpp.team5.frameless.data.NewsDataSource
import com.swpp.team5.frameless.data.PublisherItem
import com.swpp.team5.frameless.data.StoredAnalysisState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@LooperMode(LooperMode.Mode.PAUSED)
class NewsViewModelTest {
    private val mainLooper get() = shadowOf(Looper.getMainLooper())
    private val yna = PublisherItem("yna", "연합뉴스", null)
    private val mbc = PublisherItem("mbc", "MBC", null)

    private fun summary(id: String, publisher: PublisherItem = yna) = ArticleSummary(
        id, publisher, "기사 $id", NewsCategory.POLITICS,
        "2026-10-31T12:00:00+09:00", StoredAnalysisState.READY
    )

    private fun detail(id: String) = ArticleDetail(
        id, yna, "기사 $id", "본문 $id", NewsCategory.POLITICS,
        "2026-10-31T12:00:00+09:00", "https://example.com/$id", StoredAnalysisState.READY
    )

    @Test
    fun initializeLoadsPublishersAndFirstPageAndDropsUnknownSavedFilter() {
        val repository = FakeNewsDataSource().apply {
            savedCategoryValue = NewsCategory.SOCIETY
            savedPublisherValue = setOf("yna", "removed")
            pages += ArticlePage(listOf(summary("a")), "next")
        }
        val viewModel = NewsViewModel(repository)

        assertEquals(NewsCategory.SOCIETY, viewModel.feed.category)
        viewModel.initialize()
        mainLooper.idle()

        assertEquals(listOf(yna, mbc), viewModel.feed.publishers)
        assertEquals(setOf("yna"), viewModel.feed.selectedPublishers)
        assertEquals(listOf("a"), viewModel.feed.items.map { it.id })
        assertEquals("next", viewModel.feed.nextCursor)
        assertFalse(viewModel.feed.isLoading)
        viewModel.initialize()
        mainLooper.idle()
        assertEquals(1, repository.articlePageCalls)
    }

    @Test
    fun categoryPublisherRefreshAndPaginationPreserveExpectedState() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("first")), "page-2")
            pages += ArticlePage(listOf(summary("society")), null)
            pages += ArticlePage(listOf(summary("filtered", mbc)), "more")
            pages += ArticlePage(listOf(summary("more", mbc)), null)
            pages += ArticlePage(listOf(summary("refreshed", mbc)), null)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()

        viewModel.selectCategory(NewsCategory.SOCIETY)
        mainLooper.idle()
        assertEquals(NewsCategory.SOCIETY, repository.savedFilters.last().first)
        assertEquals(listOf("society"), viewModel.feed.items.map { it.id })

        viewModel.applyPublishers(setOf("mbc", "unknown"))
        mainLooper.idle()
        assertEquals(setOf("mbc"), viewModel.feed.selectedPublishers)
        assertEquals(listOf("filtered"), viewModel.feed.items.map { it.id })

        viewModel.loadMore()
        mainLooper.idle()
        assertEquals(listOf("filtered", "more"), viewModel.feed.items.map { it.id })
        viewModel.refresh()
        mainLooper.idle()
        assertEquals(listOf("refreshed"), viewModel.feed.items.map { it.id })
    }

    @Test
    fun feedFailuresAreRetryableAndAuthenticationFailureExpiresSession() {
        val repository = FakeNewsDataSource().apply {
            pageFailure = IllegalStateException("피드 실패")
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        assertEquals("피드 실패", viewModel.feed.error)

        repository.pageFailure = null
        repository.pages += ArticlePage(listOf(summary("ok")), "more")
        viewModel.retryFeed()
        mainLooper.idle()
        repository.pageFailure = NewsApiException("AUTH_REQUIRED", "로그인이 필요합니다.")
        viewModel.loadMore()
        mainLooper.idle()
        assertTrue(viewModel.authExpired)
        assertEquals("로그인이 필요합니다.", viewModel.feed.nextPageError)
        viewModel.consumeAuthExpired()
        assertFalse(viewModel.authExpired)
    }

    @Test
    fun readerLoadsArticleAndAnalysisAndKeepsAnalysisPreference() {
        val repository = FakeNewsDataSource()
        val viewModel = NewsViewModel(repository)

        viewModel.openArticle("a")
        mainLooper.idle()
        assertEquals(NewsRoute.READER, viewModel.route)
        assertEquals("a", viewModel.reader.article?.id)
        assertEquals(StoredAnalysisState.READY, viewModel.reader.analysis?.state)
        assertFalse(viewModel.reader.isArticleLoading)
        assertFalse(viewModel.reader.isAnalysisLoading)

        viewModel.setAnalysisEnabled(false)
        assertFalse(viewModel.reader.analysisEnabled)
        assertEquals(false, repository.savedAnalysisEnabledValue)
        viewModel.closeReader()
        assertEquals(NewsRoute.FEED, viewModel.route)
    }

    @Test
    fun readerSeparatesArticleAndAnalysisFailuresAndResetRestoresDefaults() {
        val repository = FakeNewsDataSource().apply {
            articleFailure = IllegalStateException("기사 실패")
        }
        val viewModel = NewsViewModel(repository)
        viewModel.openArticle("bad")
        mainLooper.idle()
        assertEquals("기사 실패", viewModel.reader.articleError)

        repository.articleFailure = null
        repository.analysisFailure = NewsApiException("AUTH_REQUIRED", "분석 인증 만료")
        viewModel.retryReader()
        mainLooper.idle()
        assertEquals("분석 인증 만료", viewModel.reader.analysisError)
        assertTrue(viewModel.authExpired)

        viewModel.reset()
        assertEquals(NewsRoute.FEED, viewModel.route)
        assertTrue(viewModel.feed.items.isEmpty())
        assertFalse(viewModel.authExpired)
    }

    private inner class FakeNewsDataSource : NewsDataSource {
        val pages = ArrayDeque<ArticlePage>()
        var pageFailure: Exception? = null
        var articleFailure: Exception? = null
        var analysisFailure: Exception? = null
        var savedCategoryValue = NewsCategory.POLITICS
        var savedPublisherValue = emptySet<String>()
        var savedAnalysisEnabledValue = true
        var articlePageCalls = 0
        val savedFilters = mutableListOf<Pair<NewsCategory, Set<String>>>()

        override suspend fun publishers() = listOf(yna, mbc)
        override suspend fun articles(category: NewsCategory, publisherCodes: Set<String>, cursor: String?): ArticlePage {
            articlePageCalls += 1
            pageFailure?.let { throw it }
            return pages.removeFirst()
        }
        override suspend fun article(articleId: String): ArticleDetail {
            articleFailure?.let { throw it }
            return detail(articleId)
        }
        override suspend fun analysis(article: ArticleDetail): ArticleAnalysisUi {
            analysisFailure?.let { throw it }
            return ArticleAnalysisUi(StoredAnalysisState.READY, "reviewed-v1", emptyList())
        }
        override fun savedCategory() = savedCategoryValue
        override fun savedPublishers() = savedPublisherValue
        override fun savedAnalysisEnabled() = savedAnalysisEnabledValue
        override fun saveFilters(category: NewsCategory, publishers: Set<String>) {
            savedCategoryValue = category
            savedPublisherValue = publishers
            savedFilters += category to publishers
        }
        override fun saveAnalysisEnabled(enabled: Boolean) {
            savedAnalysisEnabledValue = enabled
        }
    }
}
