package com.example.androidapp.pages.contacts

import com.example.androidapp.data.TrustedContact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactsTest {

    @Test
    fun testTrustedContactDefaultValues() {
        val contact = TrustedContact()
        assertEquals("", contact.id)
        assertEquals("", contact.name)
        assertEquals("", contact.phoneNumber)
        assertEquals("", contact.relationship)
        assertFalse(contact.accessGiven)
        assertEquals(null, contact.linkedUserId)
        assertEquals("", contact.privateNotes)
        assertFalse(contact.safeToStay)
    }

    @Test
    fun testTrustedContactCustomValues() {
        val contact = TrustedContact(
            id = "123",
            name = "Jane Doe",
            phoneNumber = "555-0199",
            relationship = "Friend",
            accessGiven = true,
            linkedUserId = "user_abc",
            privateNotes = "Keep safe note",
            safeToStay = true
        )
        assertEquals("123", contact.id)
        assertEquals("Jane Doe", contact.name)
        assertEquals("555-0199", contact.phoneNumber)
        assertEquals("Friend", contact.relationship)
        assertTrue(contact.accessGiven)
        assertEquals("user_abc", contact.linkedUserId)
        assertEquals("Keep safe note", contact.privateNotes)
        assertTrue(contact.safeToStay)
    }

    @Test
    fun testContactsViewModelInitialState() {
        val viewModel = ContactsViewModel()

        assertTrue(viewModel.contacts.isEmpty())
        assertFalse(viewModel.loading)
        assertFalse(viewModel.saving)
        assertEquals(null, viewModel.operationError)
        assertEquals(null, viewModel.accountUid)
        assertFalse(viewModel.hasUnsavedChanges)
    }

    @Test
    fun testDiscardDraftClearsUnsavedChanges() {
        val viewModel = ContactsViewModel()

        viewModel.updateName("John")
        viewModel.updatePhone("555-0199")
        viewModel.toggleContactType(ContactType.TRUSTED, true)
        viewModel.updateAccessGiven(true)
        viewModel.updateSafeToStay(true)

        assertTrue(viewModel.hasUnsavedChanges)
        assertEquals("John", viewModel.draftName)
        assertTrue(ContactType.TRUSTED in viewModel.draftTypes)

        viewModel.discardDraft()

        assertEquals("", viewModel.draftName)
        assertEquals("", viewModel.draftPhone)
        assertTrue(viewModel.draftTypes.isEmpty())
        assertFalse(viewModel.draftAccessGiven)
        assertFalse(viewModel.draftSafeToStay)
        assertFalse(viewModel.hasUnsavedChanges)
    }


}
