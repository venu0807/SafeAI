package com.example.android;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Main Application class for SafeGuard AI
 * Handles global initialization
 */
public class SafeGuardApp extends Application {

    private static final String TAG = "SafeGuardApp";

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Firebase
        try {
            FirebaseApp.initializeApp(this);
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (IllegalStateException e) {
            Log.e(TAG, "Firebase already initialized", e);
        } catch (Exception e) {
            Log.e(TAG, "Firebase initialization failed — continuing without remote features", e);
        }
    }
}
