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

/** Link analysis searches other outlets and runs the model on the server: one to two minutes, a few at most. */
internal const val LINK_ANALYSIS_TIMEOUT_MS = 300_000

internal const val LINK_ANALYSIS_NOT_DEPLOYED_MESSAGE =
    "링크 분석은 서버 업데이트 후 사용할 수 있어요. 지금은 링크 입력 화면만 미리 볼 수 있어요."

/**
 * A server without the link analysis API answers with a page that is not the API's JSON (404, 405 or a rejected
 * request): the reader is told the feature arrives with a server update. Errors from the API itself pass through.
 */
internal fun linkAnalysisError(error: NewsApiException): NewsApiException {
    val missingApi = error.code in setOf("HTTP_404", "HTTP_405") ||
        (error.code == "INVALID_RESPONSE" && listOf("HTTP 400", "HTTP 404", "HTTP 405").any { it in error.message })
    return if (missingApi) NewsApiException("LINK_ANALYSIS_NOT_DEPLOYED", LINK_ANALYSIS_NOT_DEPLOYED_MESSAGE) else error
}

/** Keep discovery usable while an older server deployment does not expose the grouped story endpoint yet. */
internal fun storyFeedApiUnavailable(error: NewsApiException): Boolean =
    error.code in setOf("HTTP_404", "HTTP_405") ||
        (error.code == "INVALID_RESPONSE" &&
            listOf("HTTP 400", "HTTP 404", "HTTP 405").any { it in error.message })

private fun ArticlePage.asSingletonStories(): StoryPage = StoryPage(
    items = items.map { article ->
        StorySummary(
            storyKey = "article:${article.id}",
            representativeArticle = article,
            publisherCount = 1,
            publishers = listOf(article.publisher),
            latestPublishedAt = article.publishedAt
        )
    },
    nextCursor = nextCursor
)

internal interface NewsService {
    suspend fun register(email: String, username: String, password: String,
                         confirmation: String): AppSession
    suspend fun login(identifier: String, password: String): AppSession
    suspend fun me(token: String): NewsUser
    suspend fun logout(token: String)
    suspend fun publishers(token: String?): List<PublisherItem>
    suspend fun replaceFollowedPublishers(token: String, publisherCodes: Set<String>): Set<String>
    suspend fun articles(token: String?, category: NewsCategory?, scope: FeedScope,
                         cursor: String? = null, limit: Int = 20,
                         query: String? = null): ArticlePage
    suspend fun stories(token: String?, category: NewsCategory?, scope: FeedScope,
                        cursor: String? = null, limit: Int = 20): StoryPage {
        val page = articles(token, category, scope, cursor, limit)
        return page.asSingletonStories()
    }
    suspend fun publisherArticles(token: String?, publisherCode: String,
                                  cursor: String? = null, limit: Int = 20): ArticlePage =
        articles(token, null, FeedScope.ALL, cursor, limit).let { page ->
            page.copy(items = page.items.filter { it.publisher.code == publisherCode })
        }
    suspend fun article(token: String?, articleId: String): ArticleDetail
    suspend fun analysis(token: String?, articleId: String): ApiArticleAnalysis
    suspend fun savedArticles(token: String, cursor: String? = null,
                              limit: Int = 20): ArticlePage
    suspend fun saveArticle(token: String, articleId: String)
    suspend fun removeSavedArticle(token: String, articleId: String)
    /** Analyses a pasted article link on the server and returns the stored source article's id. */
    suspend fun analyzeLink(token: String?, url: String): String =
        throw NewsApiException("UNSUPPORTED", "링크 분석을 사용할 수 없어요.")
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
    override suspend fun publishers(token: String?): List<PublisherItem> = client.publishers(token)
    override suspend fun replaceFollowedPublishers(token: String, publisherCodes: Set<String>) =
        client.replaceFollowedPublishers(token, publisherCodes)
    override suspend fun articles(token: String?, category: NewsCategory?,
                                  scope: FeedScope, cursor: String?, limit: Int,
                                  query: String?): ArticlePage =
        client.articles(token, category, scope, cursor, limit, query)
    override suspend fun stories(token: String?, category: NewsCategory?, scope: FeedScope,
                                 cursor: String?, limit: Int): StoryPage =
        client.stories(token, category, scope, cursor, limit)
    override suspend fun publisherArticles(token: String?, publisherCode: String,
                                           cursor: String?, limit: Int): ArticlePage =
        client.publisherArticles(token, publisherCode, cursor, limit)
    override suspend fun article(token: String?, articleId: String): ArticleDetail =
        client.article(token, articleId)
    override suspend fun analysis(token: String?, articleId: String): ApiArticleAnalysis =
        client.analysis(token, articleId)
    override suspend fun savedArticles(token: String, cursor: String?, limit: Int): ArticlePage =
        client.savedArticles(token, cursor, limit)
    override suspend fun saveArticle(token: String, articleId: String) =
        client.saveArticle(token, articleId)
    override suspend fun removeSavedArticle(token: String, articleId: String) =
        client.removeSavedArticle(token, articleId)
    override suspend fun analyzeLink(token: String?, url: String): String = client.analyzeLink(token, url)
}

