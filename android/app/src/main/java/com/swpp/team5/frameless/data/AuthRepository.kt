package com.swpp.team5.frameless.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
    testPreferences: SharedPreferences? = null
) : AuthDataSource {
    private val applicationContext = context.applicationContext
    private val preferences = testPreferences ?: run {
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            applicationContext, "frameless_auth_v1", masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun currentSession(): AppSession? {
        val id = preferences.getString("user_id", null)
        val email = preferences.getString("email", null)
        val username = preferences.getString("username", null)
        val token = preferences.getString("token", null)
        val expiresAt = preferences.getString("expires_at", null)
        if (id.isNullOrBlank() || username.isNullOrBlank() || token.isNullOrBlank() || expiresAt.isNullOrBlank()) return null
        return AppSession(
            NewsUser(id, email, username, preferences.getBoolean("is_developer", false)),
            token,
            expiresAt
        )
    }

    override suspend fun login(identifier: String, password: String): AppSession =
        save(service.login(identifier, password))

    override suspend fun register(email: String, username: String, password: String,
                         confirmation: String): AppSession =
        save(service.register(email, username, password, confirmation))

    override suspend fun restore(): AppSession? {
        val saved = currentSession() ?: return null
        return try {
            val user = service.me(saved.token)
            save(saved.copy(user = user))
        } catch (error: NewsApiException) {
            if (error.code == "AUTH_REQUIRED") clear()
            throw error
        }
    }

    override suspend fun logout() {
        val token = currentSession()?.token
        try {
            if (token != null) service.logout(token)
        } finally {
            clear()
        }
    }

    private fun save(session: AppSession): AppSession {
        preferences.edit()
            .putString("user_id", session.user.id)
            .putString("email", session.user.email)
            .putString("username", session.user.username)
            .putBoolean("is_developer", session.user.isDeveloper)
            .putString("token", session.token)
            .putString("expires_at", session.expiresAt)
            .apply()
        return session
    }

    override fun clear() = preferences.edit().clear().apply()
}
