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
    private val session = AppSession(
        NewsUser("u1", "reader@example.com", "reader", false), "token", "expiry"
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
        override suspend fun register(email: String, username: String, password: String,
                                      confirmation: String) = currentSession
        override suspend fun login(identifier: String, password: String) = currentSession
        override suspend fun me(token: String): NewsUser {
            meFailure?.let { throw it }
            return currentSession.user
        }
        override suspend fun logout(token: String) {
            logoutFailure?.let { throw it }
        }
        override suspend fun publishers(token: String) = error("unused")
        override suspend fun articles(token: String, category: NewsCategory,
                                      publisherCodes: Set<String>, cursor: String?, limit: Int) = error("unused")
        override suspend fun article(token: String, articleId: String) = error("unused")
        override suspend fun analysis(token: String, articleId: String) = error("unused")
    }
}
