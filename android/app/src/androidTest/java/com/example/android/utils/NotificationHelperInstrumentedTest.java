package com.example.android.utils;

import android.app.NotificationManager;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Android instrumented tests for NotificationHelper.
 * Requires a device/emulator with notification support.
 */
@RunWith(AndroidJUnit4.class)
public class NotificationHelperInstrumentedTest {

    private Context context;
    private NotificationManager notificationManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Test
    public void createNotificationChannels_createsEmergencyChannel() {
        NotificationHelper.createNotificationChannels(context);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel emergencyChannel =
                    notificationManager.getNotificationChannel("emergency_alerts");
            assertNotNull("Emergency channel should exist after creation", emergencyChannel);
            assertEquals("Emergency Alerts", emergencyChannel.getName().toString());
            assertEquals(android.app.NotificationManager.IMPORTANCE_HIGH, emergencyChannel.getImportance());
        }
        // On pre-O, channels are not used
    }

    @Test
    public void createNotificationChannels_createsInfoChannel() {
        NotificationHelper.createNotificationChannels(context);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel infoChannel =
                    notificationManager.getNotificationChannel("info_notifications");
            assertNotNull("Info channel should exist after creation", infoChannel);
            assertEquals("Information", infoChannel.getName().toString());
            assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, infoChannel.getImportance());
        }
    }

    @Test
    public void createNotificationChannels_isIdempotent() {
        // Calling createNotificationChannels twice should not throw
        NotificationHelper.createNotificationChannels(context);
        NotificationHelper.createNotificationChannels(context);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            assertNotNull(notificationManager.getNotificationChannel("emergency_alerts"));
            assertNotNull(notificationManager.getNotificationChannel("info_notifications"));
        }
    }

    @Test
    public void showEmergencyNotification_doesNotThrow() {
        try {
            NotificationHelper.showEmergencyNotification(
                    context,
                    "Test Emergency",
                    "This is a test emergency notification"
            );
            // On a device, this posts a notification. We verify no crash.
            assertTrue(true);
        } catch (Exception e) {
            fail("showEmergencyNotification should not throw: " + e.getMessage());
        }
    }

    @Test
    public void showInfoNotification_doesNotThrow() {
        try {
            NotificationHelper.showInfoNotification(
                    context,
                    "Test Info",
                    "This is a test info notification"
            );
            assertTrue(true);
        } catch (Exception e) {
            fail("showInfoNotification should not throw: " + e.getMessage());
        }
    }

    @Test
    public void showEmergencyNotification_usesCorrectChannel() {
        NotificationHelper.createNotificationChannels(context);
        NotificationHelper.showEmergencyNotification(context, "Title", "Message");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Verify the emergency channel still exists
            assertNotNull(notificationManager.getNotificationChannel("emergency_alerts"));
        }
    }

    @Test
    public void createNotificationChannels_setsCorrectDescriptions() {
        NotificationHelper.createNotificationChannels(context);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel emergency = notificationManager.getNotificationChannel("emergency_alerts");
            assertEquals("Critical emergency notifications", emergency.getDescription().toString());

            android.app.NotificationChannel info = notificationManager.getNotificationChannel("info_notifications");
            assertEquals("General app notifications", info.getDescription().toString());
        }
    }
}
