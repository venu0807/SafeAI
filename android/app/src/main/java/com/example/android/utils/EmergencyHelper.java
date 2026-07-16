package com.example.android.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import com.google.gson.Gson;
import com.example.android.models.EmergencyContact;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Professional-grade Emergency Contact Management.
 * Performance optimized with cached SharedPreferences to prevent UI lag.
 */
public class EmergencyHelper {

    private static final String TAG = "EmergencyHelper";
    private static final String PREFS_NAME = "secure_emergency_contacts_final";
    private static final String KEY_CONTACTS = "contacts_list_safe";
    
    private static SharedPreferences cachedPrefs = null;

    private static synchronized SharedPreferences getPrefs(Context context) {
        if (cachedPrefs != null) return cachedPrefs;
        try {
            MasterKey masterKey = new MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            cachedPrefs = EncryptedSharedPreferences.create(
                    context,
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e) {
            Log.e(TAG, "Encryption failed, using standard prefs", e);
            cachedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
        return cachedPrefs;
    }

    public static boolean saveEmergencyContacts(Context context, List<EmergencyContact> contacts) {
        if (context == null || contacts == null) return false;
        try {
            SharedPreferences.Editor editor = getPrefs(context).edit();
            EmergencyContact[] array = contacts.toArray(new EmergencyContact[0]);
            editor.putString(KEY_CONTACTS, new Gson().toJson(array));
            return editor.commit();
        } catch (Exception e) {
            Log.e(TAG, "Save critical failure", e);
            return false;
        }
    }

    public static List<EmergencyContact> getEmergencyContacts(Context context) {
        if (context == null) return new ArrayList<>();
        try {
            String json = getPrefs(context).getString(KEY_CONTACTS, null);
            if (json == null || json.isEmpty() || json.equals("null") || json.equals("[]")) {
                // ⚠️ SAFETY: Default contacts use PLACEHOLDER numbers that CANNOT reach
                // any real emergency service. Before deploying to production, the developer
                // MUST replace these with actual helpline numbers for the target region.
                //
                // Production defaults for India:
                //   Police:   112  (pan-India emergency)
                //   Ambulance: 108
                //   Women:    1091
                //
                // These zeros ensure that accidental test triggers never reach real services.
                List<EmergencyContact> defaults = new ArrayList<>();
                defaults.add(new EmergencyContact("Police (NOT CONFIGURED)", "0000000000", "Emergency — SET REAL NUMBER IN PRODUCTION"));
                defaults.add(new EmergencyContact("Ambulance (NOT CONFIGURED)", "0000000000", "Medical — SET REAL NUMBER IN PRODUCTION"));
                defaults.add(new EmergencyContact("Women Helpline (NOT CONFIGURED)", "0000000000", "Emergency — SET REAL NUMBER IN PRODUCTION"));
                
                // Save defaults automatically so they appear in Emergency Contacts UI
                saveEmergencyContacts(context, defaults);
                return defaults;
            }

            EmergencyContact[] array = new Gson().fromJson(json, EmergencyContact[].class);
            if (array != null) return new ArrayList<>(Arrays.asList(array));
        } catch (Exception e) {
            Log.e(TAG, "Retrieval critical failure", e);
        }
        return new ArrayList<>();
    }

    public static boolean addEmergencyContact(Context context, EmergencyContact contact) {
        if (context == null || contact == null) return false;
        List<EmergencyContact> contacts = getEmergencyContacts(context);
        contacts.add(contact);
        return saveEmergencyContacts(context, contacts);
    }

    public static boolean hasEmergencyContacts(Context context) {
        return !getEmergencyContacts(context).isEmpty();
    }
}
