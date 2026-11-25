package com.swpp.team5.frameless.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException

internal interface NewsDataSource {
    fun clearReadCache() = Unit
    suspend fun reactions(articleId: String): ArticleReactions = throw NewsApiException("UNSUPPORTED", "반응을 불러오지 못했어요.")
    suspend fun setReaction(articleId: String, kind: ReactionKind?): ArticleReactions = throw NewsApiException("UNSUPPORTED", "반응을 저장하지 못했어요.")
    suspend fun homeHighlights(): HomeHighlights = throw NewsApiException("UNSUPPORTED", "홈 소식을 불러오지 못했어요.")
    suspend fun trends(): TrendPage = throw NewsApiException("UNSUPPORTED", "트렌드를 불러오지 못했어요.")
    suspend fun recordView(articleId: String) = Unit
    suspend fun publishers(refresh: Boolean = false): List<PublisherItem>
    suspend fun replaceFollowedPublishers(publisherCodes: Set<String>): Set<String>
    suspend fun articles(category: NewsCategory?, scope: FeedScope,
                         cursor: String? = null, query: String? = null, refresh: Boolean = false): ArticlePage
    suspend fun stories(category: NewsCategory?, scope: FeedScope,
                        cursor: String? = null, refresh: Boolean = false): StoryPage {
        val page = articles(category, scope, cursor, refresh = refresh)
        return StoryPage(
            page.items.map { article ->
                StorySummary(
                    storyKey = "article:${article.id}",
                    representativeArticle = article,
                    publisherCount = 1,
                    publishers = listOf(article.publisher),
                    latestPublishedAt = article.publishedAt
                )
            },
            page.nextCursor
        )
    }
    suspend fun publisherArticles(publisherCode: String, cursor: String? = null, refresh: Boolean = false): ArticlePage =
        articles(null, FeedScope.ALL, cursor, refresh = refresh).let { page ->
            page.copy(items = page.items.filter { it.publisher.code == publisherCode })
        }
    suspend fun offlineSavedArticles(category: NewsCategory? = null): ArticlePage? = null
    suspend fun article(articleId: String, refresh: Boolean = false): ArticleDetail
    suspend fun cachedArticle(articleId: String): ArticleDetail? = null
    suspend fun analysis(article: ArticleDetail, refresh: Boolean = false): ArticleAnalysisUi
    suspend fun cachedAnalysis(article: ArticleDetail): ArticleAnalysisUi? = null
    suspend fun savedArticles(cursor: String? = null, refresh: Boolean = false): ArticlePage
    suspend fun setArticleSaved(articleId: String, saved: Boolean)
    suspend fun clearSavedCache(userId: String) = Unit
    fun savedCategory(): NewsCategory
    fun savedAnalysisEnabled(): Boolean
    fun saveCategory(category: NewsCategory)
    fun saveAnalysisEnabled(enabled: Boolean)
    /** Analyses a pasted article link and returns the article id the reader opens. */
    suspend fun analyzeLink(url: String): String =
        throw NewsApiException("UNSUPPORTED", "링크 분석을 사용할 수 없어요.")
}

