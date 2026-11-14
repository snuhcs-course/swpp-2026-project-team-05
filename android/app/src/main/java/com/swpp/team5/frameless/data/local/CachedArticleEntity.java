package com.swpp.team5.frameless.data.local;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cached_article")
public final class CachedArticleEntity {
    @PrimaryKey
    @ColumnInfo(name = "article_id")
    @NonNull
    public final String articleId;
    @NonNull @ColumnInfo(name = "publisher_code") public final String publisherCode;
    @NonNull @ColumnInfo(name = "publisher_name") public final String publisherName;
    @Nullable @ColumnInfo(name = "publisher_logo_url") public final String publisherLogoUrl;
    @NonNull public final String category;
    @NonNull public final String title;
    @NonNull @ColumnInfo(name = "published_at") public final String publishedAt;
    @NonNull @ColumnInfo(name = "analysis_state") public final String analysisState;
    @Nullable @ColumnInfo(name = "body_text") public final String bodyText;
    @Nullable @ColumnInfo(name = "original_url") public final String originalUrl;
    @Nullable @ColumnInfo(name = "content_hash") public final String contentHash;
    @ColumnInfo(name = "summary_cached_at") public final long summaryCachedAt;
    @Nullable @ColumnInfo(name = "detail_cached_at") public final Long detailCachedAt;
    @Nullable @ColumnInfo(name = "last_opened_at") public final Long lastOpenedAt;

    public CachedArticleEntity(
            @NonNull String articleId,
            @NonNull String publisherCode,
            @NonNull String publisherName,
            @Nullable String publisherLogoUrl,
            @NonNull String category,
            @NonNull String title,
            @NonNull String publishedAt,
            @NonNull String analysisState,
            @Nullable String bodyText,
            @Nullable String originalUrl,
            @Nullable String contentHash,
            long summaryCachedAt,
            @Nullable Long detailCachedAt,
            @Nullable Long lastOpenedAt
    ) {
        this.articleId = articleId;
        this.publisherCode = publisherCode;
        this.publisherName = publisherName;
        this.publisherLogoUrl = publisherLogoUrl;
        this.category = category;
        this.title = title;
        this.publishedAt = publishedAt;
        this.analysisState = analysisState;
        this.bodyText = bodyText;
        this.originalUrl = originalUrl;
        this.contentHash = contentHash;
        this.summaryCachedAt = summaryCachedAt;
        this.detailCachedAt = detailCachedAt;
        this.lastOpenedAt = lastOpenedAt;
    }
}
