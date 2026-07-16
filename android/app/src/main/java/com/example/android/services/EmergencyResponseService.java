package com.example.android.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.telephony.SmsManager;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.example.android.R;
import com.example.android.activities.MainActivity;
import com.example.android.models.EmergencyContact;
import com.example.android.utils.EmergencyHelper;
import com.example.android.utils.LocationHelper;
import com.example.android.utils.SharedPrefsHelper;
import com.example.android.utils.ShakeDetector;
import com.example.android.utils.EmergencyDispatchSender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmergencyResponseService extends Service {

    private static final String TAG = "EmergencyResponse";
    private static final String CHANNEL_ID = "emergency_alert_channel";
    private static final int NOTIFICATION_ID = 9911;
    private static final String DEFAULT_NUMBER = "112";
    private static final String PLACEHOLDER_NUMBER = "0000000000";

    private FusedLocationProviderClient fusedLocationClient;
    private SharedPrefsHelper prefsHelper;
    private ExecutorService executor;
    private PowerManager.WakeLock wakeLock;
    private boolean shouldCallPolice = true;

    @Override
    public void onCreate() {
        super.onCreate();

        prefsHelper = new SharedPrefsHelper(this);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        executor = Executors.newSingleThreadExecutor();

        // Shake Detector has been moved to ThreatDetectionService for 24/7 monitoring

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "SafeGuardAI::EmergencyWakeLock");
        }

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            shouldCallPolice = intent.getBooleanExtra("call_police", true);
        }

        Notification notification = createForegroundNotification();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (wakeLock != null)
            wakeLock.acquire(120000); // 2 minutes

        startEmergencyFlow();

        return START_NOT_STICKY;
    }

    private void startEmergencyFlow() {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission missing");
            executor.execute(() -> dispatchSMS("Location permission denied", 0, 0));
            return;
        }

        // Use CancellationTokenSource to enforce a 10-second timeout on the fresh GPS fix.
        // If GPS can't get a lock in 10 seconds, fall back to the last known location.
        CancellationTokenSource tokenSource = new CancellationTokenSource();
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Log.w(TAG, "Fresh GPS fix timed out after 10s, falling back to last known");
            tokenSource.cancel();
        }, 10000);

        fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                tokenSource.getToken()).addOnSuccessListener(location -> {
                    try {
                        if (!executor.isShutdown()) {
                            executor.execute(() -> {
                                try {
                                    if (location == null) {
                                        Log.e(TAG, "Fresh location is null, trying last known location");
                                        tryGetLastLocationAndFinish();
                                    } else {
                                        processLocationAndRespond(location, true);
                                    }
                                } catch (Exception e) {
                                    Log.e(TAG, "Error in emergency flow background task", e);
                                }
                            });
                        }
                    } catch (java.util.concurrent.RejectedExecutionException e) {
                        Log.e(TAG, "Executor was rejected during location callback", e);
                    }
                }).addOnFailureListener(e -> {
                    try {
                        if (!executor.isShutdown()) {
                            Log.w(TAG, "Fresh location fetch failed, trying last known", e);
                            executor.execute(this::tryGetLastLocationAndFinish);
                        }
                    } catch (java.util.concurrent.RejectedExecutionException ex) {
                        Log.e(TAG, "Executor was rejected during location failure", ex);
                    }
                });
    }

    private void tryGetLastLocationAndFinish() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            dispatchSMS("Location unavailable", 0, 0);
            finishFlow();
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            try {
                if (!executor.isShutdown()) {
                    executor.execute(() -> {
                        if (location != null) {
                            // Check how old this cached location is
                            long ageMs = System.currentTimeMillis() - location.getTime();
                            boolean isRecent = ageMs < 5 * 60 * 1000; // Less than 5 minutes old
                            if (ageMs > 60 * 1000) {
                                Log.w(TAG, "Using last known location that is " +
                                        (ageMs / 1000) + " seconds old");
                            }
                            processLocationAndRespond(location, isRecent);
                        } else {
                            dispatchSMS("No location data available", 0, 0);
                        }
                        finishFlow();
                    });
                }
            } catch (java.util.concurrent.RejectedExecutionException e) {
                Log.e(TAG, "Executor was rejected during last location callback", e);
            }
        }).addOnFailureListener(e -> {
            try {
                if (!executor.isShutdown()) {
                    executor.execute(() -> {
                        dispatchSMS("Location retrieval failed", 0, 0);
                        finishFlow();
                    });
                }
            } catch (java.util.concurrent.RejectedExecutionException ex) {
                Log.e(TAG, "Executor was rejected during last location failure", ex);
            }
        });
    }

    private void processLocationAndRespond(Location location, boolean isFresh) {
        double lat = location.getLatitude();
        double lon = location.getLongitude();
        String address = LocationHelper.getAddressFromLocation(this, lat, lon);

        // Tag the location so the SMS recipient knows its reliability
        String locationTag = isFresh ? "🔴 Live GPS" : "📍 Approx";

        // Activate camera flash as visual deterrent
        activateCameraFlash();

        dispatchSMS(address, lat, lon, locationTag);
        dispatchNetworkAlert(address, lat, lon);
        
        // --- SILENT POLICE DISPATCH INTEGRATION ---
        if (shouldCallPolice) {
            Log.w(TAG, "Verified Threat: Sending silent dispatch to Police API.");
            EmergencyDispatchSender.sendSilentDispatch(this, lat, lon, address);
        } else {
            Log.i(TAG, "Unverified/Manual Threat: Police dispatch bypassed to prevent false alarms.");
        }

        if (prefsHelper.isAutoCallEnabled()) {
            initiateAutomaticCall();
        } else {
            Log.d(TAG, "Automatic call disabled by user settings");
            showCallNotification();
        }

        finishFlow();
    }

    /**
     * Activate camera LED flash as a visual deterrent during emergency.
     * Flash blinks for 10 seconds to attract attention and deter attackers.
     */
    private void activateCameraFlash() {

        // Check runtime permission first
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "CAMERA permission not granted — skipping flash deterrent");
            return;
        }
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (cameraManager == null) return;

            String cameraId = null;
            for (String id : cameraManager.getCameraIdList()) {
                android.hardware.camera2.CameraCharacteristics chars =
                        cameraManager.getCameraCharacteristics(id);
                Boolean flashAvailable = chars.get(
                        android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = chars.get(
                        android.hardware.camera2.CameraCharacteristics.LENS_FACING);
                if (flashAvailable != null && flashAvailable
                        && facing != null && facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }

            if (cameraId == null) return;

            // Final locals for lambda capture
            final CameraManager cm = cameraManager;
            final String camId = cameraId;

            // Turn flash ON
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                cm.setTorchMode(camId, true);
            }

            // Turn flash OFF after 10 seconds
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        cm.setTorchMode(camId, false);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to turn off camera flash", e);
                }
            }, 10000);

            Log.d(TAG, "Camera flash activated as visual deterrent (10s)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to activate camera flash", e);
        }
    }

    private void dispatchNetworkAlert(String address, double lat, double lon) {
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                Log.e(TAG, "Firebase not initialized, cannot send network alert");
                return;
            }
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            String uid = user != null ? user.getUid() : "anonymous_" + prefsHelper.getUserName();

            DatabaseReference alertsRef = FirebaseDatabase.getInstance().getReference("sos_alerts").child(uid).push();

            java.util.Map<String, Object> alertData = new java.util.HashMap<>();
            alertData.put("timestamp", System.currentTimeMillis());
            alertData.put("latitude", lat);
            alertData.put("longitude", lon);
            alertData.put("address", address);
            alertData.put("userName", prefsHelper.getUserName());

            alertsRef.setValue(alertData)
                    .addOnSuccessListener(aVoid -> Log.d(TAG, "Network SOS alert sent successfully to Firebase"))
                    .addOnFailureListener(e -> Log.e(TAG, "Failed to send network SOS alert", e));
        } catch (Exception e) {
            Log.e(TAG, "Error dispatching network alert", e);
        }
    }

    private void finishFlow() {
        if (wakeLock != null && wakeLock.isHeld())
            wakeLock.release();
        stopSelf();
    }

    /**
     * Overload for error messages where no real location is available.
     * Falls through to the 4-param version with an empty tag.
     */
    private void dispatchSMS(String address, double lat, double lon) {
        dispatchSMS(address, lat, lon, "");
    }

    private void dispatchSMS(String address, double lat, double lon, String locationTag) {

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "SEND_SMS permission missing");
            return;
        }

        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(this);
        if (contacts == null || contacts.isEmpty()) {
            Log.e(TAG, "No emergency contacts");
            return;
        }

        String user = prefsHelper.getUserName();
        if (user.isEmpty())
            user = "User";

        String deviceId = android.provider.Settings.Secure.getString(
                getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        String timestamp = java.text.SimpleDateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.MEDIUM)
                .format(new java.util.Date());

        String tagLine = locationTag.isEmpty() ? "" : "\n" + locationTag;
        String msg = "🚨 SAFEGUARD AI ALERT 🚨" + tagLine + "\n" +
                "User: " + user + "\n" +
                "Device ID: " + (deviceId != null ? deviceId : "N/A") + "\n" +
                "Time: " + timestamp + "\n" +
                "Location: " + address + "\n" +
                "Map: https://maps.google.com/?q=" + lat + "," + lon;

        SmsManager sms = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? getSystemService(SmsManager.class)
                : SmsManager.getDefault();

        boolean anySmsFailed = false;
        for (EmergencyContact c : contacts) {
            String phone = c.getPhoneNumber().replaceAll("[^0-9+]", "");
            if (!phone.isEmpty()) {
                if (!sendSmsToNumber(sms, phone, msg)) {
                    anySmsFailed = true;
                }
            }
        }

        // If all SMS methods failed for all contacts, show one call notification
        if (anySmsFailed) {
            showCallNotification();
        }
    }

    /**
     * Send SMS to a single phone number, with Android 14+ compatibility.
     *
     * Android 14 (API 34, UPSIDE_DOWN_CAKE) blocks sendMultipartTextMessage() for
     * non-default SMS apps because it internally calls the hidden getGroupIdLevel1() API.
     * sendTextMessage() does NOT call this method and works normally.
     * The phone/radio handles SMS concatenation transparently.
     *
     * @return true if SMS was sent successfully, false if all attempts failed
     */
    private boolean sendSmsToNumber(SmsManager sms, String phone, String msg) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+: sendTextMessage avoids the getGroupIdLevel1() restriction.
                // The device handles long message concatenation automatically.
                sms.sendTextMessage(phone, null, msg, null, null);
                Log.d(TAG, "SMS sent to " + phone);
            } else {
                // Pre-Android 14: use multipart for reliable concatenation
                ArrayList<String> parts = sms.divideMessage(msg);
                sms.sendMultipartTextMessage(phone, null, parts, null, null);
                Log.d(TAG, "SMS sent to " + phone);
            }
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "SMS blocked by system on this device (Android 14+ restriction): " + e.getMessage());
            return sendSmsFallback(sms, phone, msg);
        } catch (Exception e) {
            Log.e(TAG, "Failed to send SMS to " + phone, e);
            return sendSmsFallback(sms, phone, msg);
        }
    }

    /**
     * Emergency fallback: truncate message to fit in a single SMS (160 chars).
     *
     * @return true if the fallback SMS was sent, false if it also failed
     */
    private boolean sendSmsFallback(SmsManager sms, String phone, String msg) {
        try {
            String shortMsg = msg.length() > 160 ? msg.substring(0, 157) + "..." : msg;
            sms.sendTextMessage(phone, null, shortMsg, null, null);
            Log.d(TAG, "Fallback single SMS sent to " + phone);
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Fallback SMS also failed for " + phone, ex);
            return false;
        }
    }

    /**
     * Returns the first emergency contact's phone number, cleaned of non-digit characters.
     * Returns null if no real contacts are configured (only placeholder defaults).
     * Returns DEFAULT_NUMBER (112) only if the contacts list is null/empty.
     *
     * IMPORTANT: Does NOT fall back to DEFAULT_NUMBER for placeholder numbers —
     * those are treated as "no real contact configured" to prevent accidental
     * emergency calls during testing.
     */
    private String getCallTargetNumber() {
        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(this);
        if (contacts == null || contacts.isEmpty()) {
            return DEFAULT_NUMBER;
        }
        String number = contacts.get(0).getPhoneNumber().replaceAll("[^0-9+]", "");
        // Treat placeholder as "no real contact configured" — never fall back to 112
        if (PLACEHOLDER_NUMBER.equals(number)) {
            return null;
        }
        return number;
    }

    private void initiateAutomaticCall() {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "CALL_PHONE permission missing, showing notification instead");
            showCallNotification();
            return;
        }

        String number = getCallTargetNumber();

        // Skip auto-call when no real contacts are configured (placeholder defaults)
        if (number == null) {
            Log.w(TAG, "No real emergency contacts configured — skipping automatic call");
            showCallNotification();
            return;
        }

        String telUri = "tel:" + number;
        Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse(telUri));
        callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            startActivity(callIntent);
            Log.d(TAG, "Automatic call initiated to " + number);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start automatic call", e);
            showCallNotification(); // Fallback
        }
    }

    private void showCallNotification() {
        String number = getCallTargetNumber();
        if (number == null) {
            Log.w(TAG, "No real contacts configured — showing generic notification");
        }

        String telUri = number != null ? "tel:" + number : "tel:" + DEFAULT_NUMBER;

        Intent callIntent = new Intent(Intent.ACTION_CALL, Uri.parse(telUri));
        PendingIntent fullScreenIntent = PendingIntent.getActivity(
                this,
                0,
                callIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification callNotification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setContentTitle("Emergency Call Needed")
                .setContentText("Tap to call your emergency contact")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenIntent, true)
                .setAutoCancel(true)
                .build();

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null)
            nm.notify(NOTIFICATION_ID + 1, callNotification);
    }

    private Notification createForegroundNotification() {
        Intent i = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                0,
                i,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("SafeGuard AI Active")
                .setContentText("Emergency protocol running")
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setContentIntent(pi)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(
                    CHANNEL_ID,
                    "Emergency Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null)
                nm.createNotificationChannel(c);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        if (executor != null)
            executor.shutdownNow();
        if (wakeLock != null && wakeLock.isHeld())
            wakeLock.release();
        super.onDestroy();
    }
}
