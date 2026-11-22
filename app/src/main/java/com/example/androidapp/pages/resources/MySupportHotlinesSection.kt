package com.example.androidapp.pages.resources

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.androidapp.data.SupportHotline
import com.example.androidapp.data.SupportHotlineRepository
import com.example.androidapp.data.toFriendlyFirestoreMessage

@Composable
internal fun MySupportHotlinesSection(
    uid: String,
    onQuickExit: () -> Unit
) {
    key(uid) {
        MySupportHotlinesContent(uid, onQuickExit)
    }
}

@Composable
private fun MySupportHotlinesContent(
    uid: String,
    onQuickExit: () -> Unit
) {
    val repository = remember { SupportHotlineRepository() }

    var hotlines by remember {
        mutableStateOf<List<SupportHotline>>(emptyList())
    }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var adding by remember { mutableStateOf(false) }

    DisposableEffect(uid, retry) {
        var disposed = false

        loading = true
        loadError = null
        hotlines = emptyList()

        val registration = repository.observeHotlines(uid) { result ->
            if (!disposed) {
                result.onSuccess {
                    hotlines = it
                    loading = false
                    loadError = null
                }.onFailure {
                    hotlines = emptyList()
                    loading = false
                    loadError = it.toFriendlyFirestoreMessage()
                }
            }
        }

        onDispose {
            disposed = true
            registration.remove()
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "My support hotlines",
            style = MaterialTheme.typography.titleLarge
        )

        Text("Save hotline names and numbers for your own reference.")

        if (adding) {
            HotlineEditor(
                title = "Add a support hotline",
                onCancel = { adding = false },
                onSave = { name, phone, notes, complete ->
                    repository.addHotline(
                        uid = uid,
                        name = name,
                        phoneNumber = phone,
                        notes = notes,
                        onResult = complete
                    )
                },
                onSaved = { adding = false }
            )
        } else {
            OutlinedButton(onClick = { adding = true }) {
                Text("Add hotline")
            }
        }

        when {
            loading -> {
                CircularProgressIndicator()
                Text("Loading your hotlines…")
            }

            loadError != null -> {
                Text(
                    loadError.orEmpty(),
                    color = MaterialTheme.colorScheme.error
                )
                TextButton(onClick = { retry++ }) {
                    Text("Retry")
                }
            }

            hotlines.isEmpty() -> {
                Text("You haven’t added any hotlines yet.")
            }

            else -> {
                hotlines.forEach { hotline ->
                    key(hotline.id) {
                        SavedHotlineCard(
                            uid = uid,
                            hotline = hotline,
                            repository = repository,
                            onQuickExit = onQuickExit
                        )
                    }
                }
            }
        }

        HorizontalDivider()
    }
}

