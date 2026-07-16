package com.example.android.activities;

import android.content.Intent;

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
 * Espresso tests for ProfileActivity.
 * Tests profile display in local mode (no Firebase), logout button.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class ProfileActivityEspressoTest {

    @Rule
    public DismissKeyguardRule dismissKeyguard = new DismissKeyguardRule();

    @Before
    public void setUp() {
        SharedPrefsHelper prefs = new SharedPrefsHelper(
                ApplicationProvider.getApplicationContext());
        prefs.clearAll();
        prefs.setUserId("test_uid_123");
        prefs.setUserName("Espresso Tester");

        androidx.test.core.app.ActivityScenario.launch(
                new Intent(ApplicationProvider.getApplicationContext(),
                        ProfileActivity.class));
    }

    @After
    public void tearDown() {
        SharedPrefsHelper prefs = new SharedPrefsHelper(
                ApplicationProvider.getApplicationContext());
        prefs.clearAll();
    }

    @Test
    public void profileScreen_displaysTitle() {
        Espresso.onView(ViewMatchers.withText("Guardian Profile"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void profileScreen_displaysUserName() {
        Espresso.onView(ViewMatchers.withId(R.id.profile_name))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Espresso Tester"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void profileScreen_displaysEmail() {
        Espresso.onView(ViewMatchers.withId(R.id.profile_email))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void profileScreen_displaysAddress() {
        Espresso.onView(ViewMatchers.withId(R.id.profile_address))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void profileScreen_displaysUID() {
        Espresso.onView(ViewMatchers.withId(R.id.profile_uid))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void profileScreen_showsLocalModeInfo() {
        // Without Firebase, the profile shows simulation mode info
        Espresso.onView(ViewMatchers.withText("Simulation Mode (No Google Config)"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Stored Locally on Device"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void profileScreen_displaysLogoutButton() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_logout))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Logout & Deactivate"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void profileScreen_displaysAvatar() {
        Espresso.onView(ViewMatchers.withId(R.id.profile_image))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void profileScreen_displaysAddressCard() {
        Espresso.onView(ViewMatchers.withText("REGISTERED HOME ADDRESS"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void profileScreen_displaysSecurityUIDCard() {
        Espresso.onView(ViewMatchers.withText("SECURITY USER ID"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void logoutButton_navigatesToLogin() {
        Intents.init();
        try {
            Espresso.onView(ViewMatchers.withId(R.id.btn_logout))
                    .perform(ViewActions.scrollTo(), ViewActions.click());
            Intents.intended(IntentMatchers.hasComponent(LoginActivity.class.getName()));
        } finally {
            Intents.release();
        }

        Espresso.onView(ViewMatchers.withId(R.id.btn_skip_login))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Continue as Guest"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }
}
