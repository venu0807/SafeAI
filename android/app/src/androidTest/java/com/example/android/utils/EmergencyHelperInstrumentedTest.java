package com.example.android.utils;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.android.models.EmergencyContact;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Android instrumented tests for EmergencyHelper.
 * Requires a device/emulator because EncryptedSharedPreferences is used.
 */
@RunWith(AndroidJUnit4.class)
public class EmergencyHelperInstrumentedTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        // Clear existing contacts by overwriting with empty list
        EmergencyHelper.saveEmergencyContacts(context, new java.util.ArrayList<>());
    }

    @After
    public void tearDown() {
        EmergencyHelper.saveEmergencyContacts(context, new java.util.ArrayList<>());
    }

    @Test
    public void getEmergencyContacts_whenEmpty_returnsDefaults() {
        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(context);

        assertNotNull("Should return non-null list", contacts);
        assertFalse("Should return default contacts when empty", contacts.isEmpty());

        // Default contacts should include standard emergency numbers
        boolean hasPolice = false;
        for (EmergencyContact c : contacts) {
            if ("112".equals(c.getPhoneNumber())) {
                hasPolice = true;
                break;
            }
        }
        assertTrue("Default contacts should include Police (112)", hasPolice);
    }

    @Test
    public void getEmergencyContacts_whenEmpty_hasThreeDefaults() {
        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(context);
        assertTrue("Should have at least 3 default contacts, got: " + contacts.size(),
                contacts.size() >= 3);
    }

    @Test
    public void addEmergencyContact_addsToList() {
        EmergencyContact contact = new EmergencyContact("Test User", "+1234567890", "Friend");

        boolean added = EmergencyHelper.addEmergencyContact(context, contact);

        assertTrue("addEmergencyContact should return true", added);

        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(context);
        boolean found = false;
        for (EmergencyContact c : contacts) {
            if ("+1234567890".equals(c.getPhoneNumber())) {
                found = true;
                assertEquals("Test User", c.getName());
                assertEquals("Friend", c.getRelationship());
                break;
            }
        }
        assertTrue("Added contact should be in the list", found);
    }

    @Test
    public void addEmergencyContact_multipleContacts_allRetained() {
        EmergencyHelper.addEmergencyContact(context, new EmergencyContact("Alice", "+111", "Sister"));
        EmergencyHelper.addEmergencyContact(context, new EmergencyContact("Bob", "+222", "Brother"));
        EmergencyHelper.addEmergencyContact(context, new EmergencyContact("Charlie", "+333", "Friend"));

        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(context);
        // Should have defaults + 3 added contacts = at least 6
        assertTrue("Should retain all added contacts plus defaults, got: " + contacts.size(),
                contacts.size() >= 6);
    }

    @Test
    public void hasEmergencyContacts_afterAdding_returnsTrue() {
        assertTrue("Should have default emergency contacts",
                EmergencyHelper.hasEmergencyContacts(context));

        EmergencyHelper.addEmergencyContact(context,
                new EmergencyContact("Extra", "+999", "Backup"));
        assertTrue(EmergencyHelper.hasEmergencyContacts(context));
    }

    @Test
    public void saveEmergencyContacts_overwritesCorrectly() {
        EmergencyHelper.addEmergencyContact(context,
                new EmergencyContact("Original", "+111", "Test"));

        // Now save a completely new list
        java.util.List<EmergencyContact> newList = new java.util.ArrayList<>();
        newList.add(new EmergencyContact("Replacement", "+222", "Updated"));
        boolean saved = EmergencyHelper.saveEmergencyContacts(context, newList);

        assertTrue("save should return true", saved);

        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(context);
        // Should NOT contain the original contact anymore
        boolean hasOriginal = false;
        for (EmergencyContact c : contacts) {
            if ("+111".equals(c.getPhoneNumber())) {
                hasOriginal = true;
                break;
            }
        }
        assertFalse("Original contact should be gone after overwrite", hasOriginal);

        // Should contain the replacement
        boolean hasReplacement = false;
        for (EmergencyContact c : contacts) {
            if ("+222".equals(c.getPhoneNumber())) {
                hasReplacement = true;
                break;
            }
        }
        assertTrue("Replacement contact should exist", hasReplacement);
    }

    @Test
    public void saveEmergencyContacts_withNullContacts_returnsFalse() {
        boolean result = EmergencyHelper.saveEmergencyContacts(context, null);
        assertFalse("Saving null contacts should return false", result);
    }

    @Test
    public void addEmergencyContact_withNullContact_returnsFalse() {
        boolean result = EmergencyHelper.addEmergencyContact(context, null);
        assertFalse("Adding null contact should return false", result);
    }

    @Test
    public void addEmergencyContact_withNullContext_returnsFalse() {
        boolean result = EmergencyHelper.addEmergencyContact(null,
                new EmergencyContact("Test", "+111", "Test"));
        assertFalse("Adding contact with null context should return false", result);
    }

    @Test
    public void getEmergencyContacts_withNullContext_returnsEmptyList() {
        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(null);
        assertNotNull("Should return non-null list", contacts);
        assertTrue("Should return empty list for null context", contacts.isEmpty());
    }

    @Test
    public void multipleAddAndRetrieve_roundTripsCorrectly() {
        EmergencyContact c1 = new EmergencyContact("Mother", "+111", "Mother");
        EmergencyContact c2 = new EmergencyContact("Father", "+222", "Father");
        EmergencyContact c3 = new EmergencyContact("Sister", "+333", "Sister");

        EmergencyHelper.addEmergencyContact(context, c1);
        EmergencyHelper.addEmergencyContact(context, c2);
        EmergencyHelper.addEmergencyContact(context, c3);

        List<EmergencyContact> contacts = EmergencyHelper.getEmergencyContacts(context);

        assertEquals("Mother", findContactByPhone(contacts, "+111").getName());
        assertEquals("Father", findContactByPhone(contacts, "+222").getName());
        assertEquals("Sister", findContactByPhone(contacts, "+333").getName());
    }

    private EmergencyContact findContactByPhone(List<EmergencyContact> contacts, String phone) {
        for (EmergencyContact c : contacts) {
            if (phone.equals(c.getPhoneNumber())) {
                return c;
            }
        }
        return null;
    }
}
