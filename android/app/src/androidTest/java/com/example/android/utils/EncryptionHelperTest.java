package com.example.android.utils;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Android instrumented tests for EncryptionHelper.
 * Requires a device/emulator because Android KeyStore is used.
 */
@RunWith(AndroidJUnit4.class)
public class EncryptionHelperTest {

    private EncryptionHelper encryptionHelper;

    @Before
    public void setUp() {
        // EncryptionHelper uses Android KeyStore which requires a real Android environment
        encryptionHelper = new EncryptionHelper();
    }

    @After
    public void tearDown() {
        encryptionHelper = null;
    }

    @Test
    public void encryptDecrypt_roundTrip_returnsOriginalData() throws Exception {
        byte[] original = "Hello, SafeGuard AI!".getBytes();

        byte[] encrypted = encryptionHelper.encrypt(original);
        byte[] decrypted = encryptionHelper.decrypt(encrypted);

        assertArrayEquals("Decrypted data should match original", original, decrypted);
    }

    @Test
    public void encrypt_changesData() throws Exception {
        byte[] original = "Sensitive emergency contact data".getBytes();

        byte[] encrypted = encryptionHelper.encrypt(original);

        assertFalse("Encrypted data should differ from original",
                Arrays.equals(original, encrypted));
    }

    @Test
    public void encryptDecrypt_withEmptyData_returnsEmpty() throws Exception {
        byte[] original = new byte[0];

        byte[] encrypted = encryptionHelper.encrypt(original);
        byte[] decrypted = encryptionHelper.decrypt(encrypted);

        assertArrayEquals("Empty data round-trip should work", original, decrypted);
    }

    @Test
    public void encryptDecrypt_withLargeData_returnsOriginal() throws Exception {
        // Simulate a 100KB chunk of audio data
        byte[] original = new byte[102400];
        for (int i = 0; i < original.length; i++) {
            original[i] = (byte) (i & 0xFF);
        }

        byte[] encrypted = encryptionHelper.encrypt(original);
        byte[] decrypted = encryptionHelper.decrypt(encrypted);

        assertArrayEquals("Large data round-trip should work", original, decrypted);
    }

    @Test
    public void encrypt_producesDifferentOutputEachTime() throws Exception {
        byte[] data = "Consistent input data".getBytes();

        byte[] encrypted1 = encryptionHelper.encrypt(data);
        byte[] encrypted2 = encryptionHelper.encrypt(data);

        // AES-GCM uses a random IV each time, so outputs should differ
        assertFalse("Two encryptions of same data should produce different output",
                Arrays.equals(encrypted1, encrypted2));
    }

    @Test
    public void decrypt_withTamperedData_throwsException() {
        try {
            byte[] original = "Test data for tamper detection".getBytes();
            byte[] encrypted = encryptionHelper.encrypt(original);

            // Tamper with the encrypted data
            encrypted[encrypted.length - 1] ^= 0xFF;

            byte[] decrypted = encryptionHelper.decrypt(encrypted);
            fail("Should have thrown an exception for tampered data. Got: "
                    + Arrays.toString(decrypted));
        } catch (Exception e) {
            // Expected — AES-GCM authentication should detect tampering
            assertTrue("Exception should be thrown for tampered data", true);
        }
    }

    @Test
    public void multipleEncryptDecrypt_roundTripsCorrectly() throws Exception {
        EncryptionHelper helper = new EncryptionHelper();

        byte[][] testData = {
                "First message".getBytes(),
                "Second message with different length!".getBytes(),
                "Third".getBytes(),
                new byte[0]
        };

        for (byte[] data : testData) {
            byte[] encrypted = helper.encrypt(data);
            byte[] decrypted = helper.decrypt(encrypted);
            assertArrayEquals("Round-trip failed for: " + new String(data), data, decrypted);
        }
    }
}
