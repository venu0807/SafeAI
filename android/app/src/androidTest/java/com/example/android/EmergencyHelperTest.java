package com.example.android;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.example.android.models.EmergencyContact;
import com.example.android.utils.EmergencyHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Instrumented tests for EmergencyHelper — ensuring default contacts
 * are returned safely and CRUD operations work correctly.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class EmergencyHelperTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // Clear all contacts to prevent state pollution between tests.
        // EmergencyHelper uses static cachedPrefs, so test order matters
        // without explicit cleanup.
        EmergencyHelper.saveEmergencyContacts(context, new ArrayList<>());
    }

    @Test
    public void defaultContacts_areReturned_whenNoneSaved() {
        // Clear any existing contacts by saving an empty list
        EmergencyHelper.saveEmergencyContacts(context, List.of());

        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(context);
        assertNotNull("Contacts list should not be null", contacts);
        assertFalse("Default contacts should be auto-created", contacts.isEmpty());
    }

    @Test
    public void defaultContacts_containPoliceAndAmbulance() {
        // Clear contacts to trigger default creation
        EmergencyHelper.saveEmergencyContacts(context, List.of());

        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(context);
        boolean hasPolice = contacts.stream()
                .anyMatch(c -> c.getName().contains("Police"));
        boolean hasAmbulance = contacts.stream()
                .anyMatch(c -> c.getName().contains("Ambulance"));

        assertTrue("Default contacts should include Police/112", hasPolice);
        assertTrue("Default contacts should include Ambulance/108", hasAmbulance);
    }

    @Test
    public void addContact_persistsAndIsRetrievable() {
        EmergencyContact testContact = new EmergencyContact(
                "Test Person", "+1-555-0000", "Unit Test");

        boolean added = EmergencyHelper.addEmergencyContact(context, testContact);
        assertTrue("Contact should be added successfully", added);

        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(context);
        boolean found = contacts.stream()
                .anyMatch(c -> "+1-555-0000".equals(c.getPhoneNumber()));
        assertTrue("Added contact should be retrievable", found);
    }

    @Test
    public void addMultipleContacts_allAreRetrievable() {
        EmergencyContact contact1 = new EmergencyContact("Alice", "111", "Friend");
        EmergencyContact contact2 = new EmergencyContact("Bob", "222", "Family");

        EmergencyHelper.addEmergencyContact(context, contact1);
        EmergencyHelper.addEmergencyContact(context, contact2);

        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(context);
        // Note: default contacts are prepended, so we search by phone number
        assertTrue("Alice should exist in contacts",
                contacts.stream().anyMatch(c -> "111".equals(c.getPhoneNumber())));
        assertTrue("Bob should exist in contacts",
                contacts.stream().anyMatch(c -> "222".equals(c.getPhoneNumber())));
    }

    @Test
    public void saveEmptyList_restoresDefaults() {
        // Add a contact first
        EmergencyHelper.addEmergencyContact(context,
                new EmergencyContact("Temp", "999", "Temporary"));

        // Save empty list (simulating delete-all)
        boolean saved = EmergencyHelper.saveEmergencyContacts(context, List.of());
        assertTrue("Saving empty list should succeed", saved);

        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(context);
        assertFalse("Default contacts should be restored when list is empty",
                contacts.isEmpty());
    }

    @Test
    public void hasEmergencyContacts_returnsTrue_whenContactsExist() {
        EmergencyHelper.addEmergencyContact(context,
                new EmergencyContact("Test", "1234567890", "Test"));

        assertTrue("hasEmergencyContacts should return true when contacts exist",
                EmergencyHelper.hasEmergencyContacts(context));
    }
}
