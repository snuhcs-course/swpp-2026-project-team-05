package com.swpp.team5.frameless.data

import android.content.Context

internal interface NewsDataSource {
    suspend fun publishers(): List<PublisherItem>
    suspend fun replaceFollowedPublishers(publisherCodes: Set<String>): Set<String>
    suspend fun articles(category: NewsCategory?, scope: FeedScope,
                         cursor: String? = null, query: String? = null): ArticlePage
    suspend fun offlineSavedArticles(category: NewsCategory? = null): ArticlePage? = null
    suspend fun article(articleId: String): ArticleDetail
    suspend fun cachedArticle(articleId: String): ArticleDetail? = null
    suspend fun analysis(article: ArticleDetail): ArticleAnalysisUi
    suspend fun cachedAnalysis(article: ArticleDetail): ArticleAnalysisUi? = null
    suspend fun savedArticles(cursor: String? = null): ArticlePage
    suspend fun setArticleSaved(articleId: String, saved: Boolean)
    suspend fun clearSavedCache(userId: String) = Unit
    fun savedCategory(): NewsCategory
    fun savedAnalysisEnabled(): Boolean
    fun saveCategory(category: NewsCategory)
    fun saveAnalysisEnabled(enabled: Boolean)
}

internal class NewsRepository(
    context: Context,
    private val service: NewsService = NewsApi,
    private val auth: AuthDataSource = AuthRepository(context.applicationContext),
    private val cache: NewsCacheDataSource = NewsCache(context.applicationContext)
) : NewsDataSource {
    private val preferences = context.applicationContext
        .getSharedPreferences("frameless_news_preferences", Context.MODE_PRIVATE)

    private fun token(): String = auth.currentSession()?.token
        ?: throw NewsApiException("AUTH_REQUIRED", "로그인이 필요합니다.")

    private fun optionalToken(): String? = auth.currentSession()?.token

    override suspend fun publishers(): List<PublisherItem> = service.publishers(optionalToken())

    override suspend fun replaceFollowedPublishers(publisherCodes: Set<String>): Set<String> =
        service.replaceFollowedPublishers(token(), publisherCodes)

    override suspend fun articles(category: NewsCategory?, scope: FeedScope,
                         cursor: String?, query: String?): ArticlePage {
        return service.articles(
            if (scope == FeedScope.FOLLOWING) token() else optionalToken(),
            category, scope, cursor, query = query
        )
    }

    override suspend fun offlineSavedArticles(category: NewsCategory?): ArticlePage? =
        auth.currentSession()?.user?.id?.let { cache.offlineSavedArticles(it, category) }

    override suspend fun article(articleId: String): ArticleDetail {
        return service.article(optionalToken(), articleId).also { cache.storeArticle(it) }
    }

    override suspend fun cachedArticle(articleId: String): ArticleDetail? {
        val article = cache.article(articleId) ?: return null
        val userId = auth.currentSession()?.user?.id ?: return article
        return article.copy(isSaved = cache.isSaved(userId, articleId))
    }

    override suspend fun analysis(article: ArticleDetail): ArticleAnalysisUi {
        val response = service.analysis(optionalToken(), article.id)
        return response.toUi(article).also { cache.storeAnalysis(article, response) }
    }

    override suspend fun cachedAnalysis(article: ArticleDetail): ArticleAnalysisUi? =
        cache.analysis(article)?.toUi(article)

    override suspend fun savedArticles(cursor: String?): ArticlePage {
        val session = auth.currentSession()
            ?: throw NewsApiException("AUTH_REQUIRED", "로그인이 필요합니다.")
        return service.savedArticles(session.token, cursor).also { page ->
            cache.storeSavedPage(session.user.id, page, replace = cursor == null)
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
