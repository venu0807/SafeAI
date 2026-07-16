package com.example.android.utils;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Secure Encryption Helper using Android KeyStore (AES-GCM)
 */
public class EncryptionHelper {

    private static final String TAG = "EncryptionHelper";
    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "SafeGuardAudioKey";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    public EncryptionHelper() {
        try {
            initKey();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize encryption key", e);
        }
    }

    private void initKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);

        if (!keyStore.containsAlias(KEY_ALIAS)) {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);
            keyGenerator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            keyGenerator.generateKey();
        }
    }

    private SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
    }

    public byte[] encrypt(byte[] data) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
        byte[] iv = cipher.getIV();
        byte[] encryptedData = cipher.doFinal(data);

        // Prepend IV to encrypted data for decryption later
        byte[] combined = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encryptedData, 0, combined, iv.length, encryptedData.length);
        return combined;
    }

    public byte[] decrypt(byte[] combined) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        byte[] iv = new byte[12]; // GCM standard IV length
        System.arraycopy(combined, 0, iv, 0, iv.length);
        
        byte[] encryptedData = new byte[combined.length - iv.length];
        System.arraycopy(combined, iv.length, encryptedData, 0, encryptedData.length);

        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);
        return cipher.doFinal(encryptedData);
    }
}
