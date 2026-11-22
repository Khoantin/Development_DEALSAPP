package com.example.androidapp.pages.guidance

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidapp.data.EmergencyGuide
import com.example.androidapp.data.EmergencyGuideContent
import java.text.DateFormat
import java.util.Date
import java.util.UUID

@Composable
fun AdminEmergencyGuidancePage(
    viewModel: EmergencyGuidanceViewModel,
    onBack: () -> Unit
) {
    LaunchedEffect(viewModel) {
        viewModel.start(asAdmin = true)
    }

    // Clears local drafts and previews when authentication changes.
    key(viewModel.state.sessionVersion) {
        AdminGuidanceContent(viewModel, onBack)
    }
}

@Composable
private fun AdminGuidanceContent(
    viewModel: EmergencyGuidanceViewModel,
    onBack: () -> Unit
) {
    val state = viewModel.state

    var editor by remember { mutableStateOf<EmergencyGuide?>(null) }
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("both") }
    var stepsText by remember { mutableStateOf("") }
    var scenario by remember { mutableStateOf("") }

    var preview by remember { mutableStateOf<EmergencyGuide?>(null) }
    var withdrawal by remember { mutableStateOf<EmergencyGuide?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val busy = state.isLoading || state.isSaving

    val original = editor?.content
    val dirty = original != null && (
            title != original.title ||
                    summary != original.summary ||
                    audience != original.audience ||
                    stepsText != original.steps.joinToString("\n") ||
                    scenario != original.practiceScenario
            )

    fun openEditor(guide: EmergencyGuide) {
        editor = guide
        title = guide.content.title
        summary = guide.content.summary
        audience = guide.content.audience
        stepsText = guide.content.steps.joinToString("\n")
        scenario = guide.content.practiceScenario
        confirmDiscard = false
        viewModel.clearMessages()
    }

    val handleBack: () -> Unit = {
        when {
            state.isSaving -> Unit
            confirmDiscard -> confirmDiscard = false
            withdrawal != null -> withdrawal = null
            preview != null -> preview = null
            editor != null && dirty -> confirmDiscard = true
            editor != null -> editor = null
            else -> onBack()
        }
    }

    BackHandler(onBack = handleBack)

    // Only confirmed successful mutations close the current editor.
    var previousCompleted by remember {
        mutableStateOf(state.completedChanges)
    }

    LaunchedEffect(state.completedChanges) {
        if (state.completedChanges != previousCompleted) {
            previousCompleted = state.completedChanges
            editor = null
            preview = null
            withdrawal = null
            confirmDiscard = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 104.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(
            onClick = handleBack,
            enabled = !state.isSaving,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Back")
        }

        Text(
            "Emergency response guidance",
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            "Maintain guidance for Users and Support. " +
                    "This page does not monitor emergencies or send alerts."
        )

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        state.success?.let {
            Text(it, color = MaterialTheme.colorScheme.primary)
        }

        if (state.isLoading) {
            CircularProgressIndicator()
        }

        if (state.isSaving) {
            Text("Saving changes…")
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (confirmDiscard) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Discard unsaved edits?")
                    Text("The last saved draft will remain unchanged.")

                    TextButton(onClick = {
                        confirmDiscard = false
                        editor = null
                    }) {
                        Text("Discard edits")
                    }

                    TextButton(onClick = { confirmDiscard = false }) {
                        Text("Keep editing")
                    }
                }
            }
        } else {
            val currentPreview = preview
            val currentWithdrawal = withdrawal
            val currentEditor = editor

            when {
                currentWithdrawal != null -> {
                    Text(
                        "Withdraw “${currentWithdrawal.content.title}”?",
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        "It will no longer appear in newly loaded published " +
                                "guide lists. Your draft stays available. " +
                                "Someone already viewing the guide may still see " +
                                "their loaded copy until they refresh or leave."
                    )

                    Button(
                        enabled = !busy,
                        onClick = {
                            viewModel.withdraw(currentWithdrawal)
                        }
                    ) {
                        Text("Withdraw guide")
                    }

                    TextButton(
                        enabled = !busy,
                        onClick = { withdrawal = null }
                    ) {
                        Text("Keep published")
                    }
                }

                currentPreview != null -> {
                    Text(
                        "Saved draft preview",
                        style = MaterialTheme.typography.titleLarge
                    )

                    GuidancePreview(currentPreview.content)

                    HorizontalDivider()

                    Text(
                        "Publishing replaces the version readers currently " +
                                "see. Confirm that you reviewed the content and " +
                                "that the practice scenario is fictional."
                    )

                    Button(
                        enabled = !busy,
                        onClick = {
                            viewModel.publish(currentPreview)
                        }
                    ) {
                        Text("Reviewed — publish guide")
                    }

                    TextButton(
                        enabled = !busy,
                        onClick = { preview = null }
                    ) {
                        Text("Return without publishing")
                    }
                }

                currentEditor != null -> {
                    Text(
                        if (currentEditor.revision == 0L) {
                            "New guide"
                        } else {
                            "Edit draft"
                        },
                        style = MaterialTheme.typography.titleLarge
                    )

                    Text(
                        "Saving updates the draft only. " +
                                "Preview and publish it separately when ready."
                    )

                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            if (it.length <= 120) title = it
                        },
                        enabled = !busy,
                        label = { Text("Title") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = summary,
                        onValueChange = {
                            if (it.length <= 1000) summary = it
                        },
                        enabled = !busy,
                        label = { Text("Summary") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )

                    Text("Who is this guide for?")

                    listOf(
                        "user" to "User",
                        "support" to "Support",
                        "both" to "User and Support"
                    ).forEach { (value, label) ->
                        FilterChip(
                            selected = audience == value,
                            enabled = !busy,
                            onClick = { audience = value },
                            label = { Text(label) }
                        )
                    }

                    OutlinedTextField(
                        value = stepsText,
                        onValueChange = {
                            if (it.length <= 12012) stepsText = it
                        },
                        enabled = !busy,
                        label = { Text("Steps — one per line") },
                        supportingText = {
                            Text("Up to 12 steps, 1,000 characters per step.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5
                    )

                    OutlinedTextField(
                        value = scenario,
                        onValueChange = {
                            if (it.length <= 2000) scenario = it
                        },
                        enabled = !busy,
                        label = { Text("Fictional practice scenario") },
                        supportingText = {
                            Text("Use invented examples, not personal case details.")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4
                    )

                    Button(
                        enabled = !busy && title.isNotBlank(),
                        onClick = {
                            viewModel.save(
                                id = currentEditor.id,
                                revision = currentEditor.revision,
                                content = EmergencyGuideContent(
                                    title = title,
                                    summary = summary,
                                    audience = audience,
                                    steps = stepsText.lines()
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() },
                                    practiceScenario = scenario
                                )
                            )
                        }
                    ) {
                        Text("Save draft")
                    }

                    Text(
                        "If a save cannot be confirmed, return to the library " +
                                "and refresh before repeating it."
                    )
                }

                else -> {
                    Button(
                        enabled = !busy,
                        onClick = {
                            openEditor(
                                EmergencyGuide(
                                    id = UUID.randomUUID().toString(),
                                    content = EmergencyGuideContent()
                                )
                            )
                        }
                    ) {
                        Text("Create guide")
                    }

                    OutlinedButton(
                        enabled = !busy,
                        onClick = { viewModel.refresh() }
                    ) {
                        Text("Refresh")
                    }

                    if (
                        !busy &&
                        state.error == null &&
                        state.guides.isEmpty()
                    ) {
                        Text("No guides yet. Create your first draft.")
                    }

                    state.guides.forEach { guide ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    guide.content.title,
                                    style = MaterialTheme.typography.titleLarge
                                )

                                Text(
                                    when {
                                        !guide.isPublished -> "Draft — not published"
                                        guide.hasUnpublishedChanges ->
                                            "Published — draft has unpublished changes"
                                        else -> "Published"
                                    }
                                )

                                guide.lastReviewedAtMillis?.let {
                                    Text(
                                        "Last reviewed: " +
                                                DateFormat.getDateTimeInstance(
                                                    DateFormat.MEDIUM,
                                                    DateFormat.SHORT
                                                ).format(Date(it))
                                    )
                                }

                                OutlinedButton(
                                    enabled = !busy,
                                    onClick = { openEditor(guide) }
                                ) {
                                    Text("Edit draft")
                                }

                                OutlinedButton(
                                    enabled = !busy,
                                    onClick = {
                                        viewModel.clearMessages()
                                        preview = guide
                                    }
                                ) {
                                    Text("Preview and publish")
                                }

                                if (guide.isPublished) {
                                    TextButton(
                                        enabled = !busy,
                                        onClick = {
                                            viewModel.clearMessages()
                                            withdrawal = guide
                                        }
                                    ) {
                                        Text("Withdraw published guide")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GuidancePreview(content: EmergencyGuideContent) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            content.title,
            style = MaterialTheme.typography.headlineSmall
        )

        Text(
            "For: " + when (content.audience) {
                "user" -> "User"
                "support" -> "Support"
                else -> "User and Support"
            }
        )

        Text(content.summary.ifBlank { "Summary not added yet." })

        Text("Steps", style = MaterialTheme.typography.titleMedium)

        if (content.steps.isEmpty()) {
            Text("No steps added yet.")
        } else {
            content.steps.forEachIndexed { index, step ->
                Text("${index + 1}. $step")
            }
        }

        HorizontalDivider()

        Text(
            "Fictional practice scenario",
            style = MaterialTheme.typography.titleMedium
        )

        Text(
            content.practiceScenario.ifBlank {
                "Practice scenario not added yet."
            }
        )

        Text(
            "Practice only. Reading this guide does not send an alert " +
                    "or contact anyone.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}