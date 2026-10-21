package com.swpp.team5.frameless.data

import android.content.Context

internal data class AppSession(val username: String, val token: String)

internal class AuthRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("frameless_auth", Context.MODE_PRIVATE)

    fun currentSession(): AppSession? {
        val username = preferences.getString("username", null)
        val token = preferences.getString("token", null)
        return if (username.isNullOrBlank() || token.isNullOrBlank()) null else AppSession(username, token)
    }

    suspend fun login(username: String, password: String, clientId: String): AppSession {
        val session = AnalysisApi.login(username, password, clientId)
        preferences.edit().putString("username", session.username).putString("token", session.token).apply()
        return session
    }

    fun logout() = preferences.edit().clear().apply()
}
