package com.example.androidapp.pages.contactpage
import com.example.androidapp.security.trackPrivacyInteraction
import com.example.androidapp.security.PrivacyDialogFocus

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidapp.ui.theme.AndroidAppTheme
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------
// NAVIGATION
// ---------------------------------------------------------

private enum class ContactsDestination {
    LIST,
    EDITOR,
    DETAILS
}

// ---------------------------------------------------------
// MAIN CONTACTS PAGE
// ---------------------------------------------------------

@Composable
fun ContactsPage(
    viewModel: ContactsViewModel,
    onBackClick: () -> Unit = {},
    onSupportRequestsClick: () -> Unit = {}
) {
    var destination by remember {
        mutableStateOf(ContactsDestination.LIST)
    }

    var editorReturn by remember {
        mutableStateOf(ContactsDestination.LIST)
    }

    var selectedContactId by remember {
        mutableStateOf<Long?>(null)
    }

    var editingExisting by remember {
        mutableStateOf(false)
    }

    var showDiscardDialog by remember {
        mutableStateOf(false)
    }

    var showDeleteDialog by remember {
        mutableStateOf(false)
    }

    val back: () -> Unit = {
        when (destination) {
            ContactsDestination.LIST -> onBackClick()

            ContactsDestination.EDITOR -> {
                if (viewModel.hasUnsavedChanges) {
                    showDiscardDialog = true
                } else {
                    viewModel.discardDraft()
                    destination = editorReturn
                }
            }

            ContactsDestination.DETAILS -> {
                destination = ContactsDestination.LIST
            }
        }
    }

    BackHandler(onBack = back)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (destination) {

            ContactsDestination.LIST -> {
                ContactsListScreen(
                    contacts = viewModel.visibleContacts(),
                    onSupportRequestsClick = onSupportRequestsClick,
                    searchQuery = viewModel.searchQuery,
                    selectedTab = viewModel.selectedTab,
                    onSearchChange = viewModel::updateSearch,
                    onTabChange = viewModel::selectTab,
                    onBackClick = back,
                    onAddClick = {
                        editorReturn = ContactsDestination.LIST
                        editingExisting = false
                        viewModel.startNewContact()
                        destination = ContactsDestination.EDITOR
                    },
                    onContactClick = { contact ->
                        selectedContactId = contact.id
                        destination = ContactsDestination.DETAILS
                    },
                    onEditClick = { contact ->
                        selectedContactId = contact.id
                        editorReturn = ContactsDestination.LIST
                        editingExisting = true
                        viewModel.startEditing(contact)
                        destination = ContactsDestination.EDITOR
                    },
                    onDeleteClick = { contact ->
                        selectedContactId = contact.id
                        showDeleteDialog = true
                    }
                )
            }

            ContactsDestination.EDITOR -> {
                ContactEditorScreen(
                    name = viewModel.draftName,
                    phone = viewModel.draftPhone,
                    email = viewModel.draftEmail,
                    relationship = viewModel.draftRelationship,
                    types = viewModel.draftTypes,
                    privateNotes = viewModel.draftPrivateNotes,
                    errorMessage = viewModel.editorError,
                    isEditing = editingExisting,
                    onNameChange = viewModel::updateName,
                    onPhoneChange = viewModel::updatePhone,
                    onEmailChange = viewModel::updateEmail,
                    onRelationshipChange =
                        viewModel::updateRelationship,
                    onTypeChange = viewModel::toggleContactType,
                    onNotesChange = viewModel::updatePrivateNotes,
                    onBackClick = back,
                    onSaveClick = {
                        if (viewModel.saveDraft()) {
                            destination = editorReturn
                        }
                    }
                )
            }

            ContactsDestination.DETAILS -> {
                val contact = selectedContactId?.let(
                    viewModel::findContact
                )

                ContactDetailsScreen(
                    contact = contact,
                    onBackClick = back,
                    onEditClick = {
                        if (contact != null) {
                            editorReturn = ContactsDestination.DETAILS
                            editingExisting = true
                            viewModel.startEditing(contact)
                            destination = ContactsDestination.EDITOR
                        }
                    },
                    onDeleteClick = {
                        if (contact != null) {
                            selectedContactId = contact.id
                            showDeleteDialog = true
                        }
                    }
                )
            }
        }
    }

    // -----------------------------------------------------
    // DISCARD CONFIRMATION
    // -----------------------------------------------------

    if (showDiscardDialog) {
        AlertDialog(
            modifier = Modifier.trackPrivacyInteraction(),
            onDismissRequest = {
                showDiscardDialog = false
            },
            title = {
                Text("Discard changes?")
            },
            text = {
                PrivacyDialogFocus()
                Text("Your unsaved contact changes will be lost.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.discardDraft()
                        showDiscardDialog = false
                        destination = editorReturn
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                    }
                ) {
                    Text("Keep Editing")
                }
            }
        )
    }

    // -----------------------------------------------------
    // DELETE CONFIRMATION
    // -----------------------------------------------------

    if (showDeleteDialog) {
        val contact = selectedContactId?.let(
            viewModel::findContact
        )

        AlertDialog(
            modifier = Modifier.trackPrivacyInteraction(),
            onDismissRequest = {
                showDeleteDialog = false
            },
            title = {
                Text("Delete contact?")
            },
            text = {
                PrivacyDialogFocus()
                Text(
                    "Are you sure you want to delete " +
                            "${contact?.name ?: "this contact"}?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedContactId?.let {
                            viewModel.deleteContact(it)
                        }

                        selectedContactId = null
                        showDeleteDialog = false
                        destination = ContactsDestination.LIST
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ---------------------------------------------------------
// CONTACTS LIST
// ---------------------------------------------------------

@Composable
fun ContactsListScreen(
    contacts: List<ContactEntry>,
    searchQuery: String,
    selectedTab: Int,
    onSearchChange: (String) -> Unit,
    onTabChange: (Int) -> Unit,
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onContactClick: (ContactEntry) -> Unit,
    onEditClick: (ContactEntry) -> Unit,
    onDeleteClick: (ContactEntry) -> Unit,
    onSupportRequestsClick: () -> Unit = {}
) {
    ContactsScreenLayout(
        title = "Contacts",
        onBackClick = onBackClick
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = {
                Text("Search contacts")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // Updated Material 3 tabs.
        PrimaryScrollableTabRow(
            selectedTabIndex = selectedTab,
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.background
        ) {
            listOf(
                "All",
                "Trusted",
                "Emergency",
                "Support"
            ).forEachIndexed { index, title ->

                Tab(
                    selected = selectedTab == index,
                    onClick = {
                        onTabChange(index)
                    },
                    text = {
                        Text(title)
                    }
                )
            }
        }

        if (selectedTab == 3) {
            ElevatedCard(
                onClick = onSupportRequestsClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Support Requests",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "Review and manage incoming " +
                                "support connection requests.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Text(
                        text = "View Requests",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        ContactsPrimaryButton(
            text = "+ Add Contact",
            onClick = onAddClick
        )

        if (contacts.isEmpty()) {
            ContactsEmptyCard(
                title = if (searchQuery.isNotBlank()) {
                    "No matching contacts"
                } else {
                    "No contacts found"
                },
                message = if (searchQuery.isNotBlank()) {
                    "Try searching with another keyword."
                } else {
                    "Add a contact to get started."
                }
            )
        }

        contacts.forEach { contact ->
            key(contact.id) {
                ContactCard(
                    contact = contact,
                    onOpenClick = {
                        onContactClick(contact)
                    },
                    onEditClick = {
                        onEditClick(contact)
                    },
                    onDeleteClick = {
                        onDeleteClick(contact)
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------
// CONTACT CARD
// ---------------------------------------------------------

@Composable
private fun ContactCard(
    contact: ContactEntry,
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
                    text = contact.name,
                    modifier = Modifier.weight(1f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Box {
                    TextButton(
                        onClick = {
                            expanded = true
                        }
                    ) {
                        Text("More")
                    }

                    DropdownMenu(
                        modifier = Modifier.trackPrivacyInteraction(),
                        expanded = expanded,
                        onDismissRequest = {
                            expanded = false
                        }
                    ) {
                        PrivacyDialogFocus()
                        DropdownMenuItem(
                            text = {
                                Text("View Contact")
                            },
                            onClick = {
                                expanded = false
                                onOpenClick()
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text("Edit Contact")
                            },
                            onClick = {
                                expanded = false
                                onEditClick()
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text("Delete Contact")
                            },
                            onClick = {
                                expanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }

            if (contact.relationship.isNotBlank()) {
                ContactsSupportingText(contact.relationship)
            }

            ContactsSupportingText(contact.phone)

            ContactTypeChips(
                types = contact.types
            )
        }
    }
}

// ---------------------------------------------------------
// CONTACT TYPE CHIPS
// ---------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactTypeChips(
    types: Set<ContactType>
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ContactType.entries.forEach { type ->
            if (type in types) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(type.label)
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------
// ADD / EDIT CONTACT
// ---------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactEditorScreen(
    name: String,
    phone: String,
    email: String,
    relationship: String,
    types: Set<ContactType>,
    privateNotes: String,
    errorMessage: String?,
    isEditing: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onEmailChange: (String) -> Unit,
    onRelationshipChange: (String) -> Unit,
    onTypeChange: (ContactType, Boolean) -> Unit,
    onNotesChange: (String) -> Unit,
    onBackClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    var relationshipExpanded by remember {
        mutableStateOf(false)
    }

    val relationshipOptions = listOf(
        "Family",
        "Friend",
        "Counsellor",
        "Doctor",
        "Other"
    )

    ContactsScreenLayout(
        title = if (isEditing) {
            "Edit Contact"
        } else {
            "New Contact"
        },
        onBackClick = onBackClick
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = {
                Text("Name *")
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = phone,
            onValueChange = onPhoneChange,
            label = {
                Text("Phone number *")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Phone
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = {
                Text("Email (optional)")
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        ContactsSectionTitle("Contact Type")

        ContactsSupportingText(
            "Select all that apply."
        )

        ContactType.entries.forEach { type ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onTypeChange(
                            type,
                            type !in types
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = type in types,
                    onCheckedChange = { checked ->
                        onTypeChange(type, checked)
                    }
                )

                Text(
                    text = "${type.label} Contact",
                    fontSize = 16.sp
                )
            }
        }

        ContactsSectionTitle("Relationship")

        // Updated Material 3 dropdown.
        ExposedDropdownMenuBox(
            expanded = relationshipExpanded,
            onExpandedChange = {
                relationshipExpanded = it
            }
        ) {
            OutlinedTextField(
                value = relationship,
                onValueChange = {},
                readOnly = true,
                label = {
                    Text("Relationship (optional)")
                },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(
                        expanded = relationshipExpanded
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(
                        ExposedDropdownMenuAnchorType.PrimaryNotEditable
                    )
            )

            ExposedDropdownMenu(
                modifier = Modifier.trackPrivacyInteraction(),
                expanded = relationshipExpanded,
                onDismissRequest = {
                    relationshipExpanded = false
                }
            ) {
                PrivacyDialogFocus()
                relationshipOptions.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(option)
                        },
                        onClick = {
                            onRelationshipChange(option)
                            relationshipExpanded = false
                        }
                    )
                }

                DropdownMenuItem(
                    text = {
                        Text("Clear selection")
                    },
                    onClick = {
                        onRelationshipChange("")
                        relationshipExpanded = false
                    }
                )
            }
        }

        ContactsSectionTitle("Private Notes")

        OutlinedTextField(
            value = privateNotes,
            onValueChange = onNotesChange,
            label = {
                Text("Private notes (optional)")
            },
            placeholder = {
                Text("Add information about this contact...")
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 150.dp)
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error
            )
        }

        ContactsPrimaryButton(
            text = if (isEditing) {
                "Save Changes"
            } else {
                "Save Contact"
            },
            onClick = onSaveClick
        )
    }
}

// ---------------------------------------------------------
// CONTACT DETAILS
// ---------------------------------------------------------

@Composable
fun ContactDetailsScreen(
    contact: ContactEntry?,
    onBackClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    ContactsScreenLayout(
        title = "Contact Details",
        onBackClick = onBackClick
    ) {
        if (contact == null) {
            ContactsEmptyCard(
                title = "Contact unavailable",
                message = "Return to the contacts list."
            )
        } else {
            ContactsSectionTitle(contact.name)

            ContactsSupportingText(
                "Created: ${formatContactTimestamp(contact.createdAt)}"
            )

            if (contact.updatedAt != contact.createdAt) {
                ContactsSupportingText(
                    "Updated: ${formatContactTimestamp(contact.updatedAt)}"
                )
            }

            ContactDetailField(
                label = "Phone number",
                value = contact.phone
            )

            if (contact.email.isNotBlank()) {
                ContactDetailField(
                    label = "Email",
                    value = contact.email
                )
            }

            if (contact.relationship.isNotBlank()) {
                ContactDetailField(
                    label = "Relationship",
                    value = contact.relationship
                )
            }

            ContactsSectionTitle("Contact Type")

            ContactTypeChips(
                types = contact.types
            )

            ContactsSectionTitle("Private Notes")

            if (contact.privateNotes.isBlank()) {
                ContactsSupportingText(
                    "No private notes added."
                )
            } else {
                Text(
                    text = contact.privateNotes,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            ContactsPrimaryButton(
                text = "Edit Contact",
                onClick = onEditClick
            )

            OutlinedButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Text(
                    text = "Delete Contact",
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ---------------------------------------------------------
// CONTACT DETAIL FIELD
// ---------------------------------------------------------

@Composable
private fun ContactDetailField(
    label: String,
    value: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ContactsSupportingText(label)

        Text(
            text = value,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---------------------------------------------------------
// SHARED SCREEN LAYOUT
// ---------------------------------------------------------

@Composable
private fun ContactsScreenLayout(
    title: String,
    onBackClick: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            TextButton(
                onClick = onBackClick,
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "←",
                    fontSize = 32.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = title,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            content()
        }
    }
}

// ---------------------------------------------------------
// SHARED COMPONENTS
// ---------------------------------------------------------

@Composable
private fun ContactsSectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun ContactsSupportingText(text: String) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ContactsEmptyCard(
    title: String,
    message: String
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold
            )

            ContactsSupportingText(message)
        }
    }
}

@Composable
private fun ContactsPrimaryButton(
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

// ---------------------------------------------------------
// TIMESTAMP HELPER
// ---------------------------------------------------------

private fun formatContactTimestamp(value: Long): String {
    return DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT
    ).format(Date(value))
}

// ---------------------------------------------------------
// PREVIEW DATA
// ---------------------------------------------------------

private fun previewContact(): ContactEntry {
    return ContactEntry(
        id = 1L,
        name = "Sarah",
        phone = "780-555-0100",
        email = "sarah@example.com",
        relationship = "Friend",
        types = setOf(
            ContactType.TRUSTED,
            ContactType.EMERGENCY
        ),
        privateNotes = "Someone I can contact when I need support.",
        createdAt = 1792418400000L,
        updatedAt = 1792422000000L
    )
}

// ---------------------------------------------------------
// PREVIEW 1: CONTACTS PAGE
// ---------------------------------------------------------

@Preview(
    name = "01 Contacts Page",
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
private fun ContactsPagePreview() {
    AndroidAppTheme {
        val model = remember {
            ContactsViewModel()
        }

        ContactsPage(
            viewModel = model
        )
    }
}

// ---------------------------------------------------------
// PREVIEW 2: CONTACTS LIST
// ---------------------------------------------------------

@Preview(
    name = "02 Contacts List",
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
private fun ContactsListPreview() {
    AndroidAppTheme {
        var search by remember {
            mutableStateOf("")
        }

        var tab by remember {
            mutableIntStateOf(0)
        }

        val contacts = listOf(
            previewContact(),
            previewContact().copy(
                id = 2L,
                name = "Mom",
                relationship = "Family",
                types = setOf(ContactType.EMERGENCY)
            ),
            previewContact().copy(
                id = 3L,
                name = "Counsellor",
                relationship = "Counsellor",
                types = setOf(ContactType.SUPPORT)
            )
        )

        val filtered = contacts.filter { contact ->
            val matchesTab = when (tab) {
                1 -> ContactType.TRUSTED in contact.types
                2 -> ContactType.EMERGENCY in contact.types
                3 -> ContactType.SUPPORT in contact.types
                else -> true
            }

            val matchesSearch = contact.name.contains(
                search.trim(),
                ignoreCase = true
            )

            matchesTab && matchesSearch
        }

        ContactsListScreen(
            contacts = filtered,
            searchQuery = search,
            selectedTab = tab,
            onSearchChange = {
                search = it
            },
            onTabChange = {
                tab = it
            },
            onBackClick = {},
            onAddClick = {},
            onContactClick = {},
            onEditClick = {},
            onDeleteClick = {}
        )
    }
}

// ---------------------------------------------------------
// PREVIEW 3: NEW CONTACT
// ---------------------------------------------------------

@Preview(
    name = "03 New Contact",
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
private fun NewContactPreview() {
    AndroidAppTheme {
        ContactEditorScreen(
            name = "",
            phone = "",
            email = "",
            relationship = "",
            types = emptySet(),
            privateNotes = "",
            errorMessage = null,
            isEditing = false,
            onNameChange = {},
            onPhoneChange = {},
            onEmailChange = {},
            onRelationshipChange = {},
            onTypeChange = { _, _ -> },
            onNotesChange = {},
            onBackClick = {},
            onSaveClick = {}
        )
    }
}

// ---------------------------------------------------------
// PREVIEW 4: EDIT CONTACT
// ---------------------------------------------------------

@Preview(
    name = "04 Edit Contact",
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
private fun EditContactPreview() {
    AndroidAppTheme {
        val contact = previewContact()

        ContactEditorScreen(
            name = contact.name,
            phone = contact.phone,
            email = contact.email,
            relationship = contact.relationship,
            types = contact.types,
            privateNotes = contact.privateNotes,
            errorMessage = null,
            isEditing = true,
            onNameChange = {},
            onPhoneChange = {},
            onEmailChange = {},
            onRelationshipChange = {},
            onTypeChange = { _, _ -> },
            onNotesChange = {},
            onBackClick = {},
            onSaveClick = {}
        )
    }
}

// ---------------------------------------------------------
// PREVIEW 5: CONTACT DETAILS
// ---------------------------------------------------------

@Preview(
    name = "05 Contact Details",
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
private fun ContactDetailsPreview() {
    AndroidAppTheme {
        ContactDetailsScreen(
            contact = previewContact(),
            onBackClick = {},
            onEditClick = {},
            onDeleteClick = {}
        )
    }
}

// ---------------------------------------------------------
// PREVIEW 6: EMPTY CONTACTS
// ---------------------------------------------------------

@Preview(
    name = "06 Empty Contacts",
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
private fun EmptyContactsPreview() {
    AndroidAppTheme {
        ContactsListScreen(
            contacts = emptyList(),
            searchQuery = "",
            selectedTab = 0,
            onSearchChange = {},
            onTabChange = {},

            onBackClick = {},
            onAddClick = {},
            onContactClick = {},
            onEditClick = {},
            onDeleteClick = {}
        )
    }
}
