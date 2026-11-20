package com.swpp.team5.frameless.data.local;

import androidx.room.ColumnInfo;
import androidx.room.Embedded;

public final class CachedSavedArticleRow {
    @Embedded public CachedArticleEntity article;
    @ColumnInfo(name = "relation_saved_at") public String savedAt;
}
