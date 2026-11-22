package com.example.androidapp.pages.notes
import com.example.androidapp.security.trackPrivacyInteraction
import android.widget.Toast
import com.example.androidapp.security.PrivacyDialogFocus
import com.example.androidapp.security.LocalPrivacyExternalInteraction

import com.example.androidapp.utils.NoteTimestamp
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import coil.compose.AsyncImage
import com.example.androidapp.ui.theme.AndroidAppTheme
import java.io.File

// ---------------------------------------------------------
// NAVIGATION
// ---------------------------------------------------------

private enum class NotesDestination {
    LIST,
    EDITOR,
    DETAILS
}

@Composable
fun NotesPage(
    viewModel: NotesViewModel,
    onBackClick: () -> Unit = {}
) {
    var destination by remember {
        mutableStateOf(NotesDestination.LIST)
    }

    var editorReturn by remember {
        mutableStateOf(NotesDestination.LIST)
    }

    var selectedNoteId by remember {
        mutableStateOf<Long?>(null)
    }

    var editingExisting by remember {
        mutableStateOf(false)
    }

    var showDiscardDialog by remember {
        mutableStateOf(false)
    }
    var noteToDelete by remember {
        mutableStateOf<NoteEntry?>(null)
    }

    val context = LocalContext.current
    val onExternalInteractionChanged = LocalPrivacyExternalInteraction.current

    LaunchedEffect(Unit) {
        viewModel.initContext(context)
    }

    val attachmentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        onExternalInteractionChanged(false)
        viewModel.addAttachments(
            uris.map { attachmentFromUri(context, it) }
        )
    }

    val back: () -> Unit = {
        when (destination) {
            NotesDestination.LIST -> onBackClick()

            NotesDestination.EDITOR -> {
                if (viewModel.hasUnsavedChanges) {
                    showDiscardDialog = true
                } else {
                    viewModel.discardDraft()
                    destination = editorReturn
                }
            }

            NotesDestination.DETAILS -> {
                destination = NotesDestination.LIST
            }
        }
    }

    BackHandler(onBack = back)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (destination) {
            NotesDestination.LIST -> {
                NotesListScreen(
                    notes = viewModel.notes,
                    searchQuery = viewModel.searchQuery,
                    onSearchChange = viewModel::updateSearch,
                    onBackClick = back,
                    onNewNoteClick = {
                        editorReturn = NotesDestination.LIST
                        editingExisting = false
                        viewModel.startNewNote()
                        destination = NotesDestination.EDITOR
                    },
                    onDocumentChecklistClick = {
                        editorReturn = NotesDestination.LIST
                        editingExisting = viewModel.notes.any { it.title.contains("Document Checklist", ignoreCase = true) }
                        viewModel.startDocumentChecklist()
                        destination = NotesDestination.EDITOR
                    },
                    onSafetyPlanClick = {
                        editorReturn = NotesDestination.LIST
                        editingExisting = viewModel.notes.any { it.title.contains("Safety Plan", ignoreCase = true) }
                        viewModel.startSafetyPlan()
                        destination = NotesDestination.EDITOR
                    },
                    onNoteClick = { note ->
                        selectedNoteId = note.id
                        destination = NotesDestination.DETAILS
                    },
                    onEditClick = { note ->
                        selectedNoteId = note.id
                        editorReturn = NotesDestination.LIST
                        editingExisting = true
                        viewModel.startEditing(note)
                        destination = NotesDestination.EDITOR
                    },
                    onDeleteClick = { note ->
                        noteToDelete = note
                    }
                )
            }

            NotesDestination.EDITOR -> {
                NoteEditorScreen(
                    title = viewModel.draftTitle,
                    content = viewModel.draftContent,
                    attachments = viewModel.draftAttachments,
                    errorMessage = viewModel.editorError,
                    onTitleChange = viewModel::updateTitle,
                    onContentChange = viewModel::updateContent,
                    onRemoveAttachment = viewModel::removeAttachment,
                    onAddAttachmentsClick = {
                        onExternalInteractionChanged(true)
                        try {
                            attachmentPicker.launch("*/*")
                        } catch (exception: RuntimeException) {
                            onExternalInteractionChanged(false)
                            Toast.makeText(
                                context,
                                "Unable to open the attachment picker. Please try again.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    onBackClick = back,
                    onSaveClick = {
                        viewModel.saveDraft(context) { success ->
                            if (success) {
                                destination = editorReturn
                            }
                        }
                    },
                    isEditing = editingExisting
                )
            }

            NotesDestination.DETAILS -> {
                val note = selectedNoteId?.let(viewModel::findNote)

                NoteDetailsScreen(
                    note = note,
                    onBackClick = back,
                    onChecklistToggle = { lineIndex ->
                        selectedNoteId?.let { id ->
                            viewModel.toggleNoteChecklistItem(id, lineIndex)
                        }
                    },
                    onEditClick = {
                        if (note != null) {
                            editorReturn = NotesDestination.DETAILS
                            editingExisting = true
                            viewModel.startEditing(note)
                            destination = NotesDestination.EDITOR
                        }
                    },
                    onDeleteClick = {
                        if (note != null) noteToDelete = note
                    }
                )
            }
        }
    }

    if (showDiscardDialog) {
        DiscardChangesDialog(
            onKeepEditing = {
                showDiscardDialog = false
            },
            onDiscard = {
                viewModel.discardDraft()
                showDiscardDialog = false
                destination = editorReturn
            }
        )
    }


    noteToDelete?.let { note ->
        AlertDialog(
            modifier = Modifier.trackPrivacyInteraction(),
            onDismissRequest = {
                noteToDelete = null
            },
            title = {
                Text("Delete note?")
            },
            text = {
                PrivacyDialogFocus()
                Text(
                    "Are you sure you want to delete " +
                            "\"${note.title}\"? This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteNote(note.id)
                        selectedNoteId = null
                        noteToDelete = null
                        destination = NotesDestination.LIST
                    }
                ) {
                    Text(
                        "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        noteToDelete = null
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }

}

// ---------------------------------------------------------
// NOTES LIST SCREEN
// ---------------------------------------------------------

@Composable
fun NotesListScreen(
    notes: List<NoteEntry>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onNewNoteClick: () -> Unit,
    onDocumentChecklistClick: () -> Unit = {},
    onSafetyPlanClick: () -> Unit = {},
    onNoteClick: (NoteEntry) -> Unit,
    onEditClick: (NoteEntry) -> Unit,
    onDeleteClick: (NoteEntry) -> Unit = {}
) {
    val query = searchQuery.trim()

    val visibleNotes = notes.filter { note ->
        note.title.contains(query, ignoreCase = true) ||
                note.content.contains(query, ignoreCase = true)
    }.sortedByDescending { it.updatedAt }

    NotesScreenLayout(
        title = "Notes",
        onBackClick = onBackClick
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Search notes") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        NotesPrimaryButton(
            text = "+ New Note",
            onClick = onNewNoteClick
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onDocumentChecklistClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Document Checklist", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = onSafetyPlanClick,
                modifier = Modifier.weight(1f)
            ) {
                Text("Safety Plan", fontSize = 13.sp)
            }
        }

        if (visibleNotes.isEmpty()) {
            EmptyNotesCard(
                title = if (query.isNotEmpty()) {
                    "No matching notes"
                } else {
                    "No notes yet"
                },
                message = if (query.isNotEmpty()) {
                    "Try searching with a different keyword."
                } else {
                    "Create your first note to get started."
                }
            )
        }

        visibleNotes.forEach { note ->
            key(note.id) {
                NoteCard(
                    note = note,
                    onOpenClick = { onNoteClick(note) },
                    onEditClick = { onEditClick(note) },
                    onDeleteClick = { onDeleteClick(note) }
                )
            }
        }
    }
}

// ---------------------------------------------------------
// NOTE CARD
// ---------------------------------------------------------

@Composable
private fun NoteCard(
    note: NoteEntry,
    onOpenClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = note.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Box {
                    IconButton(
                        onClick = { expanded = true }
                    ) {
                        Text(
                            text = "⋮",
                            fontSize = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    DropdownMenu(
                        modifier = Modifier.trackPrivacyInteraction(),
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        PrivacyDialogFocus()
                        DropdownMenuItem(
                            text = { Text("Read Note") },
                            onClick = {
                                expanded = false
                                onOpenClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Edit Note") },
                            onClick = {
                                expanded = false
                                onEditClick()
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Delete Note",
                                    color = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                expanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            Text(
                text = note.content,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                NotesSupportingText(formatTimestamp(note.updatedAt))

                if (note.attachments.isNotEmpty()) {
                    NotesSupportingText("${note.attachments.size} attachment(s)")
                }
            }
        }
    }
}

// ---------------------------------------------------------
// NOTE EDITOR SCREEN
// ---------------------------------------------------------

@Composable
fun NoteEditorScreen(
    title: String,
    content: String,
    attachments: List<NoteAttachment>,
    errorMessage: String?,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onAddAttachmentsClick: () -> Unit,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit,
    isEditing: Boolean
) {
    var selectedAttachment by remember {
        mutableStateOf<NoteAttachment?>(null)
    }

    NotesScreenLayout(
        title = if (isEditing) "Edit Note" else "New Note",
        onBackClick = onBackClick
    ) {
        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = content,
            onValueChange = onContentChange,
            label = { Text("Note content") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
        )

        OutlinedButton(
            onClick = {
                val newItem = if (content.isBlank()) "[ ] " else "\n[ ] "
                onContentChange(content + newItem)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Add Checklist Item")
        }

        ChecklistEditor(
            content = content,
            onContentChange = onContentChange
        )

        NotesSectionTitle("Attachments (optional)")

        NotesSecondaryButton(
            text = "+ Add Attachments",
            onClick = onAddAttachmentsClick
        )

        if (attachments.isEmpty()) {
            NotesSupportingText("No attachments added.")
        }

        attachments.forEach { attachment ->
            AttachmentRow(
                attachment = attachment,
                onOpen = {
                    selectedAttachment = attachment
                },
                onRemove = {
                    onRemoveAttachment(attachment.uri)
                }
            )
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error
            )
        }

        NotesPrimaryButton(
            text = if (isEditing) "Save Changes" else "Save Note",
            onClick = onSaveClick
        )
    }

    selectedAttachment?.let { attachment ->
        AttachmentDetailsDialog(
            attachment = attachment,
            onDismiss = {
                selectedAttachment = null
            }
        )
    }
}

// ---------------------------------------------------------
// NOTE DETAILS SCREEN
// ---------------------------------------------------------

@Composable
fun NoteDetailsScreen(
    note: NoteEntry?,
    onBackClick: () -> Unit,
    onChecklistToggle: (Int) -> Unit = {},
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit = {}
) {
    var selectedAttachment by remember {
        mutableStateOf<NoteAttachment?>(null)
    }

    NotesScreenLayout(
        title = "Note Details",
        onBackClick = onBackClick
    ) {
        if (note == null) {
            EmptyNotesCard(
                title = "Note unavailable",
                message = "Return to the notes list."
            )
        } else {
            NotesSectionTitle(note.title)

            NotesSupportingText(
                "Created: ${formatTimestamp(note.createdAt)}"
            )

            if (note.updatedAt != note.createdAt) {
                NotesSupportingText(
                    "Updated: ${formatTimestamp(note.updatedAt)}"
                )
            }

            Text(
                text = note.content,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            ChecklistEditor(
                content = note.content,
                onContentChange = { updatedContent ->
                    // Find the changed line index
                    val oldLines = note.content.lines()
                    val newLines = updatedContent.lines()
                    for (i in oldLines.indices) {
                        if (i < newLines.size && oldLines[i] != newLines[i]) {
                            onChecklistToggle(i)
                            break
                        }
                    }
                }
            )

            NotesSectionTitle("Attachments")

            if (note.attachments.isEmpty()) {
                NotesSupportingText(
                    "No attachments on this note."
                )
            }

            note.attachments.forEach { attachment ->
                AttachmentRow(
                    attachment = attachment,
                    onOpen = {
                        selectedAttachment = attachment
                    }
                )
            }

            NotesPrimaryButton(
                text = "Edit Note",
                onClick = onEditClick
            )

            OutlinedButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(
                    text = "Delete Note",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }

    selectedAttachment?.let { attachment ->
        AttachmentDetailsDialog(
            attachment = attachment,
            onDismiss = {
                selectedAttachment = null
            }
        )
    }
}

// ---------------------------------------------------------
// IMAGE ATTACHMENT HELPER
// ---------------------------------------------------------

private fun isImageAttachment(attachment: NoteAttachment): Boolean {
    val name = attachment.name.lowercase()
    val uri = attachment.uri.lowercase()
    val onlineUrl = (attachment.onlineUrl ?: "").lowercase()

    val extensions = listOf(".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp", ".heic")
    val hasExt = extensions.any { ext -> name.contains(ext) || uri.contains(ext) || onlineUrl.contains(ext) }

    return hasExt || uri.contains("image") || uri.contains("photo") || name.contains("image") || name.contains("photo") || name.contains("img")
}

// ---------------------------------------------------------
// ATTACHMENT COMPONENT
// ---------------------------------------------------------

@Composable
private fun AttachmentRow(
    attachment: NoteAttachment,
    onOpen: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = attachment.name,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )

            if (isImageAttachment(attachment)) {
                val file = remember(attachment.uri) {
                    try {
                        val parsed = Uri.parse(attachment.uri)
                        if (parsed.scheme == "file") {
                            File(parsed.path ?: "")
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }

                if (file != null && file.exists()) {
                    val bitmap = remember(file.absolutePath) {
                        try {
                            BitmapFactory.decodeFile(file.absolutePath)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = attachment.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        val model = if (!attachment.onlineUrl.isNullOrBlank()) attachment.onlineUrl else Uri.parse(attachment.uri)
                        AsyncImage(
                            model = model,
                            contentDescription = attachment.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                } else {
                    val model = if (!attachment.onlineUrl.isNullOrBlank()) attachment.onlineUrl else Uri.parse(attachment.uri)
                    AsyncImage(
                        model = model,
                        contentDescription = attachment.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TextButton(onClick = onOpen) {
                    Text("View File")
                }

                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text("Remove")
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// ATTACHMENT DETAILS DIALOG
// ---------------------------------------------------------

@Composable
private fun AttachmentDetailsDialog(
    attachment: NoteAttachment,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val onExternalInteractionChanged = LocalPrivacyExternalInteraction.current

    val attachmentViewer = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        onExternalInteractionChanged(false)
    }

    var error by remember(attachment.uri) {
        mutableStateOf<String?>(null)
    }

    AlertDialog(
        modifier = Modifier.trackPrivacyInteraction(),
        onDismissRequest = onDismiss,
        title = { Text("Attachment") },
        text = {
            PrivacyDialogFocus()
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(attachment.name)

                if (isImageAttachment(attachment)) {
                    val file = remember(attachment.uri) {
                        try {
                            val parsed = Uri.parse(attachment.uri)
                            if (parsed.scheme == "file") {
                                File(parsed.path ?: "")
                            } else null
                        } catch (_: Exception) {
                            null
                        }
                    }

                    if (file != null && file.exists()) {
                        val bitmap = remember(file.absolutePath) {
                            try {
                                BitmapFactory.decodeFile(file.absolutePath)
                            } catch (_: Exception) {
                                null
                            }
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = attachment.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            val model = if (!attachment.onlineUrl.isNullOrBlank()) attachment.onlineUrl else Uri.parse(attachment.uri)
                            AsyncImage(
                                model = model,
                                contentDescription = attachment.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else {
                        val model = if (!attachment.onlineUrl.isNullOrBlank()) attachment.onlineUrl else Uri.parse(attachment.uri)
                        AsyncImage(
                            model = model,
                            contentDescription = attachment.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                } else {
                    Text("Open this file with a compatible app.")
                }

                error?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onExternalInteractionChanged(true)
                    error = openAttachment(context, attachment) { intent ->
                        attachmentViewer.launch(intent)
                    }
                    if (error != null) {
                        onExternalInteractionChanged(false)
                    }
                }
            ) {
                Text("Open File")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

// ---------------------------------------------------------
// DISCARD CHANGES
// ---------------------------------------------------------

@Composable
private fun DiscardChangesDialog(
    onKeepEditing: () -> Unit,
    onDiscard: () -> Unit
) {
    AlertDialog(
        modifier = Modifier.trackPrivacyInteraction(),
        onDismissRequest = onKeepEditing,
        title = { Text("Discard changes?") },
        text = {
            PrivacyDialogFocus()
            Text("Your unsaved changes will be lost.")
        },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard")
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text("Keep editing", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

// ---------------------------------------------------------
// SHARED TEXT AND BUTTON COMPONENTS
// ---------------------------------------------------------

@Composable
private fun NotesSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun NotesSupportingText(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EmptyNotesCard(
    title: String,
    message: String
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold
            )
            NotesSupportingText(message)
        }
    }
}

@Composable
private fun NotesPrimaryButton(
    text: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NotesSecondaryButton(
    text: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Text(
            text = text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatTimestamp(value: Long): String {
    return NoteTimestamp.format(value)
}

private fun toggleChecklistItemText(content: String, lineIndex: Int): String {
    val lines = content.lines().toMutableList()
    if (lineIndex in lines.indices) {
        val line = lines[lineIndex]
        lines[lineIndex] = when {
            line.startsWith("[ ] ") -> line.replaceFirst("[ ] ", "[x] ")
            line.startsWith("[x] ") || line.startsWith("[X] ") -> line.replaceFirst(Regex("\\[[xX]] "), "[ ] ")
            else -> line
        }
    }
    return lines.joinToString("\n")
}

@Composable
private fun ChecklistEditor(
    content: String,
    onContentChange: (String) -> Unit
) {
    val lines = content.lines()
    val hasChecklist = lines.any { it.startsWith("[ ] ") || it.startsWith("[x] ") || it.startsWith("[X] ") }

    if (hasChecklist) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Interactive Checklist",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            lines.forEachIndexed { index, line ->
                when {
                    line.startsWith("[ ] ") || line.startsWith("[x] ") || line.startsWith("[X] ") -> {
                        val isChecked = line.startsWith("[x] ") || line.startsWith("[X] ")
                        val text = line.substring(4)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onContentChange(toggleChecklistItemText(content, index))
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = {
                                    onContentChange(toggleChecklistItemText(content, index))
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = text,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    line.isNotBlank() -> {
                        Text(
                            text = line,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// SCREEN LAYOUT
// ---------------------------------------------------------

@Composable
private fun NotesScreenLayout(
    title: String,
    onBackClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onBackClick,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "←",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        content()
    }
}

// ---------------------------------------------------------
// URI TO ATTACHMENT
// ---------------------------------------------------------

private fun attachmentFromUri(
    context: Context,
    uri: Uri
): NoteAttachment {
    val name = try {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    } catch (_: Exception) {
        null
    } ?: "Attachment"

    // Copy to internal filesDir so URI read permission never expires and previews load 100% reliably
    val localUriString = try {
        val destFile = File(context.filesDir, "attach_${System.currentTimeMillis()}_$name")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        Uri.fromFile(destFile).toString()
    } catch (_: Exception) {
        uri.toString()
    }

    return NoteAttachment(
        uri = localUriString,
        name = name
    )
}

// ---------------------------------------------------------
// OPEN ATTACHMENT
// ---------------------------------------------------------

private fun openAttachment(
    context: Context,
    attachment: NoteAttachment,
    launch: (Intent) -> Unit
): String? {
    return try {
        val uri = Uri.parse(attachment.uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        launch(intent)
        null
    } catch (_: Exception) {
        "This file could not be opened."
    }
}