@Composable
private fun HotlineEditor(
    title: String,
    initialName: String = "",
    initialPhone: String = "",
    initialNotes: String = "",
    onCancel: () -> Unit,
    onSave: (
        String,
        String,
        String,
        (Result<Unit>) -> Unit
    ) -> Unit,
    onSaved: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phone by remember { mutableStateOf(initialPhone) }
    var notes by remember { mutableStateOf(initialNotes) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val active = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        active.value = true
        onDispose { active.value = false }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = name,
                onValueChange = {
                    if (it.length <= 100) name = it
                },
                label = { Text("Hotline name") },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = phone,
                onValueChange = {
                    if (it.length <= 40) phone = it
                },
                label = { Text("Phone number") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Phone
                ),
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = notes,
                onValueChange = {
                    if (it.length <= 1000) notes = it
                },
                label = { Text("Notes (optional)") },
                supportingText = { Text("${notes.length}/1000") },
                minLines = 2,
                maxLines = 4,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            if (saving) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Saving… An internet connection is required.")
            }

            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    error = null

                    onSave(name, phone, notes) { result ->
                        if (active.value) {
                            saving = false
                            result.onSuccess {
                                onSaved()
                            }.onFailure {
                                error = hotlineErrorMessage(it)
                            }
                        }
                    }
                }
            ) {
                Text(if (saving) "Saving…" else "Save hotline")
            }

            TextButton(
                enabled = !saving,
                onClick = onCancel
            ) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun SavedHotlineCard(
    uid: String,
    hotline: SupportHotline,
    repository: SupportHotlineRepository,
    onQuickExit: () -> Unit
) {
    val context = LocalContext.current

    var editing by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }

    val active = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        active.value = true
        onDispose { active.value = false }
    }

    // Clear a confirmation if the saved contact details change.
    LaunchedEffect(hotline.name, hotline.phoneNumber) {
        pendingAction = null
        actionError = null
    }

    if (editing) {
        HotlineEditor(
            title = "Edit support hotline",
            initialName = hotline.name,
            initialPhone = hotline.phoneNumber,
            initialNotes = hotline.notes,
            onCancel = { editing = false },
            onSave = { name, phone, notes, complete ->
                repository.updateHotline(
                    uid = uid,
                    hotlineId = hotline.id,
                    name = name,
                    phoneNumber = phone,
                    notes = notes,
                    onResult = complete
                )
            },
            onSaved = { editing = false }
        )
    } else {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    hotline.name,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(hotline.phoneNumber)

                if (hotline.notes.isNotBlank()) {
                    Text(hotline.notes)
                }

                OutlinedButton(
                    enabled = !deleting,
                    onClick = {
                        pendingAction = "dial"
                        actionError = null
                    }
                ) {
                    Text("Open dialler")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        enabled = !deleting,
                        onClick = {
                            pendingAction = null
                            actionError = null
                            editing = true
                        }
                    ) {
                        Text("Edit")
                    }

                    TextButton(
                        enabled = !deleting,
                        onClick = {
                            pendingAction = "delete"
                            actionError = null
                        }
                    ) {
                        Text("Delete")
                    }
                }

                when (pendingAction) {
                    "dial" -> {
                        HorizontalDivider()

                        Text(
                            "Open the phone dialler?",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            "This opens another app with the saved number. " +
                                    "No call is placed automatically. D.E.A.L.S. " +
                                    "will return to WeatherNow and clear any " +
                                    "unsaved forms on this page."
                        )

                        Text(
                            "Privacy protections do not cover the phone app. " +
                                    "If you place a call, it may appear in your " +
                                    "call history or phone records."
                        )

                        Button(
                            onClick = {
                                val phone = hotline.phoneNumber

                                if (!phone.matches(
                                        Regex("[+]?[0-9]{3,15}")
                                    )
                                ) {
                                    pendingAction = null
                                    actionError =
                                        "This phone number is not valid."
                                } else {
                                    val intent = Intent(
                                        Intent.ACTION_DIAL,
                                        Uri.fromParts("tel", phone, null)
                                    )

                                    pendingAction = null

                                    val launched = try {
                                        context.startActivity(intent)
                                        true
                                    } catch (_: ActivityNotFoundException) {
                                        actionError =
                                            "No phone app is available."
                                        false
                                    } catch (_: SecurityException) {
                                        actionError =
                                            "This device could not open the dialler."
                                        false
                                    }

                                    if (launched) {
                                        onQuickExit()
                                    }
                                }
                            }
                        ) {
                            Text("Continue to dialler")
                        }

                        TextButton(
                            onClick = { pendingAction = null }
                        ) {
                            Text("Stay here")
                        }
                    }

                    "delete" -> {
                        HorizontalDivider()

                        Text(
                            "Delete this saved hotline?",
                            style = MaterialTheme.typography.titleMedium
                        )

                        Text(
                            "This removes ${hotline.name} from your " +
                                    "personal list."
                        )

                        Button(
                            enabled = !deleting,
                            onClick = {
                                deleting = true
                                actionError = null

                                repository.deleteHotline(
                                    uid = uid,
                                    hotlineId = hotline.id
                                ) { result ->
                                    if (active.value) {
                                        deleting = false

                                        result.onSuccess {
                                            pendingAction = null
                                        }.onFailure {
                                            actionError =
                                                hotlineErrorMessage(it)
                                        }
                                    }
                                }
                            }
                        ) {
                            Text(
                                if (deleting) "Deleting…"
                                else "Delete hotline"
                            )
                        }

                        TextButton(
                            enabled = !deleting,
                            onClick = { pendingAction = null }
                        ) {
                            Text("Keep hotline")
                        }
                    }
                }

                actionError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun hotlineErrorMessage(error: Throwable): String =
    if (error is IllegalArgumentException) {
        error.message ?: "Please check the entered details."
    } else {
        error.toFriendlyFirestoreMessage()
    }