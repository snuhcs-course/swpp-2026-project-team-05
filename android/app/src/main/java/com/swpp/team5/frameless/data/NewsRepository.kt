package com.swpp.team5.frameless.data

import android.content.Context

internal interface NewsDataSource {
    suspend fun publishers(): List<PublisherItem>
    suspend fun replaceFollowedPublishers(publisherCodes: Set<String>): Set<String>
    suspend fun articles(category: NewsCategory, scope: FeedScope,
                         cursor: String? = null, query: String? = null): ArticlePage
    suspend fun article(articleId: String): ArticleDetail
    suspend fun analysis(article: ArticleDetail): ArticleAnalysisUi
    suspend fun savedArticles(cursor: String? = null): ArticlePage
    suspend fun setArticleSaved(articleId: String, saved: Boolean)
    fun savedCategory(): NewsCategory
    fun savedAnalysisEnabled(): Boolean
    fun saveCategory(category: NewsCategory)
    fun saveAnalysisEnabled(enabled: Boolean)
}

internal class NewsRepository(
    context: Context,
    private val service: NewsService = NewsApi,
    private val auth: AuthDataSource = AuthRepository(context.applicationContext)
) : NewsDataSource {
    private val preferences = context.applicationContext
        .getSharedPreferences("frameless_news_preferences", Context.MODE_PRIVATE)

    private fun token(): String = auth.currentSession()?.token
        ?: throw NewsApiException("AUTH_REQUIRED", "로그인이 필요합니다.")

    override suspend fun publishers(): List<PublisherItem> = service.publishers(token())

    override suspend fun replaceFollowedPublishers(publisherCodes: Set<String>): Set<String> =
        service.replaceFollowedPublishers(token(), publisherCodes)

    override suspend fun articles(category: NewsCategory, scope: FeedScope,
                         cursor: String?, query: String?): ArticlePage =
        service.articles(token(), category, scope, cursor, query = query)

    override suspend fun article(articleId: String): ArticleDetail = service.article(token(), articleId)

    override suspend fun analysis(article: ArticleDetail): ArticleAnalysisUi =
        service.analysis(token(), article.id).toUi(article)

    override suspend fun savedArticles(cursor: String?): ArticlePage =
        service.savedArticles(token(), cursor)

    override suspend fun setArticleSaved(articleId: String, saved: Boolean) {
        if (saved) service.saveArticle(token(), articleId)
        else service.removeSavedArticle(token(), articleId)
    }

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
}
