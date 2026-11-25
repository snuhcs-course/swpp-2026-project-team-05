package com.swpp.team5.frameless.data

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AuthRepositoryTest {
    @Test
    fun unavailableEncryptedStoreStartsAsGuestAndNeverSendsCredentials() = runBlocking {
        val service = FakeNewsService(session)
        var attempts = 0
        val repository = AuthRepository(RuntimeEnvironment.getApplication(), service,
            preferencesFactory = { attempts++; throw SecurityException("private diagnostic") })
        assertNull(repository.currentSession())
        val error = runCatching { repository.login("reader", "private-password") }.exceptionOrNull()
        assertEquals("SECURE_STORAGE_UNAVAILABLE", (error as NewsApiException).code)
        val signupError = runCatching {
            repository.register("reader@example.com", "reader", "password", "password")
        }.exceptionOrNull() as NewsApiException
        assertEquals("SECURE_STORAGE_UNAVAILABLE", signupError.code)
        org.junit.Assert.assertFalse(error.message.contains("private diagnostic"))
        org.junit.Assert.assertTrue(attempts >= 2)
        assertEquals(0, service.loginCalls)
        assertEquals(0, service.registerCalls)
        repository.clear() // Guest recovery and local logout do not crash.
    }

    @Test
    fun failedSecureWriteRevokesTheNewSessionAndNeverKeepsAPartialLogin() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val clean = context.getSharedPreferences("auth-write-test", Context.MODE_PRIVATE)
        clean.edit().clear().commit()
        val failedCommit = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences.Editor::class.java.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java)
        ) { proxy, method, _ -> if (method.name == "commit") false else proxy }
        var throwOnEdit = true
        val broken = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java)
        ) { _, method, args ->
            if (method.name == "edit") {
                if (throwOnEdit) throw SecurityException("private storage diagnostic")
                failedCommit
            } else method.invoke(clean, *(args ?: emptyArray()))
        } as android.content.SharedPreferences
        for (failsWithException in listOf(false, true)) for (registration in listOf(false, true)) {
            throwOnEdit = failsWithException
            val service = FakeNewsService(session)
            val repository = AuthRepository(context, service, broken, preferencesFactory = { clean })
            val error = runCatching {
                if (registration) repository.register("reader@example.com", "reader", "password", "password")
                else repository.login("reader", "password")
            }.exceptionOrNull() as NewsApiException
            assertEquals("SECURE_STORAGE_UNAVAILABLE", error.code)
            org.junit.Assert.assertFalse(error.message.contains("private storage diagnostic"))
            assertEquals(listOf(session.token), service.revokedTokens)
            assertNull(repository.currentSession())
            assertNull(clean.getString("token", null))
        }
    }

    @Test
    fun encryptedStoreRecoveryPreservesThePublicRoomCache() = runBlocking<Unit> {
        val context = RuntimeEnvironment.getApplication()
        val name = "frameless_news_cache.db"
        val detail = ArticleDetail("a1", PublisherItem("yna", "연합뉴스", null), "정책 추진",
            "정책을 추진한다.", NewsCategory.POLITICS, "2026-11-25T00:00:00Z",
            "https://example.com/a1", StoredAnalysisState.READY, contentHash = "hash-a1")
        fun openDatabase() = androidx.room.Room.databaseBuilder(context,
            com.swpp.team5.frameless.data.local.FrameLESSDatabase::class.java, name).build()
        try {
            val original = openDatabase()
            try { NewsCache(context, original).storeArticle(detail) } finally { original.close() }
            val repository = AuthRepository(context, FakeNewsService(session),
                preferencesFactory = { throw SecurityException("restored key unavailable") })
            assertNull(repository.currentSession())
            repository.clear()
            val restored = openDatabase()
            try { assertEquals(detail, NewsCache(context, restored).article(detail.id)) } finally { restored.close() }
        } finally {
            context.deleteDatabase(name)
        }
    }

    @Test
    fun unreadableCredentialsRecoverToACleanEncryptedStore() {
        val context = RuntimeEnvironment.getApplication()
        val clean = context.getSharedPreferences("auth-recovered-test", Context.MODE_PRIVATE)
        clean.edit().clear().commit()
        val broken = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java)
        ) { _, _, _ -> throw SecurityException("unreadable") } as android.content.SharedPreferences
        val repository = AuthRepository(context, FakeNewsService(session), broken, preferencesFactory = { clean })
        assertNull(repository.currentSession())
        assertNull(repository.currentSession())
        assertNull(clean.getString("token", null))
    }

    @Test
    fun retryAfterBoundsAndPackagedBackupRulesProtectTheAuthStore() {
        assertEquals(60L, boundedRetryAfter(null))
        assertEquals(60L, boundedRetryAfter("bad"))
        assertEquals(1L, boundedRetryAfter("-1"))
        assertEquals(3600L, boundedRetryAfter("99999"))
        val context = RuntimeEnvironment.getApplication()
        for ((id, expected) in listOf(com.swpp.team5.frameless.R.xml.backup_rules to 1,
            com.swpp.team5.frameless.R.xml.data_extraction_rules to 2)) {
            var excludes = 0
            context.resources.getXml(id).use { xml ->
                while (xml.eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (xml.eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xml.name == "exclude" &&
                        xml.getAttributeValue(null, "domain") == "sharedpref" &&
                        xml.getAttributeValue(null, "path") == "frameless_auth_v1.xml") excludes++
                    xml.next()
                }
            }
            assertEquals(expected, excludes)
        }
    }
    private val session = AppSession(
        NewsUser("u1", "reader@example.com", "reader", false),
        "token",
        "2099-11-30T00:00:00+09:00"
    )

    private fun repository(service: FakeNewsService): AuthRepository {
        val context = RuntimeEnvironment.getApplication()
        val preferences = context.getSharedPreferences("auth-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        return AuthRepository(context, service, preferences)
    }

    @Test
    fun loginAndRegistrationPersistTheReturnedSession() = runBlocking {
        val service = FakeNewsService(session)
        val repository = repository(service)
        assertNull(repository.currentSession())

        assertEquals(session, repository.login("reader", "password"))
        assertEquals(session, repository.currentSession())
        repository.clear()
        assertEquals(session, repository.register("reader@example.com", "reader", "password", "password"))
        assertEquals("reader@example.com", repository.currentSession()?.user?.email)
    }

    @Test
    fun restoreRefreshesUserAndAuthenticationExpiryClearsSavedSession() = runBlocking {
        val service = FakeNewsService(session)
        val repository = repository(service)
        repository.login("reader", "password")
        service.currentSession = session.copy(user = session.user.copy(isDeveloper = true))
        assertEquals(true, repository.restore()?.user?.isDeveloper)

        service.meFailure = NewsApiException("AUTH_REQUIRED", "expired")
        assertThrows(NewsApiException::class.java) { runBlocking { repository.restore() } }
        assertNull(repository.currentSession())
    }

    @Test
    fun locallyExpiredSessionIsClearedBeforeAnyNetworkRequest() = runBlocking {
        val expired = session.copy(expiresAt = "2020-01-01T00:00:00Z")
        val service = FakeNewsService(expired)
        val repository = repository(service)

        repository.login("reader", "password")

        assertNull(repository.currentSession())
    }

    @Test
    fun logoutClearsLocalSessionEvenWhenRemoteLogoutFails() = runBlocking {
        val service = FakeNewsService(session)
        val repository = repository(service)
        repository.login("reader", "password")
        service.logoutFailure = IllegalStateException("offline")

        assertThrows(IllegalStateException::class.java) { runBlocking { repository.logout() } }
        assertNull(repository.currentSession())
    }

    private class FakeNewsService(var currentSession: AppSession) : NewsService {
        var meFailure: Exception? = null
        var logoutFailure: Exception? = null
        var loginCalls = 0
        var registerCalls = 0
        val revokedTokens = mutableListOf<String>()
        override suspend fun register(email: String, username: String, password: String,
                                      confirmation: String): AppSession {
            registerCalls++
            return currentSession
        }
        override suspend fun login(identifier: String, password: String): AppSession {
            loginCalls++
            return currentSession
        }
        override suspend fun me(token: String): NewsUser {
            meFailure?.let { throw it }
            return currentSession.user
        }
        override suspend fun logout(token: String) {
            revokedTokens += token
            logoutFailure?.let { throw it }
        }
        override suspend fun publishers(token: String?) = error("unused")
        override suspend fun replaceFollowedPublishers(token: String, publisherCodes: Set<String>) =
            error("unused")
        override suspend fun articles(token: String?, category: NewsCategory?,
                                      scope: FeedScope, cursor: String?, limit: Int,
                                      query: String?) = error("unused")
        override suspend fun article(token: String?, articleId: String) = error("unused")
        override suspend fun analysis(token: String?, articleId: String) = error("unused")
        override suspend fun savedArticles(token: String, cursor: String?, limit: Int) = error("unused")
        override suspend fun saveArticle(token: String, articleId: String) = Unit
        override suspend fun removeSavedArticle(token: String, articleId: String) = Unit
    }
}
