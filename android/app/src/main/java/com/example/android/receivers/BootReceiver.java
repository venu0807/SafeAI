package com.example.android.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.core.app.NotificationCompat;
import android.app.NotificationManager;
import android.app.NotificationChannel;
import android.app.PendingIntent;

import com.example.android.R;
import com.example.android.activities.MainActivity;
import com.example.android.services.ThreatDetectionService;
import com.example.android.utils.SharedPrefsHelper;

/**
 * Receiver to auto-start service after device reboot
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)) { // HTC fast boot support

            Log.d(TAG, "Device restarted or app updated - initializing SafeGuard AI...");

            SharedPrefsHelper prefsHelper = new SharedPrefsHelper(context);

            // Auto-start if protection was enabled before the reboot/update
            if (prefsHelper.isProtectionEnabled()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ prevents background microphone access. We MUST request user interaction.
                    Log.d(TAG, "Android 11+ detected. Prompting user to manually restore protection via notification.");
                    sendRestoreNotification(context);
                } else {
                    Intent serviceIntent = new Intent(context, ThreatDetectionService.class);
                    try {
                        ContextCompat.startForegroundService(context, serviceIntent);
                        Log.d(TAG, "ThreatDetectionService successfully resurrected in background.");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to start service after boot: ", e);
                        sendRestoreNotification(context); // Fallback if background start fails
                    }
                }
            } else {
                Log.d(TAG, "Protection was officially dormant prior to restart. Remaining dormant.");
            }
        }
    }

    private void sendRestoreNotification(Context context) {
        String channelId = "boot_recovery_channel";
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, "Protection Recovery", NotificationManager.IMPORTANCE_HIGH);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pi = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("SafeGuard AI Protection Paused")
                .setContentText("Your phone restarted. Tap here to restore continuous protection.")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_SYSTEM)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true);

        if (nm != null) {
            nm.notify(1005, builder.build());
        }
    }
}
