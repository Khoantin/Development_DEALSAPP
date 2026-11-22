package com.example.androidapp.pages.contacts

import android.util.Patterns
import com.example.androidapp.data.TrustedContact
import com.example.androidapp.data.TrustedContactRepository
import com.example.androidapp.data.toFriendlyFirestoreMessage
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth

enum class ContactType(val label: String) {
    TRUSTED("Trusted"), EMERGENCY("Emergency"), SUPPORT("Support")
}

data class ContactEntry(
    val id: String,
    val name: String,
    val phone: String,
    val email: String,
    val relationship: String,
    val types: Set<ContactType>,
    val privateNotes: String,
    val createdAt: Long,
    val updatedAt: Long,
    val accessGiven: Boolean = false,
    val safeToStay: Boolean = false
)

class ContactsViewModel : ViewModel() {
    // Lazy initialization also allows the standalone UI previews to render.
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val contactRepository by lazy { TrustedContactRepository() }
    private var savedContacts: List<TrustedContact> = emptyList()
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var generation = 0

    var accountUid by mutableStateOf<String?>(null)
        private set
    var contacts by mutableStateOf<List<ContactEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var operationError by mutableStateOf<String?>(null)
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

    var draftAccessGiven by mutableStateOf(false)
        private set
    var draftSafeToStay by mutableStateOf(false)
        private set
    private var originalRecord: TrustedContact? = null
    private var original: ContactEntry? = null
    private var newDocumentId: String? = null

    fun start() {
        if (authListener != null) return
        val listener = FirebaseAuth.AuthStateListener {
            generation++
            accountUid = it.currentUser?.uid
            contacts = emptyList()
            savedContacts = emptyList()
            saving = false
            operationError = null
            searchQuery = ""
            selectedTab = 0
            clearDraft()
            loadContacts()
        }
        authListener = listener
        auth.addAuthStateListener(listener)
    }

