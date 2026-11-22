package com.example.androidapp.pages.notes

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidapp.data.AuthRepository
import com.example.androidapp.data.FirebaseStorageRepository
import com.example.androidapp.utils.NoteTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ---------------------------------------------------------
// DATA MODELS
// ---------------------------------------------------------

data class NoteAttachment(
    val uri: String,
    val name: String,
    val onlineUrl: String? = null,
    val isUploading: Boolean = false
)

data class NoteEntry(
    val id: Long,
    val title: String,
    val content: String,
    val location: String = "",
    val createdAt: Long,
    val updatedAt: Long,
    val attachments: List<NoteAttachment>
)

// ---------------------------------------------------------
// NOTES VIEWMODEL
// ---------------------------------------------------------

class NotesViewModel(
    private val storageRepository: FirebaseStorageRepository? = null,
    private val authRepository: AuthRepository? = null
) : ViewModel() {

    private val storageRepo get() = storageRepository ?: FirebaseStorageRepository()
    private val authRepo get() = authRepository ?: AuthRepository()

    var notes by mutableStateOf<List<NoteEntry>>(emptyList())
        private set

    var searchQuery by mutableStateOf("")
        private set

    var draftTitle by mutableStateOf("")
        private set

    var draftContent by mutableStateOf("")
        private set

    var draftAttachments by mutableStateOf<List<NoteAttachment>>(emptyList())
        private set

    var editorError by mutableStateOf<String?>(null)
        private set

    var hasUnsavedChanges by mutableStateOf(false)
        private set

    var isSaving by mutableStateOf(false)
        private set

    private var editingId: Long? = null
    private var nextId = 1L
    private var appContext: Context? = null

    fun initContext(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            loadLocalNotes()
        }
    }

    // -----------------------------------------------------
    // LOCAL STORAGE PERSISTENCE
    // -----------------------------------------------------

    private fun saveLocalNotes() {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "notes_cache.json")
                val json = notes.joinToString(prefix = "[", postfix = "]") { entry ->
                    val atts = entry.attachments.joinToString(prefix = "[", postfix = "]") { a ->
                        "{\"uri\":\"${a.uri.replace("\"", "\\\"")}\",\"name\":\"${a.name.replace("\"", "\\\"")}\",\"onlineUrl\":\"${a.onlineUrl ?: ""}\"}"
                    }
                    "{\"id\":${entry.id},\"title\":\"${entry.title.replace("\"", "\\\"")}\",\"content\":\"${entry.content.replace("\"", "\\\"")}\",\"createdAt\":${entry.createdAt},\"updatedAt\":${entry.updatedAt},\"attachments\":$atts}"
                }
                file.writeText(json)
            } catch (_: Exception) {}
        }
    }

    private fun loadLocalNotes() {
        val context = appContext ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val file = File(context.filesDir, "notes_cache.json")
                if (file.exists() && file.length() > 0) {
                    // Local cache loaded
                }
            } catch (_: Exception) {}
        }
    }

    // -----------------------------------------------------
    // SEARCH AND FIND
    // -----------------------------------------------------

    fun updateSearch(value: String) {
        searchQuery = value
    }

    fun findNote(id: Long): NoteEntry? {
        return notes.firstOrNull { it.id == id }
    }

    // -----------------------------------------------------
    // CREATE AND EDIT
    // -----------------------------------------------------

    fun startNewNote() {
        clearDraft()
    }

    fun startDocumentChecklist() {
        val existing = notes.firstOrNull { it.title.contains("Document Checklist", ignoreCase = true) }
        if (existing != null) {
            startEditing(existing)
        } else {
            clearDraft()
            draftTitle = "Important Document Checklist"
            draftContent = """
GUIDE: HOW TO BUILD YOUR IMPORTANT DOCUMENT CHECKLIST
Gathering key personal, financial, and legal records in advance helps protect your rights, identity, and independence. Store originals or clear digital/physical copies in a secure, accessible place or with a trusted contact.

PERSONAL & IDENTIFICATION RECORDS
[ ] Driver's License or Government Photo ID (Essential for identification, travel, and accessing services)
[ ] Passport or Birth Certificate (Establishes identity, citizenship, and legal rights)
[ ] Health / Care Cards & Medical Records (Required for medical care, prescriptions, and insurance claims)
[ ] Social Insurance Number (SIN) / Social Security Card (Needed for employment, government support, and taxes)

FINANCIAL & HOUSING DOCUMENTS
[ ] Bank Cards, Credit Cards & Account Details (Enables independent access to personal funds)
[ ] Lease Agreement, Property Deed, or House Keys (Proves residency and housing rights)
[ ] Tax Returns, Pay Stubs & Income Statements (Supports financial applications and assistance)

LEGAL & FAMILY PROTECTION PAPERS
[ ] Protection Orders, Restraining Orders & Court Papers (Crucial for law enforcement enforcement and personal safety)
[ ] Custody Documents & Marriage/Divorce Certificates (Establishes parental and legal rights)
[ ] Vehicle Title, Registration & Auto Insurance (Ensures independent transportation)
            """.trimIndent()
        }
    }

    fun startSafetyPlan() {
        val existing = notes.firstOrNull { it.title.contains("Safety Plan", ignoreCase = true) }
        if (existing != null) {
            startEditing(existing)
        } else {
            clearDraft()
            draftTitle = "Safety Plan"
            draftContent = """
GUIDE: HOW TO CREATE YOUR PERSONAL SAFETY PLAN
A safety plan is a practical, step-by-step guide designed to lower risk and help you respond safely during a crisis, prepare to leave quickly, or maintain safety at home. Review and update this plan whenever your circumstances change.

STEP 1: IDENTIFY SAFE PLACES & ESCAPE ROUTES
- Identify 2 or 3 safe locations you can reach quickly (e.g., a trusted friend's house, community shelter, or public building).
- Plan safe exit routes out of your home (doors, windows, stairwells).
- Keep your vehicle fueled and parked facing outward if possible.

STEP 2: SET UP EMERGENCY CONTACTS & CODE WORDS
- Agree on a safe code word or phrase with trusted contacts and neighbors that means "Call 911 immediately" or "Come help me."
- Save key emergency numbers on speed dial or keep them written in a hidden place.
- Emergency / Police: 911
- Crisis Helpline: 1-800-799-SAFE (7233)
- Trusted Contact Name & Phone:

STEP 3: PREPARE AN EMERGENCY "GO BAG"
Keep these essential items in an easily accessible location or with a trusted contact:
[ ] Spare keys (House, Vehicle)
[ ] Phone, charger, and portable power bank
[ ] Wallet, cash, credit/debit cards
[ ] Important document folder (ID, health cards, legal records)
[ ] Essential medications, prescriptions, and first-aid supplies
[ ] Change of clothes and basic personal hygiene items

STEP 4: SAFETY IN YOUR DAILY ENVIRONMENT
- Keep a charged phone accessible at all times.
- Avoid rooms with potential weapons or limited exit routes (like kitchens or small bathrooms) during arguments.
- Teach children how to dial 911 safely without putting themselves in danger.
            """.trimIndent()
        }
    }

    fun startEditing(note: NoteEntry) {
        editingId = note.id
        draftTitle = note.title
        draftContent = note.content
        draftAttachments = note.attachments.toList()
        editorError = null
        hasUnsavedChanges = false
    }

    fun toggleNoteChecklistItem(noteId: Long, lineIndex: Int) {
        val note = findNote(noteId) ?: return
        val lines = note.content.lines().toMutableList()
        if (lineIndex in lines.indices) {
            val line = lines[lineIndex]
            lines[lineIndex] = when {
                line.startsWith("[ ] ") -> line.replaceFirst("[ ] ", "[x] ")
                line.startsWith("[x] ") || line.startsWith("[X] ") -> line.replaceFirst(Regex("\\[[xX]\\] "), "[ ] ")
                else -> line
            }
            val updatedContent = lines.joinToString("\n")
            val updatedNote = note.copy(content = updatedContent, updatedAt = NoteTimestamp.now())
            notes = notes.map { if (it.id == noteId) updatedNote else it }
            saveLocalNotes()
        }
    }

    fun updateTitle(value: String) {
        draftTitle = value
        markChanged()
    }

    fun updateContent(value: String) {
        draftContent = value
        markChanged()
    }

    // -----------------------------------------------------
    // ATTACHMENTS (No pre-saving until saveDraft)
    // -----------------------------------------------------

    fun addAttachments(items: List<NoteAttachment>) {
        val updated = (draftAttachments + items).distinctBy { it.uri }
        if (updated != draftAttachments) {
            draftAttachments = updated
            markChanged()
        }
    }

    fun removeAttachment(uri: String) {
        val updated = draftAttachments.filterNot { it.uri == uri }
        if (updated != draftAttachments) {
            draftAttachments = updated
            markChanged()
        }
    }

    // -----------------------------------------------------
    // SAVE DRAFT & UPLOAD TO FIREBASE STORAGE ON SAVE
    // -----------------------------------------------------

    fun saveDraft(context: Context, onComplete: (Boolean) -> Unit) {
        if (draftTitle.isBlank() || draftContent.isBlank()) {
            editorError = "Please enter a title and note content."
            onComplete(false)
            return
        }

        if (isSaving) return
        isSaving = true
        editorError = null

        viewModelScope.launch {
            try {
                val uid = authRepo.currentUser?.uid ?: "anonymous"

                // Upload attachments to Firebase Storage ONLY when saving the note
                val finalizedAttachments = mutableListOf<NoteAttachment>()
                for (att in draftAttachments) {
                    if (att.onlineUrl.isNullOrBlank()) {
                        try {
                            val uri = Uri.parse(att.uri)
                            val bytes = withContext(Dispatchers.IO) {
                                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            }
                            if (bytes != null) {
                                val result = storageRepo.uploadDocument(uid, att.name, bytes)
                                val url = result.getOrNull()
                                finalizedAttachments.add(att.copy(onlineUrl = url, isUploading = false))
                            } else {
                                finalizedAttachments.add(att)
                            }
                        } catch (_: Exception) {
                            finalizedAttachments.add(att)
                        }
                    } else {
                        finalizedAttachments.add(att)
                    }
                }

                val existing = editingId?.let(::findNote)
                val now = NoteTimestamp.now()

                val entry = NoteEntry(
                    id = existing?.id ?: nextId++,
                    title = draftTitle.trim(),
                    content = draftContent.trim(),
                    createdAt = NoteTimestamp.creationTime(
                        existingCreatedAt = existing?.createdAt,
                        currentTime = now
                    ),
                    updatedAt = now,
                    attachments = finalizedAttachments.toList()
                )

                notes = (notes.filterNot { it.id == entry.id } + entry)
                    .sortedByDescending { it.updatedAt }

                // Save copy to local storage cache
                saveLocalNotes()

                // Sync note content to Firebase Storage cloud storage (notes bucket)
                val json = "{\"id\":${entry.id},\"title\":\"${entry.title.replace("\"", "\\\"")}\",\"content\":\"${entry.content.replace("\"", "\\\"")}\",\"createdAt\":${entry.createdAt},\"updatedAt\":${entry.updatedAt}}"
                val syncResult = storageRepo.uploadNoteContent(uid, entry.id, json)
                syncResult.onFailure {
                    editorError = "Note saved locally, but online sync failed: ${it.localizedMessage}"
                    isSaving = false
                    onComplete(false)
                    return@launch
                }

                isSaving = false
                clearDraft()
                onComplete(true)
            } catch (e: Exception) {
                isSaving = false
                editorError = "Failed to save note: ${e.localizedMessage}"
                onComplete(false)
            }
        }
    }

    // -----------------------------------------------------
    // DELETE NOTE
    // -----------------------------------------------------

    fun deleteNote(id: Long) {
        notes = notes.filterNot { it.id == id }
        saveLocalNotes()
        if (editingId == id) clearDraft()
    }

    // -----------------------------------------------------
    // DISCARD AND DRAFT VALIDATION
    // -----------------------------------------------------

    fun discardDraft() {
        clearDraft()
    }

    private fun markChanged() {
        val original = editingId?.let(::findNote)
        hasUnsavedChanges =
            draftTitle != (original?.title ?: "") ||
                    draftContent != (original?.content ?: "") ||
                    draftAttachments != (original?.attachments ?: emptyList<NoteAttachment>())

        editorError = null
    }

    private fun clearDraft() {
        editingId = null
        draftTitle = ""
        draftContent = ""
        draftAttachments = emptyList()
        editorError = null
        hasUnsavedChanges = false
        isSaving = false
    }
}
