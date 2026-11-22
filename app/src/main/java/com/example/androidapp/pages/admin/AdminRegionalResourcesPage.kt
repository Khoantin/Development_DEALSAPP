package com.example.androidapp.pages.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidapp.data.AdminResourceOptions

@Composable
fun AdminRegionalResourcesPage(
    viewModel: AdminRegionalResourcesViewModel,
    onBack: () -> Unit
) {
    val state = viewModel.state
    val draft = state.draft

    var showDiscardDialog by remember {
        mutableStateOf(false)
    }

    val goBack: () -> Unit = {
        if (!state.isSaving) {
            when {
                state.hasUnsavedChanges -> showDiscardDialog = true
                draft != null -> viewModel.closeEditor()
                else -> onBack()
            }
        }
    }

    // Block ordinary Back while saving. Global Quick Exit remains available.
    BackHandler { goBack() }

    LaunchedEffect(draft?.id) {
        showDiscardDialog = false
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved edits will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        viewModel.closeEditor()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDiscardDialog = false }
                ) {
                    Text("Keep editing")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        TextButton(
            enabled = !state.isSaving,
            onClick = goBack
        ) {
            Text(if (draft == null) "Back" else "Back to resources")
        }

        Text(
            text = "Manage regional resources",
            style = MaterialTheme.typography.headlineSmall
        )

        state.message?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        if (draft == null) {
            AdminResourceDropdown(
                label = "Region",
                selected = state.regionId,
                options = AdminResourceOptions.regions,
                enabled = !state.isLoading,
                onSelected = viewModel::selectRegion
            )

            Text(
                "Choose a published resource to edit. " +
                        "Saved changes appear on the user Resources page."
            )

            Button(
                enabled = !state.isLoading,
                onClick = viewModel::refresh
            ) {
                Text("Refresh")
            }

            if (state.isLoading) {
                CircularProgressIndicator()
            } else if (state.error == null && state.resources.isEmpty()) {
                Text("No published resources found in this region.")
            }

            state.resources.forEach { resource ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            resource.name.ifBlank { "Unnamed resource" },
                            style = MaterialTheme.typography.titleMedium
                        )

                        if (resource.isDemo) {
                            Text("DEMO — fictional service")
                        }

                        Text(resource.summary)

                        TextButton(
                            onClick = { viewModel.edit(resource) }
                        ) {
                            Text("Edit resource")
                        }
                    }
                }
            }
        } else {
            Text(
                text = AdminResourceOptions.regions[state.regionId].orEmpty(),
                style = MaterialTheme.typography.titleMedium
            )

            if (draft.isDemo) {
                Text("DEMO — fictional service")
            }

            Text("Fields marked * are required.")

            ResourceEditorField(
                "Name *", draft.name, !state.isSaving
            ) {
                viewModel.updateDraft(draft.copy(name = it))
            }

            AdminResourceDropdown(
                label = "Category *",
                selected = draft.need,
                options = AdminResourceOptions.needs,
                enabled = !state.isSaving,
                onSelected = {
                    viewModel.updateDraft(draft.copy(need = it))
                }
            )

            ResourceEditorField(
                "Description *",
                draft.summary,
                !state.isSaving,
                multiline = true
            ) {
                viewModel.updateDraft(draft.copy(summary = it))
            }

            ResourceEditorField(
                "What to expect *",
                draft.exampleProcess,
                !state.isSaving,
                multiline = true
            ) {
                viewModel.updateDraft(draft.copy(exampleProcess = it))
            }

            Text("Contact methods *")

            AdminResourceOptions.contactMethods.forEach { (id, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = id in draft.contactMethods,
                        enabled = !state.isSaving,
                        onCheckedChange = { checked ->
                            val updated = if (checked) {
                                (draft.contactMethods + id).distinct()
                            } else {
                                draft.contactMethods - id
                            }

                            viewModel.updateDraft(
                                draft.copy(contactMethods = updated)
                            )
                        }
                    )
                    Text(label)
                }
            }

            ResourceEditorField(
                "Website (https://)",
                draft.websiteUrl,
                !state.isSaving
            ) {
                viewModel.updateDraft(draft.copy(websiteUrl = it))
            }

            ResourceEditorField(
                "Phone number", draft.phoneNumber, !state.isSaving
            ) {
                viewModel.updateDraft(draft.copy(phoneNumber = it))
            }

            ResourceEditorField(
                "Hours / availability", draft.hoursNote, !state.isSaving
            ) {
                viewModel.updateDraft(draft.copy(hoursNote = it))
            }

            ResourceEditorField(
                "Last reviewed", draft.lastReviewed, !state.isSaving
            ) {
                viewModel.updateDraft(draft.copy(lastReviewed = it))
            }

            Text(
                "Only change “Last reviewed” when the resource information " +
                        "has actually been checked.",
                style = MaterialTheme.typography.bodySmall
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSaving && state.hasUnsavedChanges,
                onClick = viewModel::save
            ) {
                Text(if (state.isSaving) "Saving…" else "Save changes")
            }

            if (state.isSaving) {
                CircularProgressIndicator()
            }
        }

        // Leave room for the global Quick Exit button.
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun ResourceEditorField(
    label: String,
    value: String,
    enabled: Boolean,
    multiline: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        enabled = enabled,
        singleLine = !multiline,
        minLines = if (multiline) 3 else 1,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun AdminResourceDropdown(
    label: String,
    selected: String,
    options: Map<String, String>,
    enabled: Boolean,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(label)

        Box {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                onClick = { expanded = true }
            ) {
                Text(options[selected] ?: "Select an option")
            }

            DropdownMenu(
                expanded = expanded && enabled,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { (id, name) ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            expanded = false
                            onSelected(id)
                        }
                    )
                }
            }
        }
    }
}