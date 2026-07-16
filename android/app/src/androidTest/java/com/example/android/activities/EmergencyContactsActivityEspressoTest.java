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
import com.example.android.models.EmergencyContact;
import com.example.android.utils.DismissKeyguardRule;
import com.example.android.utils.EmergencyHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

/**
 * Espresso tests for EmergencyContactsActivity.
 * Tests default contacts, FAB dialog, add/delete flows.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class EmergencyContactsActivityEspressoTest {

    @Rule
    public DismissKeyguardRule dismissKeyguard = new DismissKeyguardRule();

    @Before
    public void setUp() {
        // Clear contacts so we start with known state
        EmergencyHelper.saveEmergencyContacts(
                ApplicationProvider.getApplicationContext(), new ArrayList<>());

        androidx.test.core.app.ActivityScenario.launch(
                new Intent(ApplicationProvider.getApplicationContext(),
                        EmergencyContactsActivity.class));
    }

    @After
    public void tearDown() {
        EmergencyHelper.saveEmergencyContacts(
                ApplicationProvider.getApplicationContext(), new ArrayList<>());
    }

    @Test
    public void contactsScreen_displaysTitle() {
        Espresso.onView(ViewMatchers.withText("Emergency Contacts"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void contactsScreen_displaysDefaultContacts() {
        // Default contacts should include Police (112)
        Espresso.onView(ViewMatchers.withText("Police"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Ambulance"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void contactsScreen_displaysAddFAB() {
        Espresso.onView(ViewMatchers.withId(R.id.fab_add_contact))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Add Contact"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void fab_click_showsAddOptionsDialog() {
        Espresso.onView(ViewMatchers.withId(R.id.fab_add_contact))
                .perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withText("Add Contact"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        // Dialog should show two options
        Espresso.onView(ViewMatchers.withText("Enter Manually"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("Choose from Contacts"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void addManually_showsAddContactDialog() {
        Espresso.onView(ViewMatchers.withId(R.id.fab_add_contact))
                .perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withText("Enter Manually"))
                .perform(ViewActions.click());

        // Add contact dialog should appear
        Espresso.onView(ViewMatchers.withText("New Emergency Contact"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withId(R.id.input_name))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withId(R.id.input_phone))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void cancelAddDialog_returnsToContacts() {
        Espresso.onView(ViewMatchers.withId(R.id.fab_add_contact))
                .perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withText("Enter Manually"))
                .perform(ViewActions.click());

        // Cancel the dialog
        Espresso.onView(ViewMatchers.withText("Cancel"))
                .perform(ViewActions.click());

        // Should return to contacts list
        Espresso.onView(ViewMatchers.withText("Police"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void defaultContacts_havePhoneNumbers() {
        Espresso.onView(ViewMatchers.withText("112"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("108"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));

        Espresso.onView(ViewMatchers.withText("1091"))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void contactsScreen_displaysRecyclerView() {
        Espresso.onView(ViewMatchers.withId(R.id.rv_contacts))
                .check(ViewAssertions.matches(ViewMatchers.isDisplayed()));
    }

    @Test
    public void contactsScreen_dismissDialog_cancelsCorrectly() {
        Espresso.onView(ViewMatchers.withId(R.id.fab_add_contact))
                .perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withText("Enter Manually"))
                .perform(ViewActions.click());

        Espresso.onView(ViewMatchers.withText("Cancel"))
                .perform(ViewActions.click());
    }
}
