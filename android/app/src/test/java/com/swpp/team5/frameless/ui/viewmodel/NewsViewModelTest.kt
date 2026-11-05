package com.swpp.team5.frameless.ui.viewmodel

import android.os.Looper
import com.swpp.team5.frameless.data.ArticleAnalysisUi
import com.swpp.team5.frameless.data.ArticleDetail
import com.swpp.team5.frameless.data.ArticlePage
import com.swpp.team5.frameless.data.ArticleSummary
import com.swpp.team5.frameless.data.FeedScope
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
import java.util.concurrent.TimeUnit

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
    fun initializeLoadsPublishersAndTheCompleteLatestFeed() {
        val repository = FakeNewsDataSource().apply {
            savedCategoryValue = NewsCategory.SOCIETY
            pages += ArticlePage(listOf(summary("a")), "next")
        }
        val viewModel = NewsViewModel(repository)

        assertEquals(NewsCategory.SOCIETY, viewModel.feed.category)
        viewModel.initialize()
        mainLooper.idle()

        assertEquals(listOf(yna, mbc), viewModel.feed.publishers)
        assertEquals(FeedScope.ALL, viewModel.feed.scope)
        assertEquals(FeedScope.ALL, repository.scopes.single())
        assertEquals(listOf("a"), viewModel.feed.items.map { it.id })
        assertEquals("next", viewModel.feed.nextCursor)
        assertFalse(viewModel.feed.isLoading)
        viewModel.initialize()
        mainLooper.idle()
        assertEquals(1, repository.articlePageCalls)
    }

    @Test
    fun reviewedExamplesLeadOnlyTheLatestFeed() {
        val regular = summary("regular")
        val panda = summary("panda").copy(
            title = "에버랜드 막내 판다 '슈바오' 30일 첫 나들이…쌍둥이 언니들은 작별 준비"
        )
        val hd = summary("hd").copy(
            title = "HD현대일렉트릭, 3분기 매출 1조1799억원 '역대 최대'"
        )
        val serverOrder = listOf(regular, panda, hd)
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(serverOrder, null)
            pages += ArticlePage(serverOrder, null)
            pages += ArticlePage(serverOrder, null)
            searchPages.getValue(NewsCategory.POLITICS) += ArticlePage(serverOrder, null, 3)
            searchPages.getValue(NewsCategory.SOCIETY) += ArticlePage(emptyList(), null, 0)
        }
        val viewModel = NewsViewModel(repository)

        viewModel.initialize()
        mainLooper.idle()
        assertEquals(listOf("hd", "panda", "regular"), viewModel.feed.items.map { it.id })

        viewModel.selectScope(FeedScope.FOLLOWING)
        mainLooper.idle()
        assertEquals(listOf("regular", "panda", "hd"), viewModel.feed.items.map { it.id })

        viewModel.selectScope(FeedScope.ALL)
        mainLooper.idle()
        viewModel.openSearch()
        viewModel.updateSearchInput("분석")
        mainLooper.idleFor(301, TimeUnit.MILLISECONDS)
        assertEquals(
            listOf("regular", "panda", "hd"),
            viewModel.feed.searchSections.getValue(NewsCategory.POLITICS).items.map { it.id }
        )
    }

    @Test
    fun categoryScopeRefreshAndPaginationPreserveExpectedState() {
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
        assertEquals(NewsCategory.SOCIETY, repository.savedCategories.last())
        assertEquals(listOf("society"), viewModel.feed.items.map { it.id })

        viewModel.selectScope(FeedScope.FOLLOWING)
        mainLooper.idle()
        assertEquals(FeedScope.FOLLOWING, viewModel.feed.scope)
        assertEquals(listOf("filtered"), viewModel.feed.items.map { it.id })

        viewModel.loadMore()
        mainLooper.idle()
        assertEquals(listOf("filtered", "more"), viewModel.feed.items.map { it.id })
        viewModel.refresh()
        mainLooper.idle()
        assertEquals(listOf("refreshed"), viewModel.feed.items.map { it.id })
    }

    @Test
    fun sourcePreferencesAreAccountBackedAndFollowingReloadsAfterSave() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("latest")), null)
            pages += ArticlePage(emptyList(), null)
            pages += ArticlePage(listOf(summary("followed", mbc)), null)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.selectScope(FeedScope.FOLLOWING)
        mainLooper.idle()

        var completed = false
        viewModel.replaceFollowedPublishers(setOf("mbc", "unknown")) { completed = true }
        mainLooper.idle()

        assertEquals(setOf("mbc"), repository.followedCodes)
        assertTrue(viewModel.feed.publishers.single { it.code == "mbc" }.isFollowed)
        assertEquals(listOf("followed"), viewModel.feed.items.map { it.id })
        assertTrue(completed)
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
    fun searchDebouncesServerQueryAndClosingRestoresFeed() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("ordinary")), null)
            searchPages.getValue(NewsCategory.POLITICS) +=
                ArticlePage(listOf(summary("politics-result")), "politics-more", 2)
            searchPages.getValue(NewsCategory.SOCIETY) +=
                ArticlePage(listOf(summary("society-result").copy(category = NewsCategory.SOCIETY)), null, 1)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()

        viewModel.openSearch()
        viewModel.updateSearchInput("정")
        assertTrue(viewModel.feed.items.isEmpty())
        assertEquals("", viewModel.feed.activeSearchQuery)

        viewModel.updateSearchInput("정책")
        viewModel.updateSearchInput("정책 추진")
        mainLooper.idleFor(301, TimeUnit.MILLISECONDS)

        assertEquals("정책 추진", repository.queries.last())
        assertEquals(FeedScope.ALL, repository.scopes.last())
        assertEquals(
            listOf("politics-result"),
            viewModel.feed.searchSections.getValue(NewsCategory.POLITICS).items.map { it.id }
        )
        assertEquals(2, viewModel.feed.searchSections.getValue(NewsCategory.POLITICS).totalCount)
        assertEquals(
            listOf("society-result"),
            viewModel.feed.searchSections.getValue(NewsCategory.SOCIETY).items.map { it.id }
        )
        assertEquals(
            listOf(NewsCategory.POLITICS, NewsCategory.SOCIETY),
            repository.categories.takeLast(2)
        )
        assertEquals("정책 추진", viewModel.feed.activeSearchQuery)

        repository.searchPages.getValue(NewsCategory.POLITICS) +=
            ArticlePage(listOf(summary("politics-result-2")), null, 2)
        viewModel.loadMoreSearch(NewsCategory.POLITICS)
        mainLooper.idle()
        assertEquals(
            listOf("politics-result", "politics-result-2"),
            viewModel.feed.searchSections.getValue(NewsCategory.POLITICS).items.map { it.id }
        )
        assertEquals(
            listOf("society-result"),
            viewModel.feed.searchSections.getValue(NewsCategory.SOCIETY).items.map { it.id }
        )

        viewModel.closeSearch()
        assertFalse(viewModel.feed.isSearchOpen)
        assertEquals(listOf("ordinary"), viewModel.feed.items.map { it.id })
        assertEquals(4, repository.articlePageCalls)
    }

    @Test
    fun searchRetriesOneCategoryWithoutDiscardingTheOther() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("ordinary")), null)
            categoryFailures[NewsCategory.POLITICS] = IllegalStateException("정치 검색 실패")
            searchPages.getValue(NewsCategory.SOCIETY) += ArticlePage(
                listOf(summary("society-ok").copy(category = NewsCategory.SOCIETY)), null, 1
            )
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()
        viewModel.openSearch()
        viewModel.updateSearchInput("검색 오류")
        mainLooper.idleFor(301, TimeUnit.MILLISECONDS)

        assertEquals(
            "정치 검색 실패",
            viewModel.feed.searchSections.getValue(NewsCategory.POLITICS).error
        )
        assertEquals(
            listOf("society-ok"),
            viewModel.feed.searchSections.getValue(NewsCategory.SOCIETY).items.map { it.id }
        )

        repository.categoryFailures.remove(NewsCategory.POLITICS)
        repository.searchPages.getValue(NewsCategory.POLITICS) +=
            ArticlePage(listOf(summary("politics-retried")), null, 1)
        viewModel.retrySearch(NewsCategory.POLITICS)
        mainLooper.idle()

        assertEquals(
            listOf("politics-retried"),
            viewModel.feed.searchSections.getValue(NewsCategory.POLITICS).items.map { it.id }
        )
        assertEquals(
            listOf("society-ok"),
            viewModel.feed.searchSections.getValue(NewsCategory.SOCIETY).items.map { it.id }
        )
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

    @Test
    fun savedRouteLoadsPagesAndReturnsToPreservedFeed() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("ordinary")), null)
            savedPages += ArticlePage(listOf(summary("saved-1").copy(isSaved = true)), "next")
            savedPages += ArticlePage(listOf(summary("saved-2").copy(isSaved = true)), null)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()

        viewModel.openSaved()
        mainLooper.idle()
        assertEquals(NewsRoute.SAVED, viewModel.route)
        assertEquals(listOf("saved-1"), viewModel.saved.items.map { it.id })
        viewModel.loadMoreSaved()
        mainLooper.idle()
        assertEquals(listOf("saved-1", "saved-2"), viewModel.saved.items.map { it.id })

        viewModel.openArticle("saved-1")
        mainLooper.idle()
        viewModel.closeReader()
        assertEquals(NewsRoute.SAVED, viewModel.route)
        viewModel.closeSaved()
        assertEquals(NewsRoute.FEED, viewModel.route)
        assertEquals(listOf("ordinary"), viewModel.feed.items.map { it.id })
    }

    @Test
    fun saveMutationUpdatesAllStateAndRollsBackAfterFailure() {
        val repository = FakeNewsDataSource().apply {
            pages += ArticlePage(listOf(summary("a")), null)
        }
        val viewModel = NewsViewModel(repository)
        viewModel.initialize()
        mainLooper.idle()

        viewModel.toggleSaved("a", false)
        assertTrue(viewModel.feed.items.single().isSaved)
        mainLooper.runToEndOfTasks()
        assertEquals(listOf("a" to true), repository.savedMutations)
        assertEquals(null, viewModel.savedMutationArticleId)

        repository.savedMutationFailure = IllegalStateException("저장 실패")
        viewModel.toggleSaved("a", true)
        mainLooper.runToEndOfTasks()
        assertTrue(viewModel.feed.items.single().isSaved)
        assertEquals("저장 실패", viewModel.saveError)
    }

    private inner class FakeNewsDataSource : NewsDataSource {
        val pages = ArrayDeque<ArticlePage>()
        val searchPages = NewsCategory.entries.associateWith { ArrayDeque<ArticlePage>() }
        val savedPages = ArrayDeque<ArticlePage>()
        var pageFailure: Exception? = null
        val categoryFailures = mutableMapOf<NewsCategory, Exception>()
        var articleFailure: Exception? = null
        var analysisFailure: Exception? = null
        var savedMutationFailure: Exception? = null
        var savedCategoryValue = NewsCategory.POLITICS
        var savedAnalysisEnabledValue = true
        var followedCodes = emptySet<String>()
        var articlePageCalls = 0
        val queries = mutableListOf<String?>()
        val scopes = mutableListOf<FeedScope>()
        val categories = mutableListOf<NewsCategory>()
        val savedCategories = mutableListOf<NewsCategory>()
        val savedMutations = mutableListOf<Pair<String, Boolean>>()

        override suspend fun publishers() = listOf(yna, mbc)
        override suspend fun replaceFollowedPublishers(publisherCodes: Set<String>): Set<String> {
            followedCodes = publisherCodes
            return publisherCodes
        }
        override suspend fun articles(category: NewsCategory, scope: FeedScope,
                                      cursor: String?, query: String?): ArticlePage {
            articlePageCalls += 1
            queries += query
            scopes += scope
            categories += category
            pageFailure?.let { throw it }
            if (query != null) categoryFailures[category]?.let { throw it }
            return if (query == null) pages.removeFirst()
            else searchPages.getValue(category).removeFirst()
        }
        override suspend fun article(articleId: String): ArticleDetail {
            articleFailure?.let { throw it }
            return detail(articleId)
        }
        override suspend fun analysis(article: ArticleDetail): ArticleAnalysisUi {
            analysisFailure?.let { throw it }
            return ArticleAnalysisUi(StoredAnalysisState.READY, "reviewed-v1", emptyList())
        }
        override suspend fun savedArticles(cursor: String?): ArticlePage = savedPages.removeFirst()
        override suspend fun setArticleSaved(articleId: String, saved: Boolean) {
            savedMutationFailure?.let { throw it }
            savedMutations += articleId to saved
        }
        override fun savedCategory() = savedCategoryValue
        override fun savedAnalysisEnabled() = savedAnalysisEnabledValue
        override fun saveCategory(category: NewsCategory) {
            savedCategoryValue = category
            savedCategories += category
        }
        override fun saveAnalysisEnabled(enabled: Boolean) {
            savedAnalysisEnabledValue = enabled
        }
    }
}
