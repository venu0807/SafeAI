package com.example.android.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.example.android.services.ThreatDetectionService;
import com.example.android.utils.SharedPrefsHelper;

/**
 * Periodic health check to ensure ThreatDetectionService is running
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPrefsHelper prefsHelper = new SharedPrefsHelper(context);

        // If protection should be enabled but service is not running (or just as a precaution)
        if (prefsHelper.isProtectionEnabled()) {
            Log.d(TAG, "Periodic health check: Ensuring service is active");
            Intent serviceIntent = new Intent(context, ThreatDetectionService.class);
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
