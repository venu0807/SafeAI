package com.example.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.example.android.models.ThreatEvent;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Professional-grade Secure SharedPreferences.
 * Performance optimized with cached instance to prevent Main Thread blocking.
 */
public class SharedPrefsHelper {

    private static final String TAG = "SharedPrefsHelper";
    private static final String PREFS_NAME = "safeguard_secure_prefs_final";

    private static SharedPreferences cachedPrefs = null;
    private final Gson gson;

    public SharedPrefsHelper(Context context) {
        this.gson = new Gson();
        initPrefs(context);
    }

    private synchronized void initPrefs(Context context) {
        if (cachedPrefs != null)
            return;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            cachedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            Log.e(TAG, "Encryption fallback used", e);
            cachedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    public void setProtectionEnabled(boolean enabled) {
        cachedPrefs.edit().putBoolean("protection_enabled", enabled).apply();
    }

    public boolean isProtectionEnabled() {
        return cachedPrefs.getBoolean("protection_enabled", false);
    }

    public void setAutoCallEnabled(boolean enabled) {
        cachedPrefs.edit().putBoolean("auto_call_enabled", enabled).apply();
    }

    public boolean isAutoCallEnabled() {
        return cachedPrefs.getBoolean("auto_call_enabled", true);
    }

    public void setSensitivity(float sensitivity) {
        cachedPrefs.edit().putFloat("detection_sensitivity", sensitivity).apply();
    }

    public float getSensitivity() {
        return cachedPrefs.getFloat("detection_sensitivity", 0.30f);
    }

    // Removed addHazardLocation as data is now stored in Room Database

    public void clearLegacyHazardLocations() {
        cachedPrefs.edit().remove("hazard_locations").apply();
    }

    public List<ThreatEvent> getHazardLocations() {
        String json = cachedPrefs.getString("hazard_locations", null);
        if (json == null || json.isEmpty())
            return new ArrayList<>();
        try {
            ThreatEvent[] array = gson.fromJson(json, ThreatEvent[].class);
            if (array != null)
                return new ArrayList<>(Arrays.asList(array));
        } catch (Exception e) {
            Log.e(TAG, "Data error", e);
        }
        return new ArrayList<>();
    }

    public void incrementDetectionCount() {
        cachedPrefs.edit().putInt("detection_count", getDetectionCount() + 1).apply();
    }

    public int getDetectionCount() {
        return cachedPrefs.getInt("detection_count", 0);
    }

    public void incrementThreatCount() {
        cachedPrefs.edit().putInt("threat_count", getThreatCount() + 1).apply();
    }

    public int getThreatCount() {
        return cachedPrefs.getInt("threat_count", 0);
    }

    public void setUserName(String name) {
        cachedPrefs.edit().putString("user_name", name).apply();
    }

    public String getUserName() {
        return cachedPrefs.getString("user_name", "");
    }

    public void setUserId(String id) {
        cachedPrefs.edit().putString("user_id", id).apply();
    }

    public String getUserId() {
        return cachedPrefs.getString("user_id", "");
    }

    public void setIncognitoEnabled(boolean enabled) {
        cachedPrefs.edit().putBoolean("incognito_enabled", enabled).apply();
    }

    public boolean isIncognitoEnabled() {
        return cachedPrefs.getBoolean("incognito_enabled", false);
    }

    public void setHomeAddress(String address) {
        cachedPrefs.edit().putString("home_address", address).apply();
    }

    public String getHomeAddress() {
        return cachedPrefs.getString("home_address", "");
    }

    public void setDuressPin(String pin) {
        cachedPrefs.edit().putString("duress_pin", pin).apply();
    }

    public String getDuressPin() {
        return cachedPrefs.getString("duress_pin", "9911");
    }

    public void setSafeWord(String word) {
        if (word != null && !word.trim().isEmpty()) {
            cachedPrefs.edit().putString("safe_word", word.trim().toLowerCase()).apply();
        }
    }

    public String getSafeWord() {
        return cachedPrefs.getString("safe_word", "pineapple");
    }

    public void clearAll() {
        cachedPrefs.edit().clear().apply();
    }
}
