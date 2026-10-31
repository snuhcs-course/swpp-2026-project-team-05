package com.swpp.team5.frameless.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewsRepositoryTest {
    private val publisher = PublisherItem("yna", "연합뉴스", null)
    private val session = AppSession(NewsUser("u1", null, "reader", false), "token", "expiry")
    private val article = ArticleDetail(
        "a1", publisher, "정책 추진", "정책을 전격 추진한다.", NewsCategory.POLITICS,
        "2026-10-31T12:00:00+09:00", "https://example.com/a1", StoredAnalysisState.READY
    )

    @Test
    fun preferencesRoundTripAndNetworkCallsUseAuthenticatedService() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("frameless_news_preferences", Context.MODE_PRIVATE).edit().clear().commit()
        val service = FakeNewsService()
        val repository = NewsRepository(context, service, FakeAuthDataSource(session))

        assertEquals(NewsCategory.POLITICS, repository.savedCategory())
        assertEquals(emptySet<String>(), repository.savedPublishers())
        repository.saveFilters(NewsCategory.SOCIETY, setOf("mbc", "yna"))
        repository.saveAnalysisEnabled(false)
        assertEquals(NewsCategory.SOCIETY, repository.savedCategory())
        assertEquals(setOf("mbc", "yna"), repository.savedPublishers())
        assertFalse(repository.savedAnalysisEnabled())

        assertEquals(listOf(publisher), repository.publishers())
        assertEquals("next", repository.articles(NewsCategory.POLITICS, setOf("yna")).nextCursor)
        assertEquals(article, repository.article("a1"))
        assertEquals("token", service.lastToken)
        assertEquals("a1", service.lastArticleId)
    }

    @Test
    fun analysisMapsVerifiedOffsetsAndMissingSessionFailsBeforeNetwork() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val service = FakeNewsService()
        val repository = NewsRepository(context, service, FakeAuthDataSource(session))
        val mapped = repository.analysis(article)
        assertEquals("전격", mapped.findings.single().markerText)

        val loggedOut = NewsRepository(context, service, FakeAuthDataSource(null))
        val failure = assertThrows(NewsApiException::class.java) {
            runBlocking { loggedOut.publishers() }
        }
        assertEquals("AUTH_REQUIRED", failure.code)
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

    private inner class FakeNewsService : NewsService {
        var lastToken: String? = null
        var lastArticleId: String? = null
        override suspend fun register(email: String, username: String, password: String,
                                      confirmation: String) = error("unused")
        override suspend fun login(identifier: String, password: String) = error("unused")
        override suspend fun me(token: String) = error("unused")
        override suspend fun logout(token: String) = Unit
        override suspend fun publishers(token: String): List<PublisherItem> {
            lastToken = token
            return listOf(publisher)
        }
        override suspend fun articles(token: String, category: NewsCategory,
                                      publisherCodes: Set<String>, cursor: String?, limit: Int): ArticlePage {
            lastToken = token
            return ArticlePage(emptyList(), "next")
        }
        override suspend fun article(token: String, articleId: String): ArticleDetail {
            lastToken = token
            lastArticleId = articleId
            return article
        }
        override suspend fun analysis(token: String, articleId: String): ApiArticleAnalysis {
            lastToken = token
            return ApiArticleAnalysis(
                StoredAnalysisState.READY, "v1", listOf(ApiFinding(
                    "f1", listOf("D"), "BODY",
                    ApiSentence(0, article.body.length, article.body),
                    ApiMarker(4, 6, "전격"), "속도를 강조한다.", emptyList()
                ))
            )
        }
    }
}
