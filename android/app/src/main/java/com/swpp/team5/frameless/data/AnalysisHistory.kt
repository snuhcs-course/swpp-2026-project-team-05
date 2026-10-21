package com.swpp.team5.frameless.data

import android.content.Context
import java.util.UUID

/** A lightweight row for the recent-analysis list. Full results load on selection. */
internal data class AnalysisHistoryItem(
    val id: String,
    val sourceUrl: String,
    val sourceTitle: String,
    val executionStatus: String,
    val updatedAt: String
)

/**
 * Creates one random ID per installed app. It identifies a history partition,
 * not a signed-in person, and the backend stores only its hash.
 */
internal object AnalysisHistoryIdentity {
    private const val PREFERENCES = "frameless_history"
    private const val CLIENT_ID = "client_id"

    fun clientId(context: Context): String {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        return preferences.getString(CLIENT_ID, null)
            ?: UUID.randomUUID().toString().also { id ->
                preferences.edit().putString(CLIENT_ID, id).apply()
            }
    }
}

/** Keeps Android identity storage out of UI and HTTP parsing code. */
internal class AnalysisRepository(context: Context) {
    private val clientId = AnalysisHistoryIdentity.clientId(context.applicationContext)
    private val auth = AuthRepository(context)

    private fun token(): String = auth.currentSession()?.token
        ?: throw IllegalStateException("로그인 후 사용할 수 있어요.")

    suspend fun analyze(url: String): AnalysisResult = AnalysisApi.analyze(url, clientId, token())

    suspend fun listHistory(): List<AnalysisHistoryItem> = AnalysisApi.listHistory(token())

    suspend fun loadHistory(recordId: String): AnalysisResult = AnalysisApi.loadHistory(recordId, token())
}