internal class NewsRepository(
    context: Context,
    private val service: NewsService = NewsApi,
    private val auth: AuthDataSource = AuthRepository(context.applicationContext),
    private val cache: NewsCacheDataSource = NewsCache(context.applicationContext),
    private val reads: NewsReadCache = NewsReadCache(),
    private val connection: () -> NewsConnection = {
        val manager = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager?.activeNetwork?.let(manager::getNetworkCapabilities)
        when {
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) != true -> NewsConnection.OFFLINE
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NewsConnection.ONLINE
            else -> NewsConnection.UNVERIFIED
        }
    }
) : NewsDataSource {
    private data class Query(val resource: String, val viewer: String?, val filters: List<String?>)
    private data class PageKey(val query: Query, val cursor: String?)
    private var readCredential: String? = null

    override fun clearReadCache() = reads.invalidate()

    private suspend fun <T : Any> read(
        resource: String, filters: List<String?> = emptyList(), cursor: String? = null,
        refresh: Boolean, freshFor: Long = 60_000, cacheIf: (T) -> Boolean = { true },
        load: suspend (String?, Boolean) -> T
    ): T {
        val session = auth.currentSession()
        synchronized(reads) {
            if (readCredential != session?.token) {
                reads.invalidate()
                readCredential = session?.token
            }
        }
        val access = connection()
        if (access == NewsConnection.OFFLINE) throw NewsApiException("NETWORK_ERROR", "인터넷 연결을 확인해 주세요.")
        val query = Query(resource, session?.user?.id, filters)
        if (refresh) reads.invalidate { it is PageKey && it.query == query }
        return reads.read(PageKey(query, cursor), if (access == NewsConnection.ONLINE) freshFor else 0, cacheIf) {
            if (cursor == null) reads.invalidate { it is PageKey && it.query == query && it.cursor != null }
            load(session?.token, access == NewsConnection.ONLINE)
        }
    }
    private val preferences = context.applicationContext
        .getSharedPreferences("frameless_news_preferences", Context.MODE_PRIVATE)

    private fun token(): String = auth.currentSession()?.token
        ?: throw NewsApiException("AUTH_REQUIRED", "로그인이 필요합니다.")

    private fun optionalToken(): String? = auth.currentSession()?.token

    override suspend fun reactions(articleId: String) = service.reactions(optionalToken(), articleId)
    override suspend fun setReaction(articleId: String, kind: ReactionKind?) = service.setReaction(token(), articleId, kind)
    override suspend fun homeHighlights() = service.homeHighlights(optionalToken())

    override suspend fun trends(): TrendPage = service.trends(optionalToken())

    override suspend fun recordView(articleId: String) {
        val token = optionalToken()
        val guestId = if (token == null) synchronized(preferences) {
            preferences.getString("guest_viewer_id", null) ?: java.util.UUID.randomUUID().toString().also {
                preferences.edit().putString("guest_viewer_id", it).apply()
            }
        } else null
        service.recordView(token, guestId, articleId)
    }

    override suspend fun publishers(refresh: Boolean): List<PublisherItem> =
        read("publishers", refresh = refresh, freshFor = 300_000) { token, _ -> service.publishers(token) }

    override suspend fun analyzeLink(url: String): String = service.analyzeLink(optionalToken(), url)

    override suspend fun replaceFollowedPublishers(publisherCodes: Set<String>): Set<String> =
        service.replaceFollowedPublishers(token(), publisherCodes).also {
            reads.invalidate { key -> key is PageKey && key.query.resource in setOf("publishers", "stories", "articles") }
        }

    override suspend fun articles(category: NewsCategory?, scope: FeedScope,
                         cursor: String?, query: String?, refresh: Boolean): ArticlePage {
        if (scope == FeedScope.FOLLOWING) token()
        val normalized = query?.let { java.text.Normalizer.normalize(it, java.text.Normalizer.Form.NFC) }
            ?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() }
        return read("articles", listOf(category?.wire, scope.name, normalized), cursor, refresh) { token, _ ->
            service.articles(token, category, scope, cursor, query = normalized)
        }
    }

    override suspend fun stories(category: NewsCategory?, scope: FeedScope,
                                 cursor: String?, refresh: Boolean): StoryPage {
        if (scope == FeedScope.FOLLOWING) token()
        return read("stories", listOf(category?.wire, scope.name), cursor, refresh) { token, _ ->
            service.stories(token, category, scope, cursor)
        }
    }

    override suspend fun publisherArticles(publisherCode: String, cursor: String?, refresh: Boolean): ArticlePage =
        read("publisher-articles", listOf(publisherCode), cursor, refresh) { token, _ ->
            service.publisherArticles(token, publisherCode, cursor)
        }

    override suspend fun offlineSavedArticles(category: NewsCategory?): ArticlePage? =
        auth.currentSession()?.user?.id?.let { cache.offlineSavedArticles(it, category) }

    override suspend fun article(articleId: String, refresh: Boolean): ArticleDetail {
        val viewer = auth.currentSession()?.user?.id
        return try {
            read("article", listOf(articleId), refresh = refresh, freshFor = 300_000) { token, reuseDisk ->
                // Disk bodies are public; their Saved relation is not an authoritative
                // server flag for a newly restored account. Memory results are viewer-bound.
                val cached = if (refresh || !reuseDisk || token != null) null
                    else local { cache.freshArticle(articleId, 300_000) }
                cached?.copy(isSaved = false)
                    ?: service.article(token, articleId).also { local { cache.storeArticle(it) } }
            }
        } catch (failure: NewsApiException) {
            if (failure.code == "NOT_FOUND") {
                reads.invalidate()
                local { cache.removeArticle(articleId, viewer) }
            }
            throw failure
        }
    }

    private suspend fun <T> local(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    }

    override suspend fun cachedArticle(articleId: String): ArticleDetail? {
        val article = cache.article(articleId) ?: return null
        val userId = auth.currentSession()?.user?.id ?: return article
        return article.copy(isSaved = cache.isSaved(userId, articleId))
    }

    override suspend fun analysis(article: ArticleDetail, refresh: Boolean): ArticleAnalysisUi {
        return read("analysis", listOf(article.id, article.contentHash), refresh = refresh,
            freshFor = 300_000, cacheIf = { it.state in OFFLINE_ANALYSIS_STATES && it.discardedMarkerCount == 0 }) { token, reuseDisk ->
            val cached = if (refresh || !reuseDisk) null else local { cache.freshAnalysis(article, 300_000) }
            if (cached != null) cached.toUi(article)
            else {
                val response = service.analysis(token, article.id)
                if (article.contentHash.isBlank() || response.sourceContentHash != article.contentHash) {
                    throw NewsApiException("ANALYSIS_VERSION_MISMATCH", "현재 기사에 맞는 분석을 불러오지 못했어요.")
                }
                response.toUi(article).also { local { cache.storeAnalysis(article, response) } }
            }
        }
    }

    override suspend fun cachedAnalysis(article: ArticleDetail): ArticleAnalysisUi? =
        cache.analysis(article)?.toUi(article)

    override suspend fun savedArticles(cursor: String?, refresh: Boolean): ArticlePage {
        val session = auth.currentSession()
            ?: throw NewsApiException("AUTH_REQUIRED", "로그인이 필요합니다.")
        return read("saved", cursor = cursor, refresh = refresh) { token, _ ->
            service.savedArticles(requireNotNull(token), cursor).also { page ->
                if (auth.currentSession()?.token == session.token) {
                    cache.storeSavedPage(session.user.id, page, replace = cursor == null)
                }
            }
        }
    }

    override suspend fun setArticleSaved(articleId: String, saved: Boolean) {
        val session = auth.currentSession()
            ?: throw NewsApiException("AUTH_REQUIRED", "로그인이 필요합니다.")
        if (saved) {
            val article = cache.article(articleId)
                ?: service.article(session.token, articleId).also { cache.storeArticle(it) }
            val cachedAnalysis = cache.analysis(article)
            var prepared = cachedAnalysis?.state in OFFLINE_ANALYSIS_STATES
            if (!prepared) {
                val response = service.analysis(session.token, articleId)
                prepared = response.state in OFFLINE_ANALYSIS_STATES &&
                    cache.storeAnalysis(article, response)
            }
            if (!prepared) throw offlineCopyUnavailable()
            service.saveArticle(session.token, articleId)
        } else {
            service.removeSavedArticle(session.token, articleId)
        }
        cache.setSaved(session.user.id, articleId, saved)
        reads.invalidate { key -> key is PageKey && (
            key.query.resource in setOf("articles", "stories", "publisher-articles", "saved") ||
                key.query.resource == "article" && key.query.filters.firstOrNull() == articleId
            ) }
    }

    private fun offlineCopyUnavailable() = NewsApiException(
        "OFFLINE_COPY_UNAVAILABLE",
        "오프라인 사본을 준비하지 못했어요. 잠시 후 다시 시도해 주세요."
    )

    override suspend fun clearSavedCache(userId: String) = cache.clearSaved(userId)

    override fun savedCategory(): NewsCategory = NewsCategory.fromWire(
        preferences.getString("category", NewsCategory.POLITICS.wire).orEmpty()
    )

    override fun savedAnalysisEnabled(): Boolean = preferences.getBoolean("analysis_enabled", true)

    override fun saveCategory(category: NewsCategory) {
        preferences.edit().putString("category", category.wire).remove("publishers").apply()
    }

    override fun saveAnalysisEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("analysis_enabled", enabled).apply()
    }

    private companion object {
        val OFFLINE_ANALYSIS_STATES = setOf(
            StoredAnalysisState.READY,
            StoredAnalysisState.PARTIAL
        )
    }
}