internal class NewsApiClient(baseUrl: String) {
    private val baseUrl = baseUrl.trimEnd('/')
    @Volatile private var groupedStoryRouteAvailable: Boolean? = null

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

    suspend fun publishers(token: String?): List<PublisherItem> =
        request("GET", "/api/v1/publishers", token = token)
            .optJSONArray("items").orEmptyObjects().map(::publisher)

    suspend fun replaceFollowedPublishers(token: String, publisherCodes: Set<String>): Set<String> {
        val codes = JSONArray().apply { publisherCodes.sorted().forEach(::put) }
        val root = request(
            "PUT",
            "/api/v1/me/followed-publishers",
            token = token,
            body = JSONObject().put("publisher_codes", codes)
        )
        return root.optJSONArray("publisher_codes").orEmptyStrings().toSet()
    }

    suspend fun articles(token: String?, category: NewsCategory?,
                         scope: FeedScope = FeedScope.ALL, cursor: String? = null,
                         limit: Int = 20, query: String? = null): ArticlePage {
        val queryString = buildList {
            category?.let { add("category=${encode(it.wire)}") }
            add("scope=${encode(scope.wire)}")
            add("limit=$limit")
            query?.takeIf { it.isNotBlank() }?.let { add("q=${encode(it)}") }
            cursor?.let { add("cursor=${encode(it)}") }
        }.joinToString("&")
        val root = request("GET", "/api/v1/articles?$queryString", token = token)
        return ArticlePage(
            items = root.optJSONArray("items").orEmptyObjects().map(::articleSummary),
            nextCursor = root.optString("next_cursor").takeIf { root.has("next_cursor") && !root.isNull("next_cursor") && it.isNotBlank() },
            totalCount = root.optInt("total_count").takeIf { root.has("total_count") && !root.isNull("total_count") }
        )
    }

    suspend fun stories(token: String?, category: NewsCategory?,
                        scope: FeedScope = FeedScope.ALL, cursor: String? = null,
                        limit: Int = 20): StoryPage {
        if (groupedStoryRouteAvailable == false) {
            return articles(token, category, scope, cursor, limit).asSingletonStories()
        }
        return try {
            val queryString = buildList {
                category?.let { add("category=${encode(it.wire)}") }
                add("scope=${encode(scope.wire)}")
                add("limit=$limit")
                cursor?.let { add("cursor=${encode(it)}") }
            }.joinToString("&")
            val root = request("GET", "/api/v1/stories?$queryString", token = token)
            groupedStoryRouteAvailable = true
            StoryPage(
                items = root.optJSONArray("items").orEmptyObjects().map(::storySummary),
                nextCursor = root.optString("next_cursor").takeIf {
                    root.has("next_cursor") && !root.isNull("next_cursor") && it.isNotBlank()
                }
            )
        } catch (error: NewsApiException) {
            if (!storyFeedApiUnavailable(error)) throw error
            groupedStoryRouteAvailable = false
            articles(token, category, scope, cursor, limit).asSingletonStories()
        }
    }

    suspend fun publisherArticles(token: String?, publisherCode: String,
                                  cursor: String? = null, limit: Int = 20): ArticlePage {
        val queryString = buildList {
            add("publisher=${encode(publisherCode)}")
            add("scope=ALL")
            add("limit=$limit")
            cursor?.let { add("cursor=${encode(it)}") }
        }.joinToString("&")
        val root = request("GET", "/api/v1/articles?$queryString", token = token)
        return ArticlePage(
            items = root.optJSONArray("items").orEmptyObjects().map(::articleSummary),
            nextCursor = root.optString("next_cursor").takeIf {
                root.has("next_cursor") && !root.isNull("next_cursor") && it.isNotBlank()
            }
        )
    }

    suspend fun article(token: String?, articleId: String): ArticleDetail =
        request("GET", "/api/v1/articles/${encode(articleId)}", token = token).let(::articleDetail)

    suspend fun analysis(token: String?, articleId: String): ApiArticleAnalysis =
        request("GET", "/api/v1/articles/${encode(articleId)}/analysis", token = token)
            .let(::articleAnalysis)

