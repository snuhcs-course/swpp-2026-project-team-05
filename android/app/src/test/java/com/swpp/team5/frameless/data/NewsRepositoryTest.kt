package com.swpp.team5.frameless.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
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

    private fun onlineRepository(context: Context, service: NewsService,
        auth: AuthDataSource, cache: NewsCacheDataSource, reads: NewsReadCache = NewsReadCache()) =
        NewsRepository(context, service, auth, cache, reads, connection = { NewsConnection.ONLINE })

    @Test
    fun anUnvalidatedNetworkAttemptsTheServerRatherThanClaimingOffline() = runBlocking {
        val service = FakeNewsService()
        val repository = NewsRepository(RuntimeEnvironment.getApplication(), service,
            FakeAuthDataSource(session), FakeNewsCache(), connection = { NewsConnection.UNVERIFIED })
        repeat(2) { assertEquals(listOf(publisher), repository.publishers()) }
        assertEquals(2, service.publisherCalls)
    }

    @Test
    fun oldViewersDelayedResponseCannotReplaceTheNewViewersCache() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<List<PublisherItem>>()
        val auth = FakeAuthDataSource(session)
        val source = FakeNewsService().apply {
            publisherRead = { token ->
                if (token == session.token) { entered.complete(Unit); gate.await() }
                else listOf(publisher.copy(isFollowed = true))
            }
        }
        val repository = onlineRepository(RuntimeEnvironment.getApplication(), source, auth, FakeNewsCache())
        val old = async(start = CoroutineStart.UNDISPATCHED) { repository.publishers() }
        entered.await()
        auth.session = session.copy(user = session.user.copy(id = "u2"), token = "new-token")
        assertTrue(repository.publishers().single().isFollowed)
        gate.complete(listOf(publisher))
        assertFalse(old.await().single().isFollowed)
        assertTrue(repository.publishers().single().isFollowed)
        assertEquals(2, source.publisherCalls)
    }

    @Test
    fun mismatchedAnalysisAndConfirmedDeletionAreNotCachedAsSuccess() = runBlocking {
        val source = FakeNewsService()
        val disk = FakeNewsCache()
        val repository = onlineRepository(RuntimeEnvironment.getApplication(), source, FakeAuthDataSource(session), disk)
        source.analysisHash = "old-body"
        val mismatch = runCatching { repository.analysis(article) }.exceptionOrNull()
        assertEquals("ANALYSIS_VERSION_MISMATCH", (mismatch as NewsApiException).code)
        assertEquals(null, disk.cachedAnalysis)
        source.analysisHash = article.contentHash
        assertEquals("v1", repository.analysis(article).version)
        source.detailFailure = NewsApiException("NOT_FOUND", "removed")
        assertTrue(runCatching { repository.article(article.id) }.isFailure)
        assertEquals(listOf(article.id to session.user.id), disk.removed)
    }

    @Test
    fun offlineReadsDoNotReuseOnlineHeadlinesAndPublishersShareTheirFirstPage() = runBlocking {
        val service = FakeNewsService()
        var connected = true
        val repository = NewsRepository(RuntimeEnvironment.getApplication(), service,
            FakeAuthDataSource(session), FakeNewsCache(), connection = { if (connected) NewsConnection.ONLINE else NewsConnection.OFFLINE })
        repeat(2) { repository.publisherArticles("yna") }
        assertEquals(1, service.listCalls)
        connected = false
        val failure = runCatching { repository.publisherArticles("yna") }.exceptionOrNull()
        assertEquals("NETWORK_ERROR", (failure as NewsApiException).code)
        assertEquals(1, service.listCalls)
    }

    @Test
    fun freshReaderAndListReadsAreReusedAndRefreshBypassesThem() = runBlocking {
        val events = mutableListOf<String>()
        var now = 0L
        val source = FakeNewsService(events)
        val repository = onlineRepository(RuntimeEnvironment.getApplication(), source,
            FakeAuthDataSource(session), FakeNewsCache(), NewsReadCache({ now }))
        repeat(2) { repository.article(article.id); repository.analysis(article) }
        assertEquals(1, events.count { it == "remote-detail" })
        assertEquals(1, events.count { it == "remote-analysis" })
        repository.article(article.id, refresh = true)
        repository.analysis(article, refresh = true)
        assertEquals(2, events.count { it == "remote-detail" })
        now = 300_000
        repository.article(article.id)
        assertEquals(3, events.count { it == "remote-detail" })
        repeat(2) { repository.articles(null, FeedScope.ALL) }
        assertEquals(1, source.listCalls)
        repository.articles(null, FeedScope.ALL, cursor = "next")
        repository.articles(null, FeedScope.ALL, refresh = true)
        repository.articles(null, FeedScope.ALL, cursor = "next")
        assertEquals(4, source.listCalls)
    }

    @Test
    fun credentialsAndMutationsInvalidatePersonalizedResponses() = runBlocking {
        val source = FakeNewsService()
        val auth = FakeAuthDataSource(session)
        val repository = onlineRepository(RuntimeEnvironment.getApplication(), source, auth, FakeNewsCache())
        repository.publishers()
        repository.publishers()
        assertEquals(1, source.publisherCalls)
        repository.replaceFollowedPublishers(setOf("yna"))
        repository.publishers()
        assertEquals(2, source.publisherCalls)
        auth.session = session.copy(user = session.user.copy(id = "u2"), token = "other-token")
        repository.publishers()
        assertEquals("other-token", source.lastToken)
        assertEquals(3, source.publisherCalls)
        repository.articles(null, FeedScope.ALL)
        repository.setArticleSaved(article.id, true)
        repository.articles(null, FeedScope.ALL)
        assertEquals(2, source.listCalls)
    }

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
        val repository = onlineRepository(context, service, FakeAuthDataSource(session), cache)

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
        val repository = onlineRepository(
            context, service, FakeAuthDataSource(session), FakeNewsCache()
        )
        val mapped = repository.analysis(article)
        assertEquals("전격", mapped.findings.single().markerText)

        val loggedOut = onlineRepository(
            context, service, FakeAuthDataSource(null), FakeNewsCache()
        )
        assertEquals(listOf(publisher), loggedOut.publishers())
        assertEquals(null, service.lastToken)
        assertEquals(article, loggedOut.article("a1"))
    }

    @Test
    fun cachedArticleRestoresTheActiveUsersSavedState() = runBlocking {
        val cache = FakeNewsCache().apply {
            cachedArticle = article.copy(isSaved = false)
            savedArticleIds += article.id
        }
        val repository = onlineRepository(
            RuntimeEnvironment.getApplication(), FakeNewsService(),
            FakeAuthDataSource(session), cache
        )

        assertTrue(repository.cachedArticle(article.id)!!.isSaved)

        val guestRepository = onlineRepository(
            RuntimeEnvironment.getApplication(), FakeNewsService(),
            FakeAuthDataSource(null), cache
        )
        assertFalse(guestRepository.cachedArticle(article.id)!!.isSaved)
    }

    @Test
    fun savePreparesOfflineCopyBeforeServerMutation() = runBlocking {
        val events = mutableListOf<String>()
        val service = FakeNewsService(events)
        val cache = FakeNewsCache(events)
        val repository = onlineRepository(
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
        val repository = onlineRepository(
            RuntimeEnvironment.getApplication(), service, FakeAuthDataSource(session), cache
        )

        val failure = runCatching { repository.setArticleSaved("a1", true) }.exceptionOrNull()

        assertTrue(failure is NewsApiException)
        assertEquals("OFFLINE_COPY_UNAVAILABLE", (failure as NewsApiException).code)
        assertFalse(events.contains("server-save"))
        assertFalse(events.contains("cache-relation"))
    }

    private class FakeAuthDataSource(var session: AppSession?) : AuthDataSource {
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
        var analysisHash = article.contentHash
        var detailFailure: Exception? = null
        var publisherRead: (suspend (String?) -> List<PublisherItem>)? = null
        var publisherCalls = 0
        var listCalls = 0
        override suspend fun register(email: String, username: String, password: String,
                                      confirmation: String) = error("unused")
        override suspend fun login(identifier: String, password: String) = error("unused")
        override suspend fun me(token: String) = error("unused")
        override suspend fun logout(token: String) = Unit
        override suspend fun publishers(token: String?): List<PublisherItem> {
            publisherCalls++
            lastToken = token
            return publisherRead?.invoke(token) ?: listOf(publisher)
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
            listCalls++
            lastToken = token
            lastQuery = query
            lastScope = scope
            return ArticlePage(emptyList(), "next")
        }
        override suspend fun article(token: String?, articleId: String): ArticleDetail {
            detailFailure?.let { throw it }
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
                )), sourceContentHash = analysisHash, rawJson = "valid-json"
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
        val removed = mutableListOf<Pair<String, String?>>()
        override suspend fun removeArticle(articleId: String, userId: String?) { removed += articleId to userId }
        var cachedArticle: ArticleDetail? = null
        var cachedAnalysis: ApiArticleAnalysis? = null
        var acceptAnalysis = true
        val savedArticleIds = mutableSetOf<String>()

        override suspend fun article(articleId: String) =
            cachedArticle?.takeIf { it.id == articleId }

        override suspend fun isSaved(userId: String, articleId: String) =
            articleId in savedArticleIds

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
