package com.swpp.team5.frameless.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "cached_analysis",
        foreignKeys = @ForeignKey(
                entity = CachedArticleEntity.class,
                parentColumns = "article_id",
                childColumns = "article_id",
                onDelete = ForeignKey.CASCADE
        )
)
public final class CachedAnalysisEntity {
    @PrimaryKey
    @NonNull @ColumnInfo(name = "article_id") public final String articleId;
    @NonNull @ColumnInfo(name = "payload_json") public final String payloadJson;
    @NonNull @ColumnInfo(name = "analysis_version") public final String analysisVersion;
    @NonNull @ColumnInfo(name = "source_content_hash") public final String sourceContentHash;
    @ColumnInfo(name = "cached_at") public final long cachedAt;

    public CachedAnalysisEntity(
            @NonNull String articleId,
            @NonNull String payloadJson,
            @NonNull String analysisVersion,
            @NonNull String sourceContentHash,
            long cachedAt
    ) {
        this.articleId = articleId;
        this.payloadJson = payloadJson;
        this.analysisVersion = analysisVersion;
        this.sourceContentHash = sourceContentHash;
        this.cachedAt = cachedAt;
    }
}