    suspend fun savedArticles(token: String, cursor: String? = null,
                              limit: Int = 20): ArticlePage {
        val query = buildList {
            add("limit=$limit")
            cursor?.let { add("cursor=${encode(it)}") }
        }.joinToString("&")
        val root = request("GET", "/api/v1/saved-articles?$query", token = token)
        return ArticlePage(
            items = root.optJSONArray("items").orEmptyObjects().map(::articleSummary),
            nextCursor = root.optString("next_cursor").takeIf {
                root.has("next_cursor") && !root.isNull("next_cursor") && it.isNotBlank()
            }
        )
    }

    suspend fun saveArticle(token: String, articleId: String) {
        request("PUT", "/api/v1/saved-articles/${encode(articleId)}", token = token)
    }

    suspend fun removeSavedArticle(token: String, articleId: String) {
        request(
            "DELETE", "/api/v1/saved-articles/${encode(articleId)}",
            token = token, allowEmpty = true
        )
    }

    /** The server searches other outlets and runs the analysis, so the answer can take a few minutes. */
    suspend fun analyzeLink(token: String?, url: String): String = try {
        request(
            method = "POST",
            path = "/api/v1/link-analyses",
            token = token,
            body = JSONObject().put("url", url),
            readTimeoutMs = LINK_ANALYSIS_TIMEOUT_MS
        ).getString("article_id")
    } catch (error: NewsApiException) {
        throw linkAnalysisError(error)
    }

    private suspend fun request(method: String, path: String, token: String? = null,
                                body: JSONObject? = null, allowEmpty: Boolean = false,
                                readTimeoutMs: Int = 25_000): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = URL("$baseUrl$path").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 10_000
                connection.readTimeout = readTimeoutMs
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
        logoUrl = root.optString("logo_url").takeIf { root.has("logo_url") && !root.isNull("logo_url") && it.isNotBlank() },
        isFollowed = root.optBoolean("is_followed", false),
        recentArticles = root.optJSONArray("recent_articles").orEmptyObjects().map { article ->
            PublisherArticlePreview(
                id = article.requireString("id"),
                title = article.requireString("title"),
                publishedAt = article.requireString("published_at")
            )
        }
    )

    private fun articleSummary(root: JSONObject) = ArticleSummary(
        id = root.requireString("id"),
        publisher = publisher(root.requireObject("publisher")),
        title = root.requireString("title"),
        category = NewsCategory.fromWire(root.requireString("category")),
        publishedAt = root.requireString("published_at"),
        analysisState = StoredAnalysisState.fromWire(root.requireString("analysis_state")),
        searchExcerpt = root.optString("search_excerpt").takeIf {
            root.has("search_excerpt") && !root.isNull("search_excerpt") && it.isNotBlank()
        },
        isSaved = root.optBoolean("is_saved", false),
        savedAt = root.optString("saved_at").takeIf {
            root.has("saved_at") && !root.isNull("saved_at") && it.isNotBlank()
        }
    )

    private fun storySummary(root: JSONObject) = StorySummary(
        storyKey = root.requireString("story_key"),
        representativeArticle = articleSummary(root.requireObject("representative_article")),
        publisherCount = root.requireInt("publisher_count"),
        publishers = root.optJSONArray("publishers").orEmptyObjects().map(::publisher),
        latestPublishedAt = root.requireString("latest_published_at")
    )

    private fun articleDetail(root: JSONObject) = ArticleDetail(
        id = root.requireString("id"),
        publisher = publisher(root.requireObject("publisher")),
        title = root.requireString("title"),
        body = root.requireString("body"),
        category = NewsCategory.fromWire(root.requireString("category")),
        publishedAt = root.requireString("published_at"),
        originalUrl = root.requireString("original_url"),
        analysisState = StoredAnalysisState.fromWire(root.requireString("analysis_state")),
        isSaved = root.optBoolean("is_saved", false),
        contentHash = root.requireString("content_hash")
    )

    private fun articleAnalysis(root: JSONObject) = decodeArticleAnalysis(root)

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

internal fun decodeArticleAnalysis(rawJson: String): ApiArticleAnalysis =
    decodeArticleAnalysis(JSONObject(rawJson))

