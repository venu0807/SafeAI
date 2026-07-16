package com.example.android.db;

import android.content.Context;
import android.util.Log;

import com.example.android.models.ThreatEvent;
import com.example.android.utils.SharedPrefsHelper;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class DatabaseRepository {

    private static final String TAG = "DatabaseRepository";
    private ThreatEventDao threatEventDao;
    private ExecutorService executorService;
    private SharedPrefsHelper prefsHelper;

    public DatabaseRepository(Context context) {
        AppDatabase db = AppDatabase.getDatabase(context);
        threatEventDao = db.threatEventDao();
        executorService = Executors.newSingleThreadExecutor();
        prefsHelper = new SharedPrefsHelper(context);

        migrateLegacyDataIfNeeded();
    }

    private void migrateLegacyDataIfNeeded() {
        executorService.execute(() -> {
            List<ThreatEvent> legacyEvents = prefsHelper.getHazardLocations();
            if (legacyEvents != null && !legacyEvents.isEmpty()) {
                Log.d(TAG, "Migrating " + legacyEvents.size() + " legacy events to Room Database...");
                try {
                    threatEventDao.insertAll(legacyEvents);
                    prefsHelper.clearLegacyHazardLocations();
                    Log.d(TAG, "Migration successful.");
                } catch (Exception e) {
                    Log.e(TAG, "Migration failed", e);
                }
            }
        });
    }

    public void insertEvent(ThreatEvent event) {
        executorService.execute(() -> {
            threatEventDao.insert(event);
        });
    }

    public void getAllEvents(Consumer<List<ThreatEvent>> callback) {
        executorService.execute(() -> {
            List<ThreatEvent> events = threatEventDao.getAllEvents();
            callback.accept(events);
        });
    }
}
