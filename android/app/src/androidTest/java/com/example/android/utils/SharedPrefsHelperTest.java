package com.example.android.utils;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.android.models.ThreatEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Android instrumented tests for SharedPrefsHelper.
 * Requires a device/emulator because EncryptedSharedPreferences is used.
 */
@RunWith(AndroidJUnit4.class)
public class SharedPrefsHelperTest {

    private SharedPrefsHelper prefsHelper;
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        prefsHelper = new SharedPrefsHelper(context);
        // Start clean
        prefsHelper.clearAll();
    }

    @After
    public void tearDown() {
        prefsHelper.clearAll();
    }

    // --- Protection ---

    @Test
    public void protectionEnabled_defaultIsFalse() {
        assertFalse("Protection should be disabled by default",
                prefsHelper.isProtectionEnabled());
    }

    @Test
    public void protectionEnabled_setAndGet() {
        prefsHelper.setProtectionEnabled(true);
        assertTrue("Should return true after setting enabled", prefsHelper.isProtectionEnabled());

        prefsHelper.setProtectionEnabled(false);
        assertFalse("Should return false after setting disabled", prefsHelper.isProtectionEnabled());
    }

    // --- Auto Call ---

    @Test
    public void autoCallEnabled_defaultIsTrue() {
        assertTrue("Auto-call should be enabled by default",
                prefsHelper.isAutoCallEnabled());
    }

    @Test
    public void autoCallEnabled_setAndGet() {
        prefsHelper.setAutoCallEnabled(false);
        assertFalse(prefsHelper.isAutoCallEnabled());

        prefsHelper.setAutoCallEnabled(true);
        assertTrue(prefsHelper.isAutoCallEnabled());
    }

    // --- Sensitivity ---

    @Test
    public void sensitivity_defaultIsCorrect() {
        assertEquals("Default sensitivity should be 0.45",
                0.45f, prefsHelper.getSensitivity(), 0.001f);
    }

    @Test
    public void sensitivity_setAndGet() {
        prefsHelper.setSensitivity(0.7f);
        assertEquals(0.7f, prefsHelper.getSensitivity(), 0.001f);
    }

    // --- User Name ---

    @Test
    public void userName_defaultIsEmpty() {
        assertEquals("Default user name should be empty", "", prefsHelper.getUserName());
    }

    @Test
    public void userName_setAndGet() {
        prefsHelper.setUserName("Test User");
        assertEquals("Test User", prefsHelper.getUserName());
    }

    // --- User ID ---

    @Test
    public void userId_setAndGet() {
        prefsHelper.setUserId("test_uid_123");
        assertEquals("test_uid_123", prefsHelper.getUserId());
    }

    // --- Detection Count ---

    @Test
    public void detectionCount_defaultIsZero() {
        assertEquals(0, prefsHelper.getDetectionCount());
    }

    @Test
    public void detectionCount_increment() {
        prefsHelper.incrementDetectionCount();
        assertEquals(1, prefsHelper.getDetectionCount());

        prefsHelper.incrementDetectionCount();
        assertEquals(2, prefsHelper.getDetectionCount());
    }

    // --- Threat Count ---

    @Test
    public void threatCount_defaultIsZero() {
        assertEquals(0, prefsHelper.getThreatCount());
    }

    @Test
    public void threatCount_increment() {
        prefsHelper.incrementThreatCount();
        assertEquals(1, prefsHelper.getThreatCount());

        prefsHelper.incrementThreatCount();
        assertEquals(2, prefsHelper.getThreatCount());
    }

    // --- Duress PIN ---

    @Test
    public void duressPin_defaultIs9911() {
        assertEquals("Default duress PIN should be 9911",
                "9911", prefsHelper.getDuressPin());
    }

    @Test
    public void duressPin_setAndGet() {
        prefsHelper.setDuressPin("1234");
        assertEquals("1234", prefsHelper.getDuressPin());
    }

    // --- Incognito Mode ---

    @Test
    public void incognitoEnabled_defaultIsFalse() {
        assertFalse(prefsHelper.isIncognitoEnabled());
    }

    @Test
    public void incognitoEnabled_setAndGet() {
        prefsHelper.setIncognitoEnabled(true);
        assertTrue(prefsHelper.isIncognitoEnabled());

        prefsHelper.setIncognitoEnabled(false);
        assertFalse(prefsHelper.isIncognitoEnabled());
    }

    // --- Safe Word ---

    @Test
    public void safeWord_defaultIsPineapple() {
        assertEquals("pineapple", prefsHelper.getSafeWord());
    }

    @Test
    public void safeWord_setAndGet() {
        prefsHelper.setSafeWord("help");
        assertEquals("help", prefsHelper.getSafeWord());
    }

    @Test
    public void safeWord_setEmpty_doesNotChange() {
        String original = prefsHelper.getSafeWord();
        prefsHelper.setSafeWord("");
        assertEquals("Setting empty safe word should not change it",
                original, prefsHelper.getSafeWord());
    }

    @Test
    public void safeWord_setNull_doesNotChange() {
        String original = prefsHelper.getSafeWord();
        prefsHelper.setSafeWord(null);
        assertEquals("Setting null safe word should not change it",
                original, prefsHelper.getSafeWord());
    }

    // --- Clear All ---

    @Test
    public void clearAll_resetsAllValues() {
        prefsHelper.setUserName("Test User");
        prefsHelper.setProtectionEnabled(true);
        prefsHelper.setSensitivity(0.9f);
        prefsHelper.incrementDetectionCount();

        prefsHelper.clearAll();

        assertEquals("", prefsHelper.getUserName());
        assertFalse(prefsHelper.isProtectionEnabled());
        assertEquals(0.45f, prefsHelper.getSensitivity(), 0.001f);
        assertEquals(0, prefsHelper.getDetectionCount());
    }
}
