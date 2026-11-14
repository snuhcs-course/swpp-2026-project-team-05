package com.swpp.team5.frameless.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(
        entities = {
                CachedArticleEntity.class,
                CachedAnalysisEntity.class,
                CachedSavedArticleEntity.class
        },
        version = 1,
        exportSchema = true
)
public abstract class FrameLESSDatabase extends RoomDatabase {
    public abstract NewsCacheDao newsCacheDao();

    private static volatile FrameLESSDatabase instance;

    public static FrameLESSDatabase get(Context context) {
        FrameLESSDatabase current = instance;
        if (current != null) return current;
        synchronized (FrameLESSDatabase.class) {
            current = instance;
            if (current == null) {
                current = Room.databaseBuilder(
                        context.getApplicationContext(),
                        FrameLESSDatabase.class,
                        "frameless_news_cache.db"
                ).build();
                instance = current;
            }
            return current;
        }
    }
}
