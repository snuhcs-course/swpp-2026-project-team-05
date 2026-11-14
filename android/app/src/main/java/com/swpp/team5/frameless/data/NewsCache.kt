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
    suspend fun article(articleId: String): ArticleDetail?
    suspend fun storeArticle(article: ArticleDetail)
    suspend fun analysis(article: ArticleDetail): ApiArticleAnalysis?
    suspend fun storeAnalysis(article: ArticleDetail, analysis: ApiArticleAnalysis): Boolean
    suspend fun offlineSavedArticles(userId: String, category: NewsCategory? = null): ArticlePage?
    suspend fun storeSavedPage(userId: String, page: ArticlePage, replace: Boolean)
    suspend fun setSaved(userId: String, articleId: String, saved: Boolean)
    suspend fun clearSaved(userId: String)
}

internal class NewsCache(
    context: Context,
    private val database: FrameLESSDatabase = FrameLESSDatabase.get(context)
) : NewsCacheDataSource {
    private val dao = database.newsCacheDao()

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
            if (analysis.state !in OFFLINE_ANALYSIS_STATES ||
                analysis.rawJson.isBlank() || article.contentHash.isBlank() ||
                analysis.sourceContentHash != article.contentHash) return@withContext false
            val decoded = runCatching { decodeArticleAnalysis(analysis.rawJson) }.getOrNull()
                ?: return@withContext false
            if (decoded.sourceContentHash != article.contentHash ||
                decoded.toUi(article).discardedMarkerCount != 0) return@withContext false
            database.runInTransaction {
                dao.upsertAnalysis(
                    CachedAnalysisEntity(
                        article.id,
                        analysis.rawJson,
                        decoded.version,
                        decoded.sourceContentHash,
                        System.currentTimeMillis()
                    )
                )
                dao.updateAnalysisState(article.id, decoded.state.name)
            }
            true
        }

    override suspend fun offlineSavedArticles(
        userId: String,
        category: NewsCategory?
    ): ArticlePage? = withContext(Dispatchers.IO) {
        dao.offlineSavedArticles(userId, category?.wire, SAVED_LIMIT)
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
                database.runInTransaction {
                    dao.deleteSaved(userId, articleId)
                    dao.removeUnusedSummaries()
                }
            }
        }

    override suspend fun clearSaved(userId: String) = withContext(Dispatchers.IO) {
        database.runInTransaction {
            dao.deleteSavedForUser(userId)
            dao.removeUnusedSummaries()
        }
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
        dao.trimDetails(DETAIL_LIMIT)
        dao.removeAnalysisWithoutDetail()
        dao.removeUnusedSummaries()
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
        val OFFLINE_ANALYSIS_STATES = setOf(
            StoredAnalysisState.READY,
            StoredAnalysisState.PARTIAL
        )
        const val DETAIL_LIMIT = 20
        const val SAVED_LIMIT = 200
    }
}
