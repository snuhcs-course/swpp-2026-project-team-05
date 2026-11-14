package com.swpp.team5.frameless.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewsRepositoryTest {
    private val publisher = PublisherItem("yna", "연합뉴스", null)
    private val session = AppSession(
        NewsUser("u1", null, "reader", false), "token", "2099-11-30T00:00:00+09:00"
    )
    private val article = ArticleDetail(
        "a1", publisher, "정책 추진", "정책을 전격 추진한다.", NewsCategory.POLITICS,
        "2026-10-31T12:00:00+09:00", "https://example.com/a1", StoredAnalysisState.READY,
        contentHash = "hash-a1"
    )

    @Test
    fun preferencesRoundTripAndNetworkCallsUseAuthenticatedService() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences(
            "frameless_news_preferences", Context.MODE_PRIVATE
        )
        preferences.edit().clear().putString("publishers", "legacy-local-source").commit()
        val service = FakeNewsService()
        val cache = FakeNewsCache().apply {
            cachedArticle = article
            cachedAnalysis = service.analysis(null, article.id)
        }
        val repository = NewsRepository(context, service, FakeAuthDataSource(session), cache)

        assertEquals(NewsCategory.POLITICS, repository.savedCategory())
        repository.saveCategory(NewsCategory.SOCIETY)
        repository.saveAnalysisEnabled(false)
        assertEquals(NewsCategory.SOCIETY, repository.savedCategory())
        assertFalse(repository.savedAnalysisEnabled())
        assertFalse(preferences.contains("publishers"))

        assertEquals(listOf(publisher), repository.publishers())
        assertEquals(0, service.followReplacementCalls)
        assertEquals(setOf("yna"), repository.replaceFollowedPublishers(setOf("yna")))
        assertEquals(1, service.followReplacementCalls)
        assertEquals(
            "next",
            repository.articles(NewsCategory.POLITICS, FeedScope.FOLLOWING, query = "정책").nextCursor
        )
        assertEquals("정책", service.lastQuery)
        assertEquals(FeedScope.FOLLOWING, service.lastScope)
        assertEquals(article, repository.article("a1"))
        assertEquals("token", service.lastToken)
        assertEquals("a1", service.lastArticleId)

        assertEquals("saved", repository.savedArticles().nextCursor)
        repository.setArticleSaved("a1", true)
        assertEquals(true, service.lastSavedState)
        repository.setArticleSaved("a1", false)
        assertEquals(false, service.lastSavedState)
    }

    @Test
    fun analysisMapsVerifiedOffsetsAndGuestUsesPublicReads() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val service = FakeNewsService()
        val repository = NewsRepository(
            context, service, FakeAuthDataSource(session), FakeNewsCache()
        )
        val mapped = repository.analysis(article)
        assertEquals("전격", mapped.findings.single().markerText)

        val loggedOut = NewsRepository(
            context, service, FakeAuthDataSource(null), FakeNewsCache()
        )
        assertEquals(listOf(publisher), loggedOut.publishers())
        assertEquals(null, service.lastToken)
        assertEquals(article, loggedOut.article("a1"))
    }

    @Test
    fun savePreparesOfflineCopyBeforeServerMutation() = runBlocking {
        val events = mutableListOf<String>()
        val service = FakeNewsService(events)
        val cache = FakeNewsCache(events)
        val repository = NewsRepository(
            RuntimeEnvironment.getApplication(), service, FakeAuthDataSource(session), cache
        )

        repository.setArticleSaved("a1", true)

        assertEquals(
            listOf(
                "remote-detail", "cache-detail", "remote-analysis", "cache-analysis",
                "server-save", "cache-relation"
            ),
            events
        )
        assertTrue(cache.cachedArticle != null)
        assertTrue(cache.cachedAnalysis != null)
    }

    @Test
    fun failedOfflineCopyPreparationDoesNotSave() = runBlocking {
        val events = mutableListOf<String>()
        val service = FakeNewsService(events)
        val cache = FakeNewsCache(events).apply { acceptAnalysis = false }
        val repository = NewsRepository(
            RuntimeEnvironment.getApplication(), service, FakeAuthDataSource(session), cache
        )

        val failure = runCatching { repository.setArticleSaved("a1", true) }.exceptionOrNull()

        assertTrue(failure is NewsApiException)
        assertEquals("OFFLINE_COPY_UNAVAILABLE", (failure as NewsApiException).code)
        assertFalse(events.contains("server-save"))
        assertFalse(events.contains("cache-relation"))
    }

    private class FakeAuthDataSource(private val session: AppSession?) : AuthDataSource {
        override fun currentSession() = session
        override suspend fun login(identifier: String, password: String) = error("unused")
        override suspend fun register(email: String, username: String, password: String,
                                      confirmation: String) = error("unused")
        override suspend fun restore() = session
        override suspend fun logout() = Unit
        override fun clear() = Unit
    }

    private inner class FakeNewsService(
        private val events: MutableList<String> = mutableListOf()
    ) : NewsService {
        var lastToken: String? = null
        var lastArticleId: String? = null
        var lastQuery: String? = null
        var lastScope: FeedScope? = null
        var lastSavedState: Boolean? = null
        var followReplacementCalls = 0
        override suspend fun register(email: String, username: String, password: String,
                                      confirmation: String) = error("unused")
        override suspend fun login(identifier: String, password: String) = error("unused")
        override suspend fun me(token: String) = error("unused")
        override suspend fun logout(token: String) = Unit
        override suspend fun publishers(token: String?): List<PublisherItem> {
            lastToken = token
            return listOf(publisher)
        }
        override suspend fun replaceFollowedPublishers(
            token: String, publisherCodes: Set<String>
        ): Set<String> {
            lastToken = token
            followReplacementCalls += 1
            return publisherCodes
        }
        override suspend fun articles(token: String?, category: NewsCategory?,
                                      scope: FeedScope, cursor: String?, limit: Int,
                                      query: String?): ArticlePage {
            lastToken = token
            lastQuery = query
            lastScope = scope
            return ArticlePage(emptyList(), "next")
        }
        override suspend fun article(token: String?, articleId: String): ArticleDetail {
            events += "remote-detail"
            lastToken = token
            lastArticleId = articleId
            return article
        }
        override suspend fun analysis(token: String?, articleId: String): ApiArticleAnalysis {
            events += "remote-analysis"
            lastToken = token
            return ApiArticleAnalysis(
                StoredAnalysisState.READY, "v1", listOf(ApiFinding(
                    "f1", listOf("D"), "BODY",
                    ApiSentence(0, article.body.length, article.body),
                    ApiMarker(4, 6, "전격"), "속도를 강조한다.", "", emptyList()
                )), sourceContentHash = article.contentHash, rawJson = "valid-json"
            )
        }
        override suspend fun savedArticles(token: String, cursor: String?, limit: Int) =
            ArticlePage(emptyList(), "saved")
        override suspend fun saveArticle(token: String, articleId: String) {
            events += "server-save"
            lastSavedState = true
        }
        override suspend fun removeSavedArticle(token: String, articleId: String) {
            lastSavedState = false
        }
    }

    private class FakeNewsCache(
        private val events: MutableList<String> = mutableListOf()
    ) : NewsCacheDataSource {
        var cachedArticle: ArticleDetail? = null
        var cachedAnalysis: ApiArticleAnalysis? = null
        var acceptAnalysis = true

        override suspend fun article(articleId: String) =
            cachedArticle?.takeIf { it.id == articleId }

        override suspend fun storeArticle(article: ArticleDetail) {
            events += "cache-detail"
            cachedArticle = article
        }

        override suspend fun analysis(article: ArticleDetail) = cachedAnalysis

        override suspend fun storeAnalysis(
            article: ArticleDetail,
            analysis: ApiArticleAnalysis
        ): Boolean {
            events += "cache-analysis"
            if (acceptAnalysis) cachedAnalysis = analysis
            return acceptAnalysis
        }

        override suspend fun offlineSavedArticles(userId: String, category: NewsCategory?) = null
        override suspend fun storeSavedPage(userId: String, page: ArticlePage, replace: Boolean) = Unit

        override suspend fun setSaved(userId: String, articleId: String, saved: Boolean) {
            events += "cache-relation"
        }

        override suspend fun clearSaved(userId: String) = Unit
    }
}