    fun stop() {
        generation++
        authListener?.let { auth.removeAuthStateListener(it) }
        authListener = null
        contacts = emptyList()
        savedContacts = emptyList()
        accountUid = null
        loading = false
        saving = false
        operationError = null
        clearDraft()
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    fun retry() {
        if (saving) return
        generation++
        loadContacts()
    }

    private fun loadContacts() {
        val uid = accountUid
        val version = generation
        loading = uid != null
        operationError = if (uid == null) "Please sign in to view contacts." else null
        if (uid == null) return

        contactRepository.getContactsFromServer(uid) { result ->
            if (generation == version && auth.currentUser?.uid == uid) {
                loading = false
                result.onSuccess { records ->
                    savedContacts = records
                    contacts = records.map(::decode).sortedBy { it.name.lowercase() }
                    operationError = null
                }.onFailure {
                    savedContacts = emptyList()
                    contacts = emptyList()
                    savedContacts = emptyList()
                    operationError = it.toFriendlyFirestoreMessage()
                }
            }
        }
    }

    private fun decode(contact: TrustedContact): ContactEntry {
        val types = contact.categories.mapNotNull { value ->
            ContactType.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }.toMutableSet()
        types.remove(ContactType.EMERGENCY)
        if (contact.emergencyContact) types.add(ContactType.EMERGENCY)
        return ContactEntry(
            id = contact.id, name = contact.name, phone = contact.phoneNumber,
            email = contact.email, relationship = contact.relationship,
            types = types, privateNotes = contact.privateNotes,
            createdAt = contact.contactCreatedAtMillis,
            updatedAt = contact.contactUpdatedAtMillis.takeIf { it > 0 } ?: contact.contactCreatedAtMillis,
            accessGiven = contact.accessGiven, safeToStay = contact.safeToStay
        )
    }

    fun updateSearch(value: String) { searchQuery = value }
    fun selectTab(index: Int) { if (index in 0..3) selectedTab = index }
    fun visibleContacts(): List<ContactEntry> {
        val query = searchQuery.trim()
        val type = when (selectedTab) {
            1 -> ContactType.TRUSTED
            2 -> ContactType.EMERGENCY
            3 -> ContactType.SUPPORT
            else -> null
        }
        return contacts.filter {
            (type == null || type in it.types) &&
                    listOf(it.name, it.phone, it.email, it.relationship).any { field ->
                        field.contains(query, ignoreCase = true)
                    }
        }.sortedBy { it.name.lowercase() }
    }
    fun findContact(id: String): ContactEntry? = contacts.firstOrNull { it.id == id }

    fun startNewContact() { if (!saving) clearDraft() }
    fun startEditing(contact: ContactEntry) {
        if (saving) return
        original = contact
        originalRecord = savedContacts.firstOrNull { it.id == contact.id }
        draftAccessGiven = contact.accessGiven
        draftSafeToStay = contact.safeToStay
        newDocumentId = null
        draftName = contact.name
        draftPhone = contact.phone
        draftEmail = contact.email
        draftRelationship = contact.relationship
        draftTypes = contact.types
        draftPrivateNotes = contact.privateNotes
        editorError = null
        operationError = null
        hasUnsavedChanges = false
    }
    fun updateName(value: String) { if (!saving) { draftName = value; markChanged() } }
    fun updatePhone(value: String) { if (!saving) { draftPhone = value; markChanged() } }
    fun updateEmail(value: String) { if (!saving) { draftEmail = value; markChanged() } }
    fun updateRelationship(value: String) { if (!saving) { draftRelationship = value; markChanged() } }
    fun updatePrivateNotes(value: String) { if (!saving) { draftPrivateNotes = value; markChanged() } }
    fun toggleContactType(type: ContactType, checked: Boolean) {
        if (saving) return
        draftTypes = if (checked) draftTypes + type else draftTypes - type
        markChanged()
    }

    fun updateAccessGiven(value: Boolean) { if (!saving) { draftAccessGiven = value; markChanged() } }
    fun updateSafeToStay(value: Boolean) { if (!saving) { draftSafeToStay = value; markChanged() } }

    fun saveDraft(onSaved: () -> Unit) {
        if (saving) return
        val uid = accountUid
        if (uid == null || auth.currentUser?.uid != uid) {
            editorError = "Please sign in again."
            return
        }
        val name = draftName.trim()
        val phone = draftPhone.trim()
        val email = draftEmail.trim()
        val relationship = draftRelationship.trim()
        val notes = draftPrivateNotes.trim()
        val types = draftTypes.toSet()
        editorError = when {
            name.isEmpty() -> "Please enter a contact name."
            name.length > 100 -> "Keep the name within 100 characters."
            phone.isEmpty() || phone.length > 60 -> "Enter a phone number within 60 characters."
            email.length > 254 || (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) ->
                "Please enter a valid email address."
            types.isEmpty() -> "Select at least one contact type."
            relationship.length > 100 -> "Keep the relationship within 100 characters."
            notes.length > 2000 -> "Keep notes within 2000 characters."
            else -> null
        }
        if (editorError != null) return

        val baseline = originalRecord
        if (original != null && baseline == null) {
            editorError = "Reopen the contact before editing."
            return
        }
        val id = baseline?.id ?: newDocumentId ?: UUID.randomUUID().toString().also { newDocumentId = it }
        val submitted = (baseline ?: TrustedContact(id = id)).copy(
            name = name, phoneNumber = phone, email = email,
            relationship = relationship, privateNotes = notes,
            categories = types.map { it.name }.sorted(),
            emergencyContact = ContactType.EMERGENCY in types,
            accessGiven = draftAccessGiven, safeToStay = draftSafeToStay
        )
        val version = generation
        saving = true
        operationError = null
        contactRepository.saveContactFromEditor(uid, submitted, baseline) { result ->
            if (generation == version && auth.currentUser?.uid == uid) {
                saving = false
                result.onSuccess {
                    clearDraft()
                    loadContacts()
                    onSaved()
                }.onFailure {
                    editorError = (it as? IllegalStateException)?.message
                        ?: it.toFriendlyFirestoreMessage()
                }
            }
        }
    }

    fun deleteContact(id: String, onDeleted: () -> Unit) {
        if (saving) return
        val uid = accountUid
        if (uid == null || auth.currentUser?.uid != uid) {
            operationError = "Please sign in again."
            return
        }
        val version = generation
        saving = true
        operationError = null
        contactRepository.deleteContact(uid, id) { result ->
            if (generation == version && auth.currentUser?.uid == uid) {
                saving = false
                result.onSuccess {
                    contacts = contacts.filterNot { it.id == id }
                    savedContacts = savedContacts.filterNot { it.id == id }
                    onDeleted()
                }.onFailure { operationError = it.toFriendlyFirestoreMessage() }
            }
        }
    }

    fun discardDraft() { if (!saving) clearDraft() }
    private fun markChanged() {
        hasUnsavedChanges = draftName != (original?.name ?: "") ||
                draftPhone != (original?.phone ?: "") || draftEmail != (original?.email ?: "") ||
                draftRelationship != (original?.relationship ?: "") ||
                draftTypes != (original?.types ?: emptySet<ContactType>()) ||
                draftPrivateNotes != (original?.privateNotes ?: "") ||
                draftAccessGiven != (original?.accessGiven ?: false) ||
                draftSafeToStay != (original?.safeToStay ?: false)
        editorError = null
    }
    private fun clearDraft() {
        original = null
        originalRecord = null
        draftAccessGiven = false
        draftSafeToStay = false
        newDocumentId = null
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
