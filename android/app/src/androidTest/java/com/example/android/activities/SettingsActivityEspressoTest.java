package com.example.android.activities;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.rule.GrantPermissionRule;
import android.Manifest;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.android.R;
import com.example.android.utils.DismissKeyguardRule;
import com.example.android.utils.SharedPrefsHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Espresso tests for SettingsActivity.
 * Tests switches, slider, save button, about dialog.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SettingsActivityEspressoTest {


    @Rule
    public DismissKeyguardRule dismissKeyguard = new DismissKeyguardRule();

    @Before
    public void setUp() {
        // Clear settings for consistent test state
        SharedPrefsHelper prefs = new SharedPrefsHelper(
                ApplicationProvider.getApplicationContext());
        prefs.clearAll();

        androidx.test.core.app.ActivityScenario.launch(
                new Intent(ApplicationProvider.getApplicationContext(),
                        SettingsActivity.class));
    }

    @Test
    public void settingsScreen_displaysTitle() {
        Espresso.onView(ViewMatchers.withText("System Settings"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void settingsScreen_displaysAutoCallSwitch() {
        Espresso.onView(ViewMatchers.withId(R.id.switch_auto_call))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Automatic Emergency Call"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void settingsScreen_displaysIncognitoSwitch() {
        Espresso.onView(ViewMatchers.withId(R.id.switch_incognito))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Incognito Mode (Calculator)"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void settingsScreen_displaysSensitivitySlider() {
        Espresso.onView(ViewMatchers.withId(R.id.slider_sensitivity))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void settingsScreen_displaysUserNameField() {
        Espresso.onView(ViewMatchers.withId(R.id.et_user_name))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void settingsScreen_displaysDuressPinField() {
        Espresso.onView(ViewMatchers.withId(R.id.et_duress_pin))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void settingsScreen_displaysSaveButton() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_save_settings))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Save System Config"))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void settingsScreen_displaysAboutButton() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_about))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("About SafeGuard AI"))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void aboutButton_click_showsAboutDialog() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_about))
                .perform(ViewActions.scrollTo(), ViewActions.click());

        // About dialog should appear
        Espresso.onView(ViewMatchers.withText("About SafeGuard AI"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("OK"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void aboutDialog_OK_dismisses() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_about))
                .perform(ViewActions.scrollTo(), ViewActions.click());

        Espresso.onView(ViewMatchers.withText("OK"))
                .perform(ViewActions.click());

        // After dismissing, we should see settings again
        Espresso.onView(ViewMatchers.withId(R.id.btn_save_settings))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void saveButton_isClickable() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_save_settings))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isClickable()));
    }

    @Test
    public void settingsScreen_displaysDetectionSensitivitySection() {
        Espresso.onView(ViewMatchers.withText("AI Detection Sensitivity"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void settingsScreen_displaysVersionInfo() {
        Espresso.onView(ViewMatchers.withId(R.id.text_version))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void autoCallSwitch_isCheckedByDefault() {
        // Auto-call is enabled by default in SharedPrefsHelper
        Espresso.onView(ViewMatchers.withId(R.id.switch_auto_call))
                .check(ViewAssertions.matches(ViewMatchers.isChecked()));
    }
}
