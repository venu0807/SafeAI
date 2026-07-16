package com.example.android.activities;

import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.assertion.ViewAssertions;
import androidx.test.espresso.matcher.ViewMatchers;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.android.R;
import com.example.android.utils.DismissKeyguardRule;
import com.example.android.utils.EmergencyHelper;
import com.example.android.utils.SharedPrefsHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Espresso tests for OnboardingActivity.
 * Tests form validation, field filling, and navigation to MainActivity.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class OnboardingActivityEspressoTest {

    @Rule
    public DismissKeyguardRule dismissKeyguard = new DismissKeyguardRule();

    @Before
    public void setUp() {
        SharedPrefsHelper prefs = new SharedPrefsHelper(
                ApplicationProvider.getApplicationContext());
        prefs.clearAll();
        EmergencyHelper.saveEmergencyContacts(
                ApplicationProvider.getApplicationContext(), new ArrayList<>());

        androidx.test.core.app.ActivityScenario.launch(
                new Intent(ApplicationProvider.getApplicationContext(),
                        OnboardingActivity.class));
    }

    @After
    public void tearDown() {
        SharedPrefsHelper prefs = new SharedPrefsHelper(
                ApplicationProvider.getApplicationContext());
        prefs.clearAll();
        EmergencyHelper.saveEmergencyContacts(
                ApplicationProvider.getApplicationContext(), new ArrayList<>());
    }

    @Test
    public void onboardingScreen_displaysTitle() {
        Espresso.onView(ViewMatchers.withText("Complete Setup"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void onboardingScreen_displaysSubtitle() {
        Espresso.onView(ViewMatchers.withText(
                "Set up your profile and primary emergency contact to get started."))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void onboardingScreen_displaysAllFormFields() {
        Espresso.onView(ViewMatchers.withId(R.id.et_onboard_name))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withId(R.id.et_onboard_address))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withId(R.id.et_contact_name))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withId(R.id.et_contact_phone))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void onboardingScreen_displaysFinishButton() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_finish_onboarding))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Finish Setup"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void onboardingScreen_displaysSectionHeaders() {
        Espresso.onView(ViewMatchers.withText("Your Profile"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Primary Emergency Contact"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void finishSetup_button_isClickable() {
        Espresso.onView(ViewMatchers.withId(R.id.btn_finish_onboarding))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isClickable()));
    }

    @Test
    public void finishSetup_fillsFormAndNavigatesToMain() {
        // Fill in all fields
        Espresso.onView(ViewMatchers.withId(R.id.et_onboard_name))
                .perform(ViewActions.scrollTo(), ViewActions.replaceText("Test User"), ViewActions.closeSoftKeyboard());

        Espresso.onView(ViewMatchers.withId(R.id.et_onboard_address))
                .perform(ViewActions.scrollTo(), ViewActions.replaceText("123 Test St"), ViewActions.closeSoftKeyboard());

        Espresso.onView(ViewMatchers.withId(R.id.et_contact_name))
                .perform(ViewActions.scrollTo(), ViewActions.replaceText("Emergency Contact"), ViewActions.closeSoftKeyboard());

        Espresso.onView(ViewMatchers.withId(R.id.et_contact_phone))
                .perform(ViewActions.scrollTo(), ViewActions.replaceText("+11234567890"), ViewActions.closeSoftKeyboard());

        // Click finish
        Espresso.onView(ViewMatchers.withId(R.id.btn_finish_onboarding))
                .perform(ViewActions.scrollTo(), ViewActions.click());

        // Should navigate to MainActivity
        Espresso.onView(ViewMatchers.withId(R.id.protection_switch))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void inputFields_acceptText() {
        Espresso.onView(ViewMatchers.withId(R.id.et_onboard_name))
                .perform(ViewActions.scrollTo(), ViewActions.replaceText("John Doe"), ViewActions.closeSoftKeyboard());

        Espresso.onView(ViewMatchers.withText("John Doe"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void inputFields_phoneAcceptsNumbers() {
        Espresso.onView(ViewMatchers.withId(R.id.et_contact_phone))
                .perform(ViewActions.scrollTo(), ViewActions.replaceText("9876543210"), ViewActions.closeSoftKeyboard());

        Espresso.onView(ViewMatchers.withText("9876543210"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void onboardingScreen_displaysFieldHints() {
        Espresso.onView(ViewMatchers.withHint("Your Full Name"))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withHint("Home Address"))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withHint("Contact Person Name"))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withHint("Contact Phone Number"))
                .perform(ViewActions.scrollTo())
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }
}
