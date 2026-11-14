package com.swpp.team5.frameless.data

import android.content.Context
import com.swpp.team5.frameless.data.local.CachedAnalysisEntity
import com.swpp.team5.frameless.data.local.CachedArticleEntity
import com.swpp.team5.frameless.data.local.CachedSavedArticleEntity
import com.swpp.team5.frameless.data.local.FrameLESSDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

internal interface NewsCacheDataSource {
    suspend fun articles(category: NewsCategory): ArticlePage?
    suspend fun storeSummaries(items: List<ArticleSummary>)
    suspend fun article(articleId: String): ArticleDetail?
    suspend fun storeArticle(article: ArticleDetail)
    suspend fun analysis(article: ArticleDetail): ApiArticleAnalysis?
    suspend fun storeAnalysis(article: ArticleDetail, analysis: ApiArticleAnalysis)
    suspend fun savedArticles(userId: String): ArticlePage?
    suspend fun storeSavedPage(userId: String, page: ArticlePage, replace: Boolean)
    suspend fun setSaved(userId: String, articleId: String, saved: Boolean)
    suspend fun clearSaved(userId: String)
}

internal class NewsCache(
    context: Context,
    private val database: FrameLESSDatabase = FrameLESSDatabase.get(context)
) : NewsCacheDataSource {
    private val dao = database.newsCacheDao()

    override suspend fun articles(category: NewsCategory): ArticlePage? = withContext(Dispatchers.IO) {
        dao.articles(category.wire, SUMMARY_LIMIT)
            .map { it.toSummary() }
            .takeIf { it.isNotEmpty() }
            ?.let { ArticlePage(it, nextCursor = null) }
    }

    override suspend fun storeSummaries(items: List<ArticleSummary>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        database.runInTransaction {
            items.forEach { storeSummary(it, now) }
            trim()
        }
    }

    override suspend fun article(articleId: String): ArticleDetail? = withContext(Dispatchers.IO) {
        dao.article(articleId)?.toDetail()?.also {
            dao.touchDetail(articleId, System.currentTimeMillis())
        }
    }

    override suspend fun storeArticle(article: ArticleDetail) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        database.runInTransaction {
            dao.insertArticle(article.toEntity(now))
            dao.updateDetail(
                article.id,
                article.publisher.code,
                article.publisher.name,
                article.publisher.logoUrl,
                article.category.wire,
                article.title,
                article.publishedAt,
                article.analysisState.name,
                article.body,
                article.originalUrl,
                article.contentHash,
                now
            )
            trim()
        }
    }

    override suspend fun analysis(article: ArticleDetail): ApiArticleAnalysis? = withContext(Dispatchers.IO) {
        val cached = dao.analysis(article.id) ?: return@withContext null
        if (article.contentHash.isBlank() || cached.sourceContentHash != article.contentHash) {
            return@withContext null
        }
        runCatching { decodeArticleAnalysis(cached.payloadJson) }
            .getOrNull()
            ?.takeIf { it.sourceContentHash == article.contentHash }
    }

    override suspend fun storeAnalysis(article: ArticleDetail, analysis: ApiArticleAnalysis) =
        withContext(Dispatchers.IO) {
            if (analysis.rawJson.isBlank() || article.contentHash.isBlank() ||
                analysis.sourceContentHash != article.contentHash) return@withContext
            val isFullyGrounded = runCatching {
                val decoded = decodeArticleAnalysis(analysis.rawJson)
                decoded.sourceContentHash == article.contentHash &&
                    decoded.toUi(article).discardedMarkerCount == 0
            }.getOrDefault(false)
            if (!isFullyGrounded) return@withContext
            dao.upsertAnalysis(
                CachedAnalysisEntity(
                    article.id,
                    analysis.rawJson,
                    analysis.version,
                    analysis.sourceContentHash,
                    System.currentTimeMillis()
                )
            )
        }

    override suspend fun savedArticles(userId: String): ArticlePage? = withContext(Dispatchers.IO) {
        dao.savedArticles(userId, SAVED_LIMIT)
            .map { it.article.toSummary(isSaved = true, savedAt = it.savedAt) }
            .takeIf { it.isNotEmpty() }
            ?.let { ArticlePage(it, nextCursor = null) }
    }

    override suspend fun storeSavedPage(userId: String, page: ArticlePage, replace: Boolean) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            database.runInTransaction {
                page.items.forEach { storeSummary(it, now) }
                if (replace) dao.deleteSavedForUser(userId)
                dao.upsertSaved(page.items.map { item ->
                    CachedSavedArticleEntity(
                        userId,
                        item.id,
                        item.savedAt ?: Instant.ofEpochMilli(now).toString(),
                        now
                    )
                })
                dao.trimSaved(userId, SAVED_LIMIT)
                trim()
            }
        }

    override suspend fun setSaved(userId: String, articleId: String, saved: Boolean) =
        withContext(Dispatchers.IO) {
            if (saved) {
                val now = System.currentTimeMillis()
                dao.upsertSaved(
                    CachedSavedArticleEntity(
                        userId, articleId, Instant.ofEpochMilli(now).toString(), now
                    )
                )
                dao.trimSaved(userId, SAVED_LIMIT)
            } else {
                dao.deleteSaved(userId, articleId)
            }
        }

    override suspend fun clearSaved(userId: String) = withContext(Dispatchers.IO) {
        dao.deleteSavedForUser(userId)
    }

    private fun storeSummary(item: ArticleSummary, now: Long) {
        dao.insertArticle(item.toEntity(now))
        dao.updateSummary(
            item.id,
            item.publisher.code,
            item.publisher.name,
            item.publisher.logoUrl,
            item.category.wire,
            item.title,
            item.publishedAt,
            item.analysisState.name,
            now
        )
    }

    private fun trim() {
        dao.trimSummaries(SUMMARY_LIMIT)
        dao.trimDetails(DETAIL_LIMIT)
        dao.removeAnalysisWithoutDetail()
    }

    private fun ArticleSummary.toEntity(now: Long) = CachedArticleEntity(
        id,
        publisher.code,
        publisher.name,
        publisher.logoUrl,
        category.wire,
        title,
        publishedAt,
        analysisState.name,
        null,
        null,
        null,
        now,
        null,
        null
    )

    private fun ArticleDetail.toEntity(now: Long) = CachedArticleEntity(
        id,
        publisher.code,
        publisher.name,
        publisher.logoUrl,
        category.wire,
        title,
        publishedAt,
        analysisState.name,
        body,
        originalUrl,
        contentHash,
        now,
        now,
        now
    )

    private fun CachedArticleEntity.toSummary(
        isSaved: Boolean = false,
        savedAt: String? = null
    ) = ArticleSummary(
        id = articleId,
        publisher = PublisherItem(publisherCode, publisherName, publisherLogoUrl),
        title = title,
        category = NewsCategory.fromWire(category),
        publishedAt = publishedAt,
        analysisState = StoredAnalysisState.fromWire(analysisState),
        isSaved = isSaved,
        savedAt = savedAt
    )

    private fun CachedArticleEntity.toDetail(): ArticleDetail? {
        val body = bodyText ?: return null
        val original = originalUrl ?: return null
        val hash = contentHash ?: return null
        return ArticleDetail(
            id = articleId,
            publisher = PublisherItem(publisherCode, publisherName, publisherLogoUrl),
            title = title,
            body = body,
            category = NewsCategory.fromWire(category),
            publishedAt = publishedAt,
            originalUrl = original,
            analysisState = StoredAnalysisState.fromWire(analysisState),
            contentHash = hash
        )
    }

    private companion object {
        const val SUMMARY_LIMIT = 100
        const val DETAIL_LIMIT = 20
        const val SAVED_LIMIT = 200
    }
}
