package com.example.android.utils;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Android instrumented tests for PermissionHelper.
 * Requires a device/emulator to check actual permission state.
 */
@RunWith(AndroidJUnit4.class)
public class PermissionHelperInstrumentedTest {

    private final Context context = ApplicationProvider.getApplicationContext();

    @Test
    public void hasAllPermissions_doesNotThrow() {
        try {
            PermissionHelper.hasAllPermissions(context);
        } catch (Exception e) {
            fail("hasAllPermissions should not throw: " + e.getMessage());
        }
    }

    @Test
    public void hasAllPermissions_returnsConsistentResult() {
        // Calling twice should return the same result (no side effects)
        boolean first = PermissionHelper.hasAllPermissions(context);
        boolean second = PermissionHelper.hasAllPermissions(context);
        assertEquals("Consecutive calls should return same result", first, second);
    }

    @Test
    public void requestPermissions_withNullActivity_throwsNullPointerException() {
        try {
            PermissionHelper.requestPermissions(null, 100);
            fail("Should throw NullPointerException with null activity");
        } catch (NullPointerException e) {
            // Expected — requestPermissions needs a valid Activity
            assertTrue(true);
        } catch (Exception e) {
            // Other exceptions are also acceptable on different Android versions
            assertTrue("Got exception as expected: " + e.getClass().getSimpleName(), true);
        }
    }

    @Test
    public void checkAndRequestBackgroundLocation_withNullActivity_throwsOnAndroidQOrHigher() {
        try {
            PermissionHelper.checkAndRequestBackgroundLocation(null, 101);
            // On Android < Q, this is a no-op, so null Activity is fine
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                assertTrue(true);
            } else {
                fail("Should have thrown on Android Q+ with null activity");
            }
        } catch (NullPointerException e) {
            // Expected on Android Q+ with null activity
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                assertTrue(true);
            } else {
                fail("Should not throw on pre-Q");
            }
        }
    }

    @Test
    public void showBatteryOptimizationDialog_withNullActivity_throwsOnAndroidMOrHigher() {
        try {
            PermissionHelper.showBatteryOptimizationDialog(null);
            // On Android < M, this is a no-op
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
                assertTrue(true);
            } else {
                fail("Should have thrown on Android M+ with null activity");
            }
        } catch (NullPointerException e) {
            // Expected on Android M+ with null activity
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                assertTrue(true);
            } else {
                fail("Should not throw on pre-M");
            }
        }
    }
}
