package com.swpp.team5.frameless

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

internal enum class ClaimType { SHARED, DIFFERENT }

internal data class SourcePassage(
    val outlet: String,
    val excerpt: String,
    val url: String?
)

internal data class ClaimPreview(
    val type: ClaimType,
    val summary: String,
    val explanation: String,
    val passages: List<SourcePassage>
)

internal data class AnalysisResult(
    val sourceTitle: String,
    val sourceUrl: String,
    val coreEvent: String,
    val relatedArticleCount: Int,
    val claims: List<ClaimPreview>
)

internal class TeamCodeException : IllegalStateException("팀 테스트 코드를 확인해줘.")

internal object AnalysisApi {
    // Gradle supplies the shared team backend URL or a developer's local override.
    private val BASE_URL = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
    val requiresTeamCode: Boolean = BASE_URL.startsWith("https://")
    private const val MAX_RELATED_ARTICLES = 3

    suspend fun analyze(articleUrl: String, teamCode: String = ""): AnalysisResult = withContext(Dispatchers.IO) {
        val connection = (URL("$BASE_URL/api/analyze").openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            if (teamCode.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer ${teamCode.trim()}")
            }
            connection.connectTimeout = 10_000
            connection.readTimeout = 600_000
            connection.doOutput = true
            val request = JSONObject().put("url", articleUrl)
                .put("max_related", MAX_RELATED_ARTICLES)
            connection.outputStream.use { it.write(request.toString().toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = try {
                JSONObject(body)
            } catch (_: Exception) {
                throw IllegalStateException("서버 응답을 읽지 못했어요. (HTTP $status)")
            }
            if (status !in 200..299) {
                if (status == HttpURLConnection.HTTP_UNAUTHORIZED &&
                    json.optJSONObject("error")?.optString("code") == "unauthorized"
                ) {
                    throw TeamCodeException()
                }
                val message = json.optJSONObject("error")?.optString("message")
                throw IllegalStateException(message?.takeIf { it.isNotBlank() }
                    ?: "분석 요청이 실패했어요. (HTTP $status)")
            }
            parseAnalysis(json, articleUrl)
        } catch (_: ConnectException) {
            throw IllegalStateException("서버에 연결할 수 없어요. 서버 주소와 상태를 확인해줘.")
        } catch (_: SocketTimeoutException) {
            throw IllegalStateException("분석 시간이 초과됐어요. 잠시 후 다시 시도해줘.")
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseAnalysis(root: JSONObject, requestedUrl: String): AnalysisResult {
        val source = root.optJSONObject("source_analysis")
            ?: throw IllegalStateException("분석 결과에 입력 기사 정보가 없어요.")
        val comparison = root.optJSONObject("comparison")
            ?: throw IllegalStateException("분석 결과에 비교 정보가 없어요.")
        val sourceUrl = source.optString("url").ifBlank { requestedUrl }
        val sourceTitle = source.optString("title").ifBlank { sourceUrl }
        val sourceClaims = source.optJSONArray("claims") ?: JSONArray()
        val claims = mutableListOf<ClaimPreview>()

        for (issue in (comparison.optJSONArray("issues") ?: JSONArray()).objects()) {
            val members = (issue.optJSONArray("members") ?: JSONArray()).objects()
            val related = members.filter { it.optInt("article_index") > 0 }
            for ((sourceClaimIndex, matchesForClaim) in related.groupBy {
                it.optInt("source_claim_index", -1)
            }) {
                val sourceClaim = sourceClaims.optJSONObject(sourceClaimIndex) ?: continue
                val sourceQuote = sourceClaim.optJSONObject("evidence")?.optString("quote").orEmpty()
                if (sourceQuote.isBlank()) continue
                val sourcePassage = SourcePassage(sourceTitle, sourceQuote, sourceUrl)
                val title = issue.optString("issue").ifBlank { sourceClaim.optString("claim") }
                if (title.isBlank()) continue

                val same = matchesForClaim.filter { it.optString("relation_to_source") == "same" }
                val different = matchesForClaim.filter {
                    it.optString("relation_to_source") in setOf("opposes", "different_interpretation")
                }
                for ((type, matches) in listOf(ClaimType.SHARED to same, ClaimType.DIFFERENT to different)) {
                    val passages = matches.mapNotNull { member ->
                        val quote = member.optJSONObject("evidence")?.optString("quote").orEmpty()
                        if (quote.isBlank()) null else SourcePassage(
                            outlet = member.optString("title").ifBlank { "다른 출처" },
                            excerpt = quote,
                            url = member.optString("url").takeIf {
                                it.startsWith("https://") || it.startsWith("http://")
                            }
                        )
                    }.distinctBy { it.url to it.excerpt }
                    if (passages.isEmpty()) continue
                    claims += ClaimPreview(
                        type = type,
                        summary = title,
                        explanation = if (type == ClaimType.SHARED) {
                            "입력 기사와 다른 출처에서 같은 주장에 대응하는 문장을 찾았어요."
                        } else {
                            "같은 주장에 대한 반박 또는 다른 해석이 있는 문장을 찾았어요."
                        },
                        passages = listOf(sourcePassage) + passages
                    )
                }
            }
        }

        return AnalysisResult(
            sourceTitle = sourceTitle,
            sourceUrl = sourceUrl,
            coreEvent = source.optString("core_event"),
            relatedArticleCount = root.optJSONArray("related_articles")?.length() ?: 0,
            claims = claims
        )
    }

    private fun JSONArray.objects(): List<JSONObject> =
        (0 until length()).mapNotNull { optJSONObject(it) }
}
