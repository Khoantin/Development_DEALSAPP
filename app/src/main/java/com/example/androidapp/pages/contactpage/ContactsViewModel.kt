
package com.example.androidapp.pages.contactpage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

// ---------------------------------------------------------
// DATA MODELS
// ---------------------------------------------------------

enum class ContactType(val label: String) {
    TRUSTED("Trusted"),
    EMERGENCY("Emergency"),
    SUPPORT("Support")
}

data class ContactEntry(
    val id: Long,
    val name: String,
    val phone: String,
    val email: String,
    val relationship: String,
    val types: Set<ContactType>,
    val privateNotes: String,
    val createdAt: Long,
    val updatedAt: Long
)

// ---------------------------------------------------------
// CONTACTS VIEWMODEL
// ---------------------------------------------------------

// UI state only. Database persistence can be added later.

class ContactsViewModel : ViewModel() {

    var contacts by mutableStateOf<List<ContactEntry>>(emptyList())
        private set

    var searchQuery by mutableStateOf("")
        private set

    var selectedTab by mutableStateOf(0)
        private set

    var draftName by mutableStateOf("")
        private set

    var draftPhone by mutableStateOf("")
        private set

    var draftEmail by mutableStateOf("")
        private set

    var draftRelationship by mutableStateOf("")
        private set

    var draftTypes by mutableStateOf<Set<ContactType>>(emptySet())
        private set

    var draftPrivateNotes by mutableStateOf("")
        private set

    var editorError by mutableStateOf<String?>(null)
        private set

    var hasUnsavedChanges by mutableStateOf(false)
        private set

    private var editingId: Long? = null
    private var nextId = 1L

    // -----------------------------------------------------
    // SEARCH AND FILTER
    // -----------------------------------------------------

    fun updateSearch(value: String) {
        searchQuery = value
    }

    fun selectTab(index: Int) {
        if (index in 0..3) {
            selectedTab = index
        }
    }

    fun visibleContacts(): List<ContactEntry> {
        val query = searchQuery.trim()

        val typeFilter = when (selectedTab) {
            1 -> ContactType.TRUSTED
            2 -> ContactType.EMERGENCY
            3 -> ContactType.SUPPORT
            else -> null
        }

        return contacts.filter { contact ->
            val matchesType =
                typeFilter == null || typeFilter in contact.types

            val matchesSearch =
                contact.name.contains(query, ignoreCase = true) ||
                        contact.phone.contains(query, ignoreCase = true) ||
                        contact.email.contains(query, ignoreCase = true) ||
                        contact.relationship.contains(query, ignoreCase = true)

            matchesType && matchesSearch
        }.sortedBy { it.name.lowercase() }
    }

    // -----------------------------------------------------
    // FIND CONTACT
    // -----------------------------------------------------

    fun findContact(id: Long): ContactEntry? {
        return contacts.firstOrNull { it.id == id }
    }

    // -----------------------------------------------------
    // CREATE AND EDIT CONTACT
    // -----------------------------------------------------

    fun startNewContact() {
        clearDraft()
    }

    fun startEditing(contact: ContactEntry) {
        editingId = contact.id

        draftName = contact.name
        draftPhone = contact.phone
        draftEmail = contact.email
        draftRelationship = contact.relationship
        draftTypes = contact.types.toSet()
        draftPrivateNotes = contact.privateNotes

        editorError = null
        hasUnsavedChanges = false
    }

    // -----------------------------------------------------
    // UPDATE FIELDS
    // -----------------------------------------------------

    fun updateName(value: String) {
        draftName = value
        markChanged()
    }

    fun updatePhone(value: String) {
        draftPhone = value
        markChanged()
    }

    fun updateEmail(value: String) {
        draftEmail = value
        markChanged()
    }

    fun updateRelationship(value: String) {
        draftRelationship = value
        markChanged()
    }

    fun updatePrivateNotes(value: String) {
        draftPrivateNotes = value
        markChanged()
    }

    // -----------------------------------------------------
    // CONTACT TYPE CHECKBOXES
    // -----------------------------------------------------

    fun toggleContactType(
        type: ContactType,
        checked: Boolean
    ) {
        draftTypes = if (checked) {
            draftTypes + type
        } else {
            draftTypes - type
        }

        markChanged()
    }

    // -----------------------------------------------------
    // SAVE CONTACT
    // -----------------------------------------------------

    fun saveDraft(): Boolean {
        if (draftName.isBlank()) {
            editorError = "Please enter a contact name."
            return false
        }

        if (draftPhone.isBlank()) {
            editorError = "Please enter a phone number."
            return false
        }

        if (draftTypes.isEmpty()) {
            editorError = "Select at least one contact type."
            return false
        }

        val cleanedEmail = draftEmail.trim()

        if (
            cleanedEmail.isNotEmpty() &&
            !android.util.Patterns.EMAIL_ADDRESS
                .matcher(cleanedEmail)
                .matches()
        ) {
            editorError = "Please enter a valid email address."
            return false
        }

        val existing = editingId?.let(::findContact)

        if (editingId != null && existing == null) {
            editorError = "This contact no longer exists."
            return false
        }

        val now = System.currentTimeMillis()

        val entry = ContactEntry(
            id = existing?.id ?: nextId++,
            name = draftName.trim(),
            phone = draftPhone.trim(),
            email = cleanedEmail,
            relationship = draftRelationship.trim(),
            types = draftTypes.toSet(),
            privateNotes = draftPrivateNotes.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )

        contacts = (
                contacts.filterNot { it.id == entry.id } + entry
                ).sortedBy { it.name.lowercase() }

        clearDraft()
        return true
    }

    // -----------------------------------------------------
    // DELETE CONTACT
    // -----------------------------------------------------

    fun deleteContact(id: Long) {
        contacts = contacts.filterNot { it.id == id }
    }

    // -----------------------------------------------------
    // DISCARD CHANGES
    // -----------------------------------------------------

    fun discardDraft() {
        clearDraft()
    }

    // -----------------------------------------------------
    // UNSAVED CHANGES
    // -----------------------------------------------------

    private fun markChanged() {
        val original = editingId?.let(::findContact)

        hasUnsavedChanges =
            draftName != (original?.name ?: "") ||
                    draftPhone != (original?.phone ?: "") ||
                    draftEmail != (original?.email ?: "") ||
                    draftRelationship != (original?.relationship ?: "") ||
                    draftTypes != (
                    original?.types ?: emptySet<ContactType>()
                    ) ||
                    draftPrivateNotes != (original?.privateNotes ?: "")

        editorError = null
    }

    // -----------------------------------------------------
    // RESET FORM
    // -----------------------------------------------------

    private fun clearDraft() {
        editingId = null

        draftName = ""
        draftPhone = ""
        draftEmail = ""
        draftRelationship = ""
        draftTypes = emptySet()
        draftPrivateNotes = ""

        editorError = null
        hasUnsavedChanges = false
    }
}
