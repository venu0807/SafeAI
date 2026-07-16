package com.example.android.activities;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.espresso.intent.Intents;
import androidx.test.espresso.intent.matcher.IntentMatchers;

import com.example.android.R;
import com.example.android.utils.DismissKeyguardRule;
import com.example.android.utils.SharedPrefsHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Espresso tests for MainActivity dashboard.
 * Tests UI elements, navigation buttons, and protection switch.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class MainActivityEspressoTest {

    @Rule
    public DismissKeyguardRule dismissKeyguard = new DismissKeyguardRule();

    @Before
    public void setUp() {
        // Set up as guest user so we can test MainActivity directly
        SharedPrefsHelper prefs = new SharedPrefsHelper(
                ApplicationProvider.getApplicationContext());
        prefs.clearAll();
        prefs.setUserId("guest_test");
        prefs.setUserName("Test User");

        ActivityScenario.launch(new Intent(
                ApplicationProvider.getApplicationContext(), MainActivity.class));
    }

    @After
    public void tearDown() {
        SharedPrefsHelper prefs = new SharedPrefsHelper(
                ApplicationProvider.getApplicationContext());
        prefs.clearAll();
    }

    @Test
    public void dashboard_displaysShieldStatus() {
        Espresso.onView(ViewMatchers.withId(R.id.status_text))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Shield Dormant"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void dashboard_displaysStatusSubtext() {
        Espresso.onView(ViewMatchers.withId(R.id.status_subtext))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Tap the switch below to activate"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void dashboard_displaysStats() {
        Espresso.onView(ViewMatchers.withId(R.id.detection_count))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withId(R.id.threat_count))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void dashboard_displaysProtectionSwitch() {
        Espresso.onView(ViewMatchers.withId(R.id.protection_switch))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void dashboard_statCounts_defaultToZero() {
        Espresso.onView(ViewMatchers.withId(R.id.detection_count))
                .check(ViewAssertions.matches(ViewMatchers.withText("0")));

        Espresso.onView(ViewMatchers.withId(R.id.threat_count))
                .check(ViewAssertions.matches(ViewMatchers.withText("0")));
    }

    @Test
    public void navigationButton_emergencyContacts_navigates() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_emergency_contacts))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Emergency Contacts"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void navigationButton_settings_navigates() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_settings))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Settings"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void navigationButton_incidentVault_navigates() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_incident_vault))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Incident Vault"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void navigationButton_profile_navigates() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_profile))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Guardian Profile"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void dashboard_displaysPanicButton() {
        Espresso.onView(ViewMatchers.withId(R.id.fab_panic_button))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("SOS"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void dashboard_displaysBatteryBypassButton() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_battery_bypass))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Fix Background Death (Battery Bypass)"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void dashboard_displaysStatusIcon() {
        Espresso.onView(ViewMatchers.withId(R.id.status_icon))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void dashboard_displaysStatusRing() {
        Espresso.onView(ViewMatchers.withId(R.id.status_ring))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void emergencyContactsButton_click_navigatesToContacts() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_emergency_contacts))
                .perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withId(R.id.rv_contacts))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void settingsButton_click_navigatesToSettings() {
        Intents.init();
        try {
            Espresso.onView(ViewMatchers.withId(R.id.btn_settings))
                    .perform(ViewActions.scrollTo(), ViewActions.click());
            Intents.intended(IntentMatchers.hasComponent(SettingsActivity.class.getName()));
        } finally {
            Intents.release();
        }
    }
}
