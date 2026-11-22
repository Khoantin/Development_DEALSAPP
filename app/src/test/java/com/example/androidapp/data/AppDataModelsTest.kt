package com.example.androidapp.data

import com.example.androidapp.pages.accountsettings.AccountSettingsState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDataModelsTest {

    @Test
    fun testUserProfileDefaultsAndValues() {
        val profile = UserProfile()
        assertEquals("", profile.displayName)
        assertEquals("", profile.legalName)
        assertEquals("", profile.email)
        assertEquals("user", profile.role)
        assertEquals("active", profile.accountStatus)
        assertTrue(profile.uploadedDocuments.isEmpty())

        val customProfile = UserProfile(
            displayName = "Alice",
            legalName = "Alice Smith",
            email = "alice@example.com",
            role = "support",
            accountStatus = "active",
            uploadedDocuments = listOf("ID: passport.pdf")
        )
        assertEquals("Alice", customProfile.displayName)
        assertEquals("Alice Smith", customProfile.legalName)
        assertEquals("alice@example.com", customProfile.email)
        assertEquals("support", customProfile.role)
        assertEquals(listOf("ID: passport.pdf"), customProfile.uploadedDocuments)
    }

    @Test
    fun testIncomingLocationShareDefaultsAndValues() {
        val share = IncomingLocationShare()
        assertEquals("", share.ownerId)
        assertEquals(0.0, share.latitude, 0.0001)
        assertEquals(0.0, share.longitude, 0.0001)
        assertFalse(share.active)

        val customShare = IncomingLocationShare(
            ownerId = "user_123",
            latitude = 37.7749,
            longitude = -122.4194,
            active = true
        )
        assertEquals("user_123", customShare.ownerId)
        assertEquals(37.7749, customShare.latitude, 0.0001)
        assertEquals(-122.4194, customShare.longitude, 0.0001)
        assertTrue(customShare.active)
    }

    @Test
    fun testAccountSettingsStateDefaultsAndCopy() {
        val state = AccountSettingsState()
        assertFalse(state.locationSharingEnabled)
        assertTrue(state.disguiseEnabled)
        assertTrue(state.emailNotificationsEnabled)
        assertTrue(state.uploadedDocuments.isEmpty())

        val updatedState = state.copy(
            locationSharingEnabled = true,
            disguiseEnabled = false
        )
        assertTrue(updatedState.locationSharingEnabled)
        assertFalse(updatedState.disguiseEnabled)
    }
}
