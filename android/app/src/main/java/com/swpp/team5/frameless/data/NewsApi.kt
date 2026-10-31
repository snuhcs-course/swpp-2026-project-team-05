package com.swpp.team5.frameless.data

import com.swpp.team5.frameless.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

internal interface NewsService {
    suspend fun register(email: String, username: String, password: String,
                         confirmation: String): AppSession
    suspend fun login(identifier: String, password: String): AppSession
    suspend fun me(token: String): NewsUser
    suspend fun logout(token: String)
    suspend fun publishers(token: String): List<PublisherItem>
    suspend fun articles(token: String, category: NewsCategory, publisherCodes: Set<String>,
                         cursor: String? = null, limit: Int = 20): ArticlePage
    suspend fun article(token: String, articleId: String): ArticleDetail
    suspend fun analysis(token: String, articleId: String): ApiArticleAnalysis
}

internal object NewsApi : NewsService {
    private val client by lazy { NewsApiClient(BuildConfig.BACKEND_BASE_URL) }

    override suspend fun register(email: String, username: String, password: String,
                                  confirmation: String): AppSession =
        client.register(email, username, password, confirmation)
    override suspend fun login(identifier: String, password: String): AppSession =
        client.login(identifier, password)
    override suspend fun me(token: String): NewsUser = client.me(token)
    override suspend fun logout(token: String) = client.logout(token)
    override suspend fun publishers(token: String): List<PublisherItem> = client.publishers(token)
    override suspend fun articles(token: String, category: NewsCategory,
                                  publisherCodes: Set<String>, cursor: String?, limit: Int): ArticlePage =
        client.articles(token, category, publisherCodes, cursor, limit)
    override suspend fun article(token: String, articleId: String): ArticleDetail =
        client.article(token, articleId)
    override suspend fun analysis(token: String, articleId: String): ApiArticleAnalysis =
        client.analysis(token, articleId)
}

internal class NewsApiClient(baseUrl: String) {
    private val baseUrl = baseUrl.trimEnd('/')

    suspend fun register(email: String, username: String, password: String,
                         confirmation: String): AppSession = request(
        method = "POST",
        path = "/api/v1/auth/register",
        body = JSONObject()
            .put("email", email)
            .put("username", username)
            .put("password", password)
            .put("password_confirmation", confirmation)
    ).let(::session)

    suspend fun login(identifier: String, password: String): AppSession = request(
        method = "POST",
        path = "/api/v1/auth/login",
        body = JSONObject().put("identifier", identifier).put("password", password)
    ).let(::session)

    suspend fun me(token: String): NewsUser = request("GET", "/api/v1/auth/me", token = token)
        .requireObject("user").let(::user)

    suspend fun logout(token: String) {
        request("POST", "/api/v1/auth/logout", token = token, allowEmpty = true)
    }

    suspend fun publishers(token: String): List<PublisherItem> =
        request("GET", "/api/v1/publishers", token = token)
            .optJSONArray("items").orEmptyObjects().map(::publisher)

    suspend fun articles(token: String, category: NewsCategory,
                         publisherCodes: Set<String>, cursor: String? = null,
                         limit: Int = 20): ArticlePage {
        val query = buildList {
            add("category=${encode(category.wire)}")
            add("limit=$limit")
            publisherCodes.sorted().forEach { add("publisher=${encode(it)}") }
            cursor?.let { add("cursor=${encode(it)}") }
        }.joinToString("&")
        val root = request("GET", "/api/v1/articles?$query", token = token)
        return ArticlePage(
            items = root.optJSONArray("items").orEmptyObjects().map(::articleSummary),
            nextCursor = root.optString("next_cursor").takeIf { root.has("next_cursor") && !root.isNull("next_cursor") && it.isNotBlank() }
        )
    }

    suspend fun article(token: String, articleId: String): ArticleDetail =
        request("GET", "/api/v1/articles/${encode(articleId)}", token = token).let(::articleDetail)

    suspend fun analysis(token: String, articleId: String): ApiArticleAnalysis =
        request("GET", "/api/v1/articles/${encode(articleId)}/analysis", token = token)
            .let(::articleAnalysis)

