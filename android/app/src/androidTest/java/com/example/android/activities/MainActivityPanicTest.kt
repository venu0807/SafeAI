package com.example.android.activities

import android.Manifest
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose-based instrumented tests for the Panic Button and dashboard UI.
 *
 * Note: The confirmation dialog uses androidx.appcompat.app.AlertDialog
 * (platform, not Compose), so its content cannot be found via ComposeTestRule.
 * We test that:
 * 1. The dashboard renders correctly (title, status, nav buttons)
 * 2. The panic button exists and is clickable without crash
 */
class MainActivityPanicTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CAMERA,
        Manifest.permission.POST_NOTIFICATIONS
    )

    @Test
    fun dashboard_displaysTitle() {
        composeTestRule.onNodeWithText("SafeGuard AI").assertExists()
    }

    @Test
    fun dashboard_displaysStatusIndicator() {
        composeTestRule.onNodeWithText("Shield Dormant").assertExists()
    }

    @Test
    fun panicButton_existsAndIsClickable() {
        composeTestRule.onNodeWithText("PANIC BUTTON").assertExists()
        composeTestRule.onNodeWithText("PANIC BUTTON").performClick()
        // App should still be responsive after click
        composeTestRule.onNodeWithText("SafeGuard AI").assertExists()
    }

    @Test
    fun panicButton_clickDoesNotCrash() {
        composeTestRule.onNodeWithText("PANIC BUTTON").performClick()
        assertTrue("Click completed without crash", true)
    }

    @Test
    fun navigationButtons_areDisplayed() {
        composeTestRule.onNodeWithText("Emergency Contacts").assertExists()
        composeTestRule.onNodeWithText("Incident Vault").assertExists()
        composeTestRule.onNodeWithText("Settings").assertExists()
    }
}
