package com.example.android.utils;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * JUnit {@link TestRule} that dismisses the keyguard (lock screen) and wakes
 * the device before each test. This ensures Espresso can interact with the
 * activity instead of timing out waiting for window focus.
 *
 * <p>Usage:
 * <pre>{@code
 * @Rule
 * public DismissKeyguardRule dismissKeyguard = new DismissKeyguardRule();
 * }</pre>
 */
public class DismissKeyguardRule implements TestRule {

    private void runShellCommand(android.app.UiAutomation uiAutomation, String command) {
        try {
            android.os.ParcelFileDescriptor fd = uiAutomation.executeShellCommand(command);
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)))) {
                while (reader.readLine() != null) {}
            }
        } catch (Exception ignored) {}
    }

    private static boolean isInitialized = false;

    @Override
    public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                if (!isInitialized) {
                    var uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

                    // 1. Standard keyguard and wakeup
                    runShellCommand(uiAutomation, "input keyevent KEYCODE_WAKEUP");
                    try { Thread.sleep(200); } catch (Exception e) {}
                    runShellCommand(uiAutomation, "wm dismiss-keyguard");
                    runShellCommand(uiAutomation, "settings put global stay_on_while_plugged_in 3");

                    // 2. Disable animations to prevent focus lag (Critical for API 35)
                    runShellCommand(uiAutomation, "settings put global window_animation_scale 0.0");
                    runShellCommand(uiAutomation, "settings put global transition_animation_scale 0.0");
                    runShellCommand(uiAutomation, "settings put global animator_duration_scale 0.0");

                    // 3. Dismiss system dialogs that steal focus on API 35 (e.g. Edge to Edge tutorial, System UI crash)
                    runShellCommand(uiAutomation, "am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS");
                    runShellCommand(uiAutomation, "settings put secure show_gesture_nav_tutorial 0");
                    runShellCommand(uiAutomation, "settings put secure immersive_mode_confirmations 0");

                    // 4. Disable Predictive Back & Soft Keyboard (API 35 specific focus issues)
                    runShellCommand(uiAutomation, "settings put secure android_predictive_back_enabled 0");
                    runShellCommand(uiAutomation, "settings put secure show_ime_with_hard_keyboard 0");
                    runShellCommand(uiAutomation, "ime disable com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME");
                    runShellCommand(uiAutomation, "ime disable com.android.inputmethod.latin/.LatinIME");
                    
                    isInitialized = true;
                }

                // Spawn a background thread to aggressively dismiss asynchronous system dialogs 
                // like the Android 15 PageSizeMismatchDialog that appear *after* the activity starts.
                var uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
                new Thread(() -> {
                    try {
                        for (int i = 0; i < 5; i++) {
                            Thread.sleep(1000);
                            runShellCommand(uiAutomation, "am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS");
                        }
                    } catch (Exception e) {}
                }).start();

                // Wait for the UI to settle
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }

                base.evaluate();
            }
        };
    }
}
