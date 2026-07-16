package com.example.android.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.android.models.ThreatEvent;

@Database(entities = {ThreatEvent.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public abstract ThreatEventDao threatEventDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                            AppDatabase.class, "safeguard_database")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