private fun decodeArticleAnalysis(root: JSONObject) = ApiArticleAnalysis(
    state = StoredAnalysisState.fromWire(root.requireString("state")),
    version = root.optString("version"),
    findings = root.optJSONArray("findings").orEmptyObjects().map { finding ->
        val source = finding.requireObject("source")
        ApiFinding(
            id = finding.requireString("id"),
            categories = finding.optJSONArray("categories").orEmptyStrings(),
            scope = source.requireString("scope"),
            sentence = parseSentence(source.requireObject("sentence")),
            marker = parseMarker(source.requireObject("marker")),
            explanation = finding.requireString("explanation"),
            comparisonOverview = finding.optString("comparison_overview"),
            comparisons = finding.optJSONArray("comparison_articles").orEmptyObjects().map { target ->
                ApiComparisonArticle(
                    articleId = target.requireString("article_id"),
                    publisher = parsePublisher(target.requireObject("publisher")),
                    title = target.requireString("title"),
                    publishedAt = target.requireString("published_at"),
                    originalUrl = target.requireString("original_url"),
                    body = target.requireString("body"),
                    evidence = target.optJSONArray("evidence").orEmptyObjects().map { evidence ->
                        ApiEvidence(
                            scope = evidence.requireString("scope"),
                            sentence = parseSentence(evidence.requireObject("sentence")),
                            marker = parseMarker(evidence.requireObject("marker")),
                            targetOwner = evidence.requireString("target_owner"),
                            targetSpeakerLabel = evidence.optString("target_speaker_label").takeIf {
                                evidence.has("target_speaker_label") &&
                                    !evidence.isNull("target_speaker_label") && it.isNotBlank()
                            },
                            comparisonSummary = evidence.optString("comparison_summary")
                        )
                    }
                )
            }
        )
    },
    sourceContentHash = root.requireString("source_content_hash"),
    rawJson = root.toString(),
    economic = root.optJSONObject("economic")?.let(::parseEconomic)
)

private fun parseEconomicView(view: JSONObject): ApiEconomicView {
    val sentence = view.requireObject("sentence")
    return ApiEconomicView(
        channels = view.optJSONArray("channels").orEmptyStrings(),
        stance = view.requireString("stance"),
        direction = view.optString("direction").takeIf { view.has("direction") && !view.isNull("direction") && it.isNotBlank() },
        phrase = view.requireString("phrase"),
        scope = sentence.requireString("scope"),
        sentence = parseSentence(sentence)
    )
}

private fun parseEconomicPosition(root: JSONObject): EconomicPositionUi? {
    val position = root.optJSONObject("position") ?: return null
    val stance = position.optString("stance")
    val summary = position.optString("summary").trim()
    return if (stance.isBlank() || summary.isBlank()) null else EconomicPositionUi(stance, summary)
}

private fun parseEconomic(root: JSONObject) = ApiEconomicLens(
    event = root.optString("event"),
    views = root.optJSONArray("views").orEmptyObjects().map(::parseEconomicView),
    others = root.optJSONArray("others").orEmptyObjects().map { outlet ->
        ApiEconomicOutlet(
            articleId = outlet.requireString("article_id"),
            publisher = parsePublisher(outlet.requireObject("publisher")),
            title = outlet.optString("title"),
            views = outlet.optJSONArray("views").orEmptyObjects().map(::parseEconomicView),
            position = parseEconomicPosition(outlet)
        )
    },
    position = parseEconomicPosition(root)
)

private fun parsePublisher(root: JSONObject) = PublisherItem(
    code = root.requireString("code"),
    name = root.requireString("name"),
    logoUrl = root.optString("logo_url").takeIf {
        root.has("logo_url") && !root.isNull("logo_url") && it.isNotBlank()
    },
    isFollowed = root.optBoolean("is_followed", false)
)

private fun parseSentence(root: JSONObject) = ApiSentence(
    start = root.requireInt("start"), end = root.requireInt("end"), text = root.requireString("text")
)

private fun parseMarker(root: JSONObject) = ApiMarker(
    startInSentence = root.requireInt("start_in_sentence"),
    endInSentence = root.requireInt("end_in_sentence"),
    text = root.requireString("text")
)

private fun JSONObject.requireObject(key: String): JSONObject = optJSONObject(key)
    ?: throw NewsApiException("INVALID_RESPONSE", "서버 응답에 $key 정보가 없어요.")
private fun JSONObject.requireString(key: String): String = optString(key).takeIf { it.isNotBlank() }
    ?: throw NewsApiException("INVALID_RESPONSE", "서버 응답에 $key 정보가 없어요.")
private fun JSONObject.requireInt(key: String): Int = if (has(key) && !isNull(key)) optInt(key, -1) else -1
private fun JSONArray?.orEmptyObjects(): List<JSONObject> = if (this == null) emptyList() else
    (0 until length()).mapNotNull(::optJSONObject)
private fun JSONArray?.orEmptyStrings(): List<String> = if (this == null) emptyList() else
    (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
