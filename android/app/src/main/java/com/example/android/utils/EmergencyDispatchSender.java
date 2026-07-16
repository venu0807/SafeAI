package com.example.android.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

/**
 * Mocks a connection to a professional 24/7 Emergency Dispatch API (like RapidSOS or Noonlight).
 * In a real-world scenario, this sends secure POST requests containing live GPS coordinates
 * and encrypted audio evidence to authorities silently.
 */
public class EmergencyDispatchSender {

    private static final String TAG = "EmergencyDispatchAPI";
    private static final String MOCK_API_ENDPOINT = "https://api.noonlight.com/dispatch/v1/alarms";

    public static void sendSilentDispatch(Context context, double lat, double lon, String address) {
        // Simulate preparing the JSON payload
        try {
            JSONObject payload = new JSONObject();
            
            SharedPrefsHelper prefs = new SharedPrefsHelper(context);
            String userName = prefs.getUserName();
            if (userName.isEmpty()) userName = "Unknown Guardian User";

            // Build the mock dispatch payload
            payload.put("name", userName);
            payload.put("phone", "Unknown (Mock)");
            payload.put("pin", "1234"); // User's cancellation PIN
            
            JSONObject locationObj = new JSONObject();
            locationObj.put("lat", lat);
            locationObj.put("lng", lon);
            locationObj.put("accuracy", 10.0);
            payload.put("location", locationObj);
            
            payload.put("notes", "Triggered via SafeGuard AI Autonomous Audio Engine. Medical info: None.");
            
            // In a real app, we would make a secure physical OkHttp POST request here.
            Log.w(TAG, "==== MOCK DISPATCH TRANSMISSION START ====");
            Log.w(TAG, "POST " + MOCK_API_ENDPOINT);
            Log.w(TAG, "Payload: " + payload.toString(4));
            Log.w(TAG, "==== MOCK DISPATCH TRANSMISSION SUCCESS ====");

            // Show a visual confirmation for the demonstration
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(context, "SILENT DISPATCH SENT: Police routed to " + address, Toast.LENGTH_LONG).show();
            });

        } catch (Exception e) {
            Log.e(TAG, "Failed to build dispatch payload", e);
        }
    }
}
