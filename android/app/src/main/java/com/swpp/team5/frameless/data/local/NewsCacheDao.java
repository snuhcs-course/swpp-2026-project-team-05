package com.swpp.team5.frameless.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.annotation.Nullable;

import java.util.List;

@Dao
public interface NewsCacheDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertArticle(CachedArticleEntity article);

    @Query("UPDATE cached_article SET publisher_code = :publisherCode, publisher_name = :publisherName, " +
            "publisher_logo_url = :logoUrl, category = :category, title = :title, " +
            "published_at = :publishedAt, analysis_state = :analysisState, " +
            "summary_cached_at = :cachedAt WHERE article_id = :articleId")
    void updateSummary(
            String articleId,
            String publisherCode,
            String publisherName,
            String logoUrl,
            String category,
            String title,
            String publishedAt,
            String analysisState,
            long cachedAt
    );

    @Query("UPDATE cached_article SET publisher_code = :publisherCode, publisher_name = :publisherName, " +
            "publisher_logo_url = :logoUrl, category = :category, title = :title, " +
            "published_at = :publishedAt, analysis_state = :analysisState, body_text = :bodyText, " +
            "original_url = :originalUrl, content_hash = :contentHash, summary_cached_at = :cachedAt, " +
            "detail_cached_at = :cachedAt, last_opened_at = :cachedAt WHERE article_id = :articleId")
    void updateDetail(
            String articleId,
            String publisherCode,
            String publisherName,
            String logoUrl,
            String category,
            String title,
            String publishedAt,
            String analysisState,
            String bodyText,
            String originalUrl,
            String contentHash,
            long cachedAt
    );

    @Query("SELECT * FROM cached_article WHERE article_id = :articleId LIMIT 1")
    CachedArticleEntity article(String articleId);

    @Query("UPDATE cached_article SET last_opened_at = :openedAt WHERE article_id = :articleId " +
            "AND detail_cached_at IS NOT NULL")
    void touchDetail(String articleId, long openedAt);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAnalysis(CachedAnalysisEntity analysis);

    @Query("SELECT * FROM cached_analysis WHERE article_id = :articleId LIMIT 1")
    CachedAnalysisEntity analysis(String articleId);

    @Query("UPDATE cached_article SET analysis_state = :analysisState " +
            "WHERE article_id = :articleId")
    void updateAnalysisState(String articleId, String analysisState);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertSaved(List<CachedSavedArticleEntity> rows);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertSaved(CachedSavedArticleEntity row);

    @Query("DELETE FROM cached_saved_article WHERE user_id = :userId")
    void deleteSavedForUser(String userId);

    @Query("DELETE FROM cached_saved_article WHERE user_id = :userId AND article_id = :articleId")
    void deleteSaved(String userId, String articleId);

    @Query("DELETE FROM cached_saved_article WHERE user_id = :userId AND article_id NOT IN " +
            "(SELECT article_id FROM cached_saved_article WHERE user_id = :userId " +
            "ORDER BY saved_at DESC LIMIT :keep)")
    void trimSaved(String userId, int keep);

    @Query("SELECT a.*, s.saved_at AS relation_saved_at FROM cached_saved_article s " +
            "JOIN cached_article a ON a.article_id = s.article_id " +
            "JOIN cached_analysis x ON x.article_id = a.article_id " +
            "WHERE s.user_id = :userId AND (:category IS NULL OR a.category = :category) " +
            "AND a.detail_cached_at IS NOT NULL AND a.body_text IS NOT NULL " +
            "AND a.original_url IS NOT NULL AND a.content_hash IS NOT NULL " +
            "AND a.analysis_state IN ('READY', 'PARTIAL') " +
            "AND x.source_content_hash = a.content_hash " +
            "ORDER BY s.saved_at DESC LIMIT :limit")
    List<CachedSavedArticleRow> offlineSavedArticles(
            String userId, @Nullable String category, int limit
    );

    @Query("DELETE FROM cached_article WHERE detail_cached_at IS NULL " +
            "AND article_id NOT IN (SELECT article_id FROM cached_saved_article)")
    void removeUnusedSummaries();

    @Query("UPDATE cached_article SET body_text = NULL, original_url = NULL, content_hash = NULL, " +
            "detail_cached_at = NULL, last_opened_at = NULL WHERE detail_cached_at IS NOT NULL " +
            "AND article_id NOT IN (SELECT article_id FROM cached_article WHERE detail_cached_at IS NOT NULL " +
            "ORDER BY COALESCE(last_opened_at, detail_cached_at) DESC LIMIT :keep)")
    void trimDetails(int keep);

    @Query("DELETE FROM cached_analysis WHERE article_id NOT IN " +
            "(SELECT article_id FROM cached_article WHERE detail_cached_at IS NOT NULL)")
    void removeAnalysisWithoutDetail();
}