    private suspend fun request(method: String, path: String, token: String? = null,
                                body: JSONObject? = null, allowEmpty: Boolean = false): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 10_000
                connection.readTimeout = 25_000
                connection.setRequestProperty("Accept", "application/json")
                token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use {
                        it.write(body.toString().toByteArray(Charsets.UTF_8))
                    }
                }
                val status = connection.responseCode
                if (allowEmpty && status in 200..299 && status == 204) return@withContext JSONObject()
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(raw) }.getOrElse {
                    throw NewsApiException("INVALID_RESPONSE", "서버 응답을 읽지 못했어요. (HTTP $status)")
                }
                if (status !in 200..299) throw apiError(json, status)
                json
            } catch (error: IOException) {
                throw NewsApiException("NETWORK_ERROR", AnalysisApi.networkErrorMessage(error))
            } finally {
                connection.disconnect()
            }
        }

    private fun apiError(root: JSONObject, status: Int): NewsApiException {
        val error = root.optJSONObject("error")
        val fields = error?.optJSONObject("fields")?.let { objectValue ->
            objectValue.keys().asSequence().associateWith { key -> objectValue.optString(key) }
        }.orEmpty()
        return NewsApiException(
            code = error?.optString("code")?.ifBlank { "HTTP_$status" } ?: "HTTP_$status",
            message = error?.optString("message")?.ifBlank { "요청을 처리하지 못했어요." }
                ?: "요청을 처리하지 못했어요.",
            fields = fields
        )
    }

    private fun session(root: JSONObject): AppSession = AppSession(
        user = user(root.requireObject("user")),
        token = root.requireString("token"),
        expiresAt = root.requireString("expires_at")
    )

    private fun user(root: JSONObject) = NewsUser(
        id = root.requireString("id"),
        email = root.optString("email").takeIf { root.has("email") && !root.isNull("email") && it.isNotBlank() },
        username = root.requireString("username"),
        isDeveloper = root.optBoolean("is_developer", false)
    )

    private fun publisher(root: JSONObject) = PublisherItem(
        code = root.requireString("code"),
        name = root.requireString("name"),
        logoUrl = root.optString("logo_url").takeIf { root.has("logo_url") && !root.isNull("logo_url") && it.isNotBlank() }
    )

    private fun articleSummary(root: JSONObject) = ArticleSummary(
        id = root.requireString("id"),
        publisher = publisher(root.requireObject("publisher")),
        title = root.requireString("title"),
        category = NewsCategory.fromWire(root.requireString("category")),
        publishedAt = root.requireString("published_at"),
        analysisState = StoredAnalysisState.fromWire(root.requireString("analysis_state"))
    )

    private fun articleDetail(root: JSONObject) = ArticleDetail(
        id = root.requireString("id"),
        publisher = publisher(root.requireObject("publisher")),
        title = root.requireString("title"),
        body = root.requireString("body"),
        category = NewsCategory.fromWire(root.requireString("category")),
        publishedAt = root.requireString("published_at"),
        originalUrl = root.requireString("original_url"),
        analysisState = StoredAnalysisState.fromWire(root.requireString("analysis_state"))
    )

    private fun articleAnalysis(root: JSONObject) = ApiArticleAnalysis(
        state = StoredAnalysisState.fromWire(root.requireString("state")),
        version = root.optString("version"),
        findings = root.optJSONArray("findings").orEmptyObjects().map { finding ->
            val source = finding.requireObject("source")
            ApiFinding(
                id = finding.requireString("id"),
                categories = finding.optJSONArray("categories").orEmptyStrings(),
                scope = source.requireString("scope"),
                sentence = sentence(source.requireObject("sentence")),
                marker = marker(source.requireObject("marker")),
                explanation = finding.requireString("explanation"),
                comparisons = finding.optJSONArray("comparison_articles").orEmptyObjects().map { target ->
                    ApiComparisonArticle(
                        articleId = target.requireString("article_id"),
                        publisher = publisher(target.requireObject("publisher")),
                        title = target.requireString("title"),
                        publishedAt = target.requireString("published_at"),
                        originalUrl = target.requireString("original_url"),
                        body = target.requireString("body"),
                        evidence = target.optJSONArray("evidence").orEmptyObjects().map { evidence ->
                            ApiEvidence(
                                scope = evidence.requireString("scope"),
                                sentence = sentence(evidence.requireObject("sentence")),
                                marker = marker(evidence.requireObject("marker")),
                                targetOwner = evidence.requireString("target_owner"),
                                targetSpeakerLabel = evidence.optString("target_speaker_label").takeIf {
                                    evidence.has("target_speaker_label") && !evidence.isNull("target_speaker_label") && it.isNotBlank()
                                },
                                comparisonSummary = evidence.requireString("comparison_summary")
                            )
                        }
                    )
                }
            )
        }
    )

    private fun sentence(root: JSONObject) = ApiSentence(
        start = root.requireInt("start"), end = root.requireInt("end"), text = root.requireString("text")
    )
    private fun marker(root: JSONObject) = ApiMarker(
        startInSentence = root.requireInt("start_in_sentence"),
        endInSentence = root.requireInt("end_in_sentence"),
        text = root.requireString("text")
    )

    private fun encode(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())
}

private fun JSONObject.requireObject(key: String): JSONObject = optJSONObject(key)
    ?: throw NewsApiException("INVALID_RESPONSE", "서버 응답에 $key 정보가 없어요.")
private fun JSONObject.requireString(key: String): String = optString(key).takeIf { it.isNotBlank() }
    ?: throw NewsApiException("INVALID_RESPONSE", "서버 응답에 $key 정보가 없어요.")
private fun JSONObject.requireInt(key: String): Int = if (has(key) && !isNull(key)) optInt(key, -1) else -1
private fun JSONArray?.orEmptyObjects(): List<JSONObject> = if (this == null) emptyList() else
    (0 until length()).mapNotNull(::optJSONObject)
private fun JSONArray?.orEmptyStrings(): List<String> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
