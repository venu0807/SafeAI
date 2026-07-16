package com.example.android.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.android.R; // Corrected Import
import com.example.android.activities.MainActivity;

/**
 * Helper for showing notifications
 */
public class NotificationHelper {

    private static final String EMERGENCY_CHANNEL_ID = "emergency_alerts";
    private static final String INFO_CHANNEL_ID = "info_notifications";
    private static final int EMERGENCY_NOTIFICATION_ID = 9999;

    /**
     * Create notification channels
     */
    public static void createNotificationChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel emergencyChannel = new NotificationChannel(
                    EMERGENCY_CHANNEL_ID,
                    "Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            emergencyChannel.setDescription("Critical emergency notifications");
            emergencyChannel.enableVibration(true);
            emergencyChannel.enableLights(true);
            manager.createNotificationChannel(emergencyChannel);

            NotificationChannel infoChannel = new NotificationChannel(
                    INFO_CHANNEL_ID,
                    "Information",
                    NotificationManager.IMPORTANCE_LOW
            );
            infoChannel.setDescription("General app notifications");
            manager.createNotificationChannel(infoChannel);
        }
    }

    /**
     * Show emergency notification
     */
    public static void showEmergencyNotification(Context context, String title, String message) {
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, EMERGENCY_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setSound(soundUri)
                .setVibrate(new long[]{0, 500, 200, 500})
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(EMERGENCY_NOTIFICATION_ID, builder.build());
    }

    /**
     * Show info notification
     */
    public static void showInfoNotification(Context context, String title, String message) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, INFO_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify((int) System.currentTimeMillis(), builder.build());
    }
}
