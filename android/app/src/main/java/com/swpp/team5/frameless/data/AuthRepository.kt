package com.swpp.team5.frameless.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.time.OffsetDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

internal interface AuthDataSource {
    fun currentSession(): AppSession?
    suspend fun login(identifier: String, password: String): AppSession
    suspend fun register(email: String, username: String, password: String,
                         confirmation: String): AppSession
    suspend fun restore(): AppSession?
    suspend fun logout()
    fun clear()
}

internal class AuthRepository(
    context: Context,
    private val service: NewsService = NewsApi,
    testPreferences: SharedPreferences? = null,
    preferencesFactory: (() -> SharedPreferences)? = null
) : AuthDataSource {
    private val applicationContext = context.applicationContext
    private val openPreferences = preferencesFactory ?: {
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            applicationContext, STORE_NAME, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    @Volatile private var preferences: SharedPreferences? = try {
        testPreferences ?: openPreferences()
    } catch (_: Exception) { null }

    init { if (preferences == null) recoverPreferences() }

    @Synchronized
    private fun recoverPreferences() {
        preferences = null
        runCatching { applicationContext.deleteSharedPreferences(STORE_NAME) }
        preferences = runCatching { openPreferences() }.getOrNull()
    }

    private fun storageUnavailable() = NewsApiException(
        "SECURE_STORAGE_UNAVAILABLE", "로그인 정보를 안전하게 저장하지 못했어요. 다시 시도해 주세요."
    )

    private fun requireStore(): SharedPreferences {
        if (preferences == null) recoverPreferences()
        return preferences ?: throw storageUnavailable()
    }

    override fun currentSession(): AppSession? = try {
        readSession()
    } catch (_: Exception) {
        recoverPreferences()
        null
    }

    private fun readSession(): AppSession? {
        val preferences = preferences ?: return null
        val id = preferences.getString("user_id", null)
        val email = preferences.getString("email", null)
        val username = preferences.getString("username", null)
        val token = preferences.getString("token", null)
        val expiresAt = preferences.getString("expires_at", null)
        if (id.isNullOrBlank() || username.isNullOrBlank() || token.isNullOrBlank() || expiresAt.isNullOrBlank()) return null
        val session = AppSession(
            NewsUser(id, email, username, preferences.getBoolean("is_developer", false)),
            token,
            expiresAt
        )
        val isValid = runCatching {
            OffsetDateTime.parse(session.expiresAt).toInstant().isAfter(java.time.Instant.now())
        }.getOrDefault(false)
        if (!isValid) {
            clear()
            return null
        }
        return session
    }

    override suspend fun login(identifier: String, password: String): AppSession {
        requireStore() // Do not create a remote session when secure persistence is unavailable.
        return persist(service.login(identifier, password))
    }

    override suspend fun register(email: String, username: String, password: String,
                         confirmation: String): AppSession {
        requireStore()
        return persist(service.register(email, username, password, confirmation))
    }

    private suspend fun persist(session: AppSession): AppSession = try {
        save(session)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        withContext(NonCancellable + Dispatchers.IO) {
            clear()
            runCatching { service.logout(session.token) }
        }
        throw storageUnavailable()
    }

    override suspend fun restore(): AppSession? {
        val saved = currentSession() ?: return null
        return try {
            val user = service.me(saved.token)
            save(saved.copy(user = user))
        } catch (error: NewsApiException) {
            if (error.code == "AUTH_REQUIRED" || error.code == "SECURE_STORAGE_UNAVAILABLE") clear()
            throw error
        }
    }

    override suspend fun logout() {
        val token = currentSession()?.token
        try {
            if (token != null) service.logout(token)
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { clear() }
        }
    }

    private suspend fun save(session: AppSession): AppSession = withContext(Dispatchers.IO) {
        try {
            val committed = requireStore().edit()
                .putString("user_id", session.user.id)
                .putString("email", session.user.email)
                .putString("username", session.user.username)
                .putBoolean("is_developer", session.user.isDeveloper)
                .putString("token", session.token)
                .putString("expires_at", session.expiresAt)
                .commit()
            if (!committed) throw storageUnavailable()
            session
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw storageUnavailable()
        }
    }

    @Synchronized
    override fun clear() {
        try {
            if (preferences?.edit()?.clear()?.commit() != true) recoverPreferences()
        } catch (_: Exception) {
            recoverPreferences()
        }
    }

    private companion object { const val STORE_NAME = "frameless_auth_v1" }
}
