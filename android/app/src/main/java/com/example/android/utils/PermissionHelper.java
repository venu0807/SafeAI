package com.example.android.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.os.PowerManager;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for managing runtime permissions across Android 8.0 to 14+
 */
public class PermissionHelper {

    private static final String[] REQUIRED_PERMISSIONS;

    static {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.RECORD_AUDIO);
        permissions.add(Manifest.permission.CAMERA);
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        permissions.add(Manifest.permission.SEND_SMS);
        permissions.add(Manifest.permission.CALL_PHONE);
        permissions.add(Manifest.permission.READ_CONTACTS);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            permissions.add(Manifest.permission.FOREGROUND_SERVICE);
        }
        
        REQUIRED_PERMISSIONS = permissions.toArray(new String[0]);
    }

    public static boolean hasAllPermissions(Context context) {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public static void requestPermissions(Activity activity, int requestCode) {
        List<String> permissionsToRequest = new ArrayList<>();

        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(activity, permissionsToRequest.toArray(new String[0]), requestCode);
        }
    }

    /**
     * Specialized logic for Background Location (Android 10+)
     */
    public static void checkAndRequestBackgroundLocation(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                new AlertDialog.Builder(activity)
                        .setTitle("Background Location Required")
                        .setMessage("To protect you 24/7, SafeGuard AI needs to access your location 'All the time'. Please select 'Allow all the time' in the next screen.")
                        .setPositiveButton("Settings", (dialog, which) -> {
                            requestBackgroundLocation(activity, requestCode);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        }
    }

    private static void requestBackgroundLocation(Activity activity, int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION}, requestCode);
        }
    }

    /**
     * Battery Optimization whitelist (Android 8.0+)
     */
    public static void showBatteryOptimizationDialog(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
        }
    }

    public static boolean isIgnoringBatteryOptimizations(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                return pm.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return true; // Not applicable or allowed below Marshmallow
    }
}
