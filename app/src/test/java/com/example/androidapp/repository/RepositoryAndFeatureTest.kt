package com.example.androidapp.repository

import com.example.androidapp.data.IncomingLocationShare
import com.example.androidapp.data.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryAndFeatureTest {

    @Test
    fun testQuickExitRepositoryBehavior() {
        val repo = QuickExitRepository()
        assertFalse(repo.isQuickExitRequested())

        repo.requestQuickExit()
        assertTrue(repo.isQuickExitRequested())

        repo.resetQuickExitRequest()
        assertFalse(repo.isQuickExitRequested())
    }

    @Test
    fun testAccountInformationAndSettingsDefaults() {
        val info = AccountInformation(
            firstName = "John",
            lastName = "Doe",
            email = "john@example.com",
            phoneNumber = "555-1234",
            address = "123 Main St",
            accountType = "Normal User"
        )
        assertEquals("John", info.firstName)
        assertEquals("Doe", info.lastName)
        assertEquals("john@example.com", info.email)
        assertEquals("555-1234", info.phoneNumber)
        assertEquals("123 Main St", info.address)
        assertEquals("Normal User", info.accountType)

        val doc = UserDocument(
            id = "doc1",
            name = "ID Card",
            type = "Verification",
            storagePath = "/storage/id.pdf"
        )
        assertEquals("doc1", doc.id)
        assertEquals("ID Card", doc.name)
        assertEquals("Verification", doc.type)
        assertEquals("/storage/id.pdf", doc.storagePath)

        val settings = UserSettings(
            accountInformation = info,
            locationSharingEnabled = true,
            disguiseEnabled = false,
            emailNotificationsEnabled = true,
            disguisePasscodeConfigured = true,
            uploadedDocuments = listOf(doc)
        )
        assertTrue(settings.locationSharingEnabled)
        assertFalse(settings.disguiseEnabled)
        assertTrue(settings.emailNotificationsEnabled)
        assertTrue(settings.disguisePasscodeConfigured)
        assertEquals(1, settings.uploadedDocuments.size)
        assertEquals("doc1", settings.uploadedDocuments[0].id)
    }

    @Test
    fun testUserProfileAndIncomingLocationShareModels() {
        val profile = UserProfile(
            displayName = "Jane",
            legalName = "Jane Smith",
            email = "jane@example.com",
            phoneNumber = "555-9876",
            address = "456 Oak Rd",
            role = "user",
            accountStatus = "active",
            regionId = "region_1"
        )
        assertEquals("Jane", profile.displayName)
        assertEquals("Jane Smith", profile.legalName)
        assertEquals("jane@example.com", profile.email)
        assertEquals("555-9876", profile.phoneNumber)
        assertEquals("456 Oak Rd", profile.address)
        assertEquals("user", profile.role)
        assertEquals("active", profile.accountStatus)
        assertEquals("region_1", profile.regionId)

        val incomingShare = IncomingLocationShare(
            ownerId = "owner_99",
            latitude = 45.5152,
            longitude = -122.6784,
            active = true
        )
        assertEquals("owner_99", incomingShare.ownerId)
        assertEquals(45.5152, incomingShare.latitude, 0.0001)
        assertEquals(-122.6784, incomingShare.longitude, 0.0001)
        assertTrue(incomingShare.active)
    }
}
