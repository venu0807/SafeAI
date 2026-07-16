package com.example.android.services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.android.activities.SOSBufferActivity;
import com.example.android.activities.SafeTimerActivity;

public class SafeTimerService extends Service {

    public static final String ACTION_START_TIMER = "ACTION_START_TIMER";
    public static final String ACTION_CANCEL_TIMER = "ACTION_CANCEL_TIMER";
    public static final String ACTION_TIMER_UPDATE = "ACTION_TIMER_UPDATE";
    public static final String EXTRA_TIME_IN_MILLIS = "EXTRA_TIME_IN_MILLIS";

    public static final String CHANNEL_ID = "safe_timer_channel";
    private static final int NOTIFICATION_ID = 8822;

    private CountDownTimer countDownTimer;
    private long timeLeftInMillis;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_START_TIMER.equals(action)) {
                long requestedTime = intent.getLongExtra(EXTRA_TIME_IN_MILLIS, 1800000); // 30 mins default
                startTimer(requestedTime);
            } else if (ACTION_CANCEL_TIMER.equals(action)) {
                cancelTimer();
            }
        }
        return START_NOT_STICKY;
    }

    private void startTimer(long timeInMillis) {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        timeLeftInMillis = timeInMillis;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification("Timer Started..."));
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Timer Started..."));
        }

        countDownTimer = new CountDownTimer(timeLeftInMillis, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeLeftInMillis = millisUntilFinished;
                int minutes = (int) (timeLeftInMillis / 1000) / 60;
                int seconds = (int) (timeLeftInMillis / 1000) % 60;
                String timeFormatted = String.format("%02d:%02d", minutes, seconds);

                updateNotification("Time remaining: " + timeFormatted);

                // Broadcast to Activity if it's open
                Intent broadcastIntent = new Intent(ACTION_TIMER_UPDATE);
                broadcastIntent.putExtra(EXTRA_TIME_IN_MILLIS, timeLeftInMillis);
                sendBroadcast(broadcastIntent);
            }

            @Override
            public void onFinish() {
                triggerEmergencySOS();
            }
        }.start();
    }

    private void cancelTimer() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        Intent broadcastIntent = new Intent(ACTION_TIMER_UPDATE);
        broadcastIntent.putExtra("IS_CANCELLED", true);
        sendBroadcast(broadcastIntent);

        stopForeground(true);
        stopSelf();
    }

    private void triggerEmergencySOS() {
        Intent bufferIntent = new Intent(this, SOSBufferActivity.class);
        bufferIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        bufferIntent.putExtra("confidence", 1.0f);
        startActivity(bufferIntent);

        stopForeground(true);
        stopSelf();
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, SafeTimerActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Intent cancelIntent = new Intent(this, SafeTimerService.class);
        cancelIntent.setAction(ACTION_CANCEL_TIMER);
        PendingIntent cancelPendingIntent = PendingIntent.getService(this, 1, cancelIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Safe Timer Active")
                .setContentText(contentText)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentIntent(pendingIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "CANCEL", cancelPendingIntent)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Safe Timer Background",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        super.onDestroy();
    }
}
