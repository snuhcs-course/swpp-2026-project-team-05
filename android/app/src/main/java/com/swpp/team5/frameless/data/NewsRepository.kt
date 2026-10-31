package com.swpp.team5.frameless.data

import android.content.Context

internal interface NewsDataSource {
    suspend fun publishers(): List<PublisherItem>
    suspend fun articles(category: NewsCategory, publisherCodes: Set<String>,
                         cursor: String? = null): ArticlePage
    suspend fun article(articleId: String): ArticleDetail
    suspend fun analysis(article: ArticleDetail): ArticleAnalysisUi
    fun savedCategory(): NewsCategory
    fun savedPublishers(): Set<String>
    fun savedAnalysisEnabled(): Boolean
    fun saveFilters(category: NewsCategory, publishers: Set<String>)
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

    override suspend fun articles(category: NewsCategory, publisherCodes: Set<String>,
                         cursor: String?): ArticlePage =
        service.articles(token(), category, publisherCodes, cursor)

    override suspend fun article(articleId: String): ArticleDetail = service.article(token(), articleId)

    override suspend fun analysis(article: ArticleDetail): ArticleAnalysisUi =
        service.analysis(token(), article.id).toUi(article)

    override fun savedCategory(): NewsCategory = NewsCategory.fromWire(
        preferences.getString("category", NewsCategory.POLITICS.wire).orEmpty()
    )

    override fun savedPublishers(): Set<String> = preferences.getString("publishers", "")
        .orEmpty().split(',').filter(String::isNotBlank).toSet()

    override fun savedAnalysisEnabled(): Boolean = preferences.getBoolean("analysis_enabled", true)

    override fun saveFilters(category: NewsCategory, publishers: Set<String>) {
        preferences.edit()
            .putString("category", category.wire)
            .putString("publishers", publishers.sorted().joinToString(","))
            .apply()
    }

    override fun saveAnalysisEnabled(enabled: Boolean) {
        preferences.edit().putBoolean("analysis_enabled", enabled).apply()
    }
}
