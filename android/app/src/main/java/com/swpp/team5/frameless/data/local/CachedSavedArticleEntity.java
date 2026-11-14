package com.swpp.team5.frameless.data.local;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

@Entity(
        tableName = "cached_saved_article",
        primaryKeys = {"user_id", "article_id"},
        foreignKeys = @ForeignKey(
                entity = CachedArticleEntity.class,
                parentColumns = "article_id",
                childColumns = "article_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = @Index("article_id")
)
public final class CachedSavedArticleEntity {
    @NonNull @ColumnInfo(name = "user_id") public final String userId;
    @NonNull @ColumnInfo(name = "article_id") public final String articleId;
    @NonNull @ColumnInfo(name = "saved_at") public final String savedAt;
    @ColumnInfo(name = "last_verified_at") public final long lastVerifiedAt;

    public CachedSavedArticleEntity(
            @NonNull String userId,
            @NonNull String articleId,
            @NonNull String savedAt,
            long lastVerifiedAt
    ) {
        this.userId = userId;
        this.articleId = articleId;
        this.savedAt = savedAt;
        this.lastVerifiedAt = lastVerifiedAt;
    }
}
