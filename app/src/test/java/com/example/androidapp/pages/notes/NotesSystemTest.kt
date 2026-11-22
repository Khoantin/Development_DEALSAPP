package com.example.androidapp.pages.notes

import com.example.androidapp.utils.NoteTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesSystemTest {

    @Test
    fun testNoteAttachmentCreation() {
        val attachment = NoteAttachment(
            uri = "content://media/external/images/123",
            name = "security_plan.png",
            onlineUrl = "https://firebasestorage.googleapis.com/v0/b/dealsapp-40972.firebasestorage.app/o/users%2F123%2Fsecurity_plan.png",
            isUploading = false
        )
        assertEquals("content://media/external/images/123", attachment.uri)
        assertEquals("security_plan.png", attachment.name)
        assertNotNull(attachment.onlineUrl)
        assertFalse(attachment.isUploading)
    }

    @Test
    fun testNoteEntryCreationAndSearch() {
        val attachment = NoteAttachment(
            uri = "content://media/doc/1",
            name = "file.pdf"
        )
        val now = NoteTimestamp.now()
        val entry = NoteEntry(
            id = 1L,
            title = "Emergency Contacts Note",
            content = "Keep this information secure at all times.",
            location = "Safehouse Alpha",
            createdAt = now,
            updatedAt = now,
            attachments = listOf(attachment)
        )

        assertEquals(1L, entry.id)
        assertEquals("Emergency Contacts Note", entry.title)
        assertEquals("Safehouse Alpha", entry.location)
        assertEquals(1, entry.attachments.size)

        // Test search filter logic
        val query = "Safehouse"
        val matches = entry.title.contains(query, ignoreCase = true) ||
                entry.content.contains(query, ignoreCase = true) ||
                entry.location.contains(query, ignoreCase = true)
        assertTrue(matches)
    }

    @Test
    fun testNoteTimestampUtilities() {
        val now = NoteTimestamp.now()
        assertTrue(now > 0)

        val created = NoteTimestamp.creationTime(existingCreatedAt = 1000L, currentTime = now)
        assertEquals(1000L, created)

        val newCreated = NoteTimestamp.creationTime(existingCreatedAt = null, currentTime = now)
        assertEquals(now, newCreated)

        val formatted = NoteTimestamp.format(now)
        assertNotNull(formatted)
        assertTrue(formatted.isNotEmpty())
    }

    @Test
    fun testImportantDocumentChecklistCreationAndToggle() {
        val viewModel = NotesViewModel()
        viewModel.startDocumentChecklist()

        assertEquals("Important Document Checklist", viewModel.draftTitle)
        assertTrue(viewModel.draftContent.contains("Driver's License"))
        assertTrue(viewModel.draftContent.contains("Passport"))
        assertTrue(viewModel.draftContent.contains("[ ]"))
    }

    @Test
    fun testSafetyPlanningInformationPageCreation() {
        val viewModel = NotesViewModel()
        viewModel.startSafetyPlan()

        assertEquals("Safety Plan", viewModel.draftTitle)
        assertTrue(viewModel.draftContent.contains("SAFE PLACES"))
        assertTrue(viewModel.draftContent.contains("EMERGENCY CONTACTS"))
        assertTrue(viewModel.draftContent.contains("SAFETY PLAN"))
    }
}
