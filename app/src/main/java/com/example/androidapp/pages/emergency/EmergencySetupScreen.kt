package com.example.androidapp.pages.emergency

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.androidapp.data.TrustedContact
import com.example.androidapp.data.TrustedContactRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source

@Composable
fun EmergencySetupScreen(
    onBack: () -> Unit,
    onOpenInbox: () -> Unit,
    onOpenSend: () -> Unit
)  {
    val auth = remember { FirebaseAuth.getInstance() }
    val db = remember { FirebaseFirestore.getInstance() }
    val repository = remember { TrustedContactRepository(db) }

    var receiveInApp by remember { mutableStateOf(false) }

    var contacts by remember {
        mutableStateOf<List<TrustedContact>>(emptyList())
    }
    var selectedIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }
    var savedIds by remember {
        mutableStateOf<Set<String>>(emptySet())
    }

    var message by remember { mutableStateOf("") }
    var savedMessage by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    var generation by remember { mutableStateOf(0) }
    var activeUid by remember { mutableStateOf<String?>(null) }
    var showDiscard by remember { mutableStateOf(false) }

    val dirty =
        loaded && (selectedIds != savedIds || message != savedMessage)

    DisposableEffect(auth, db, repository, reload) {
        var disposed = false

        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            generation++
            val requestGeneration = generation
            val uid = firebaseAuth.currentUser?.uid

            activeUid = uid
            contacts = emptyList()
            selectedIds = emptySet()
            savedIds = emptySet()
            message = ""
            savedMessage = ""
            receiveInApp = false
            loaded = false
            saving = false
            error = null
            notice = null
            showDiscard = false
            loading = uid != null

            fun isCurrent(): Boolean =
                !disposed &&
                        generation == requestGeneration &&
                        auth.currentUser?.uid == uid

            if (uid == null) {
                error = "Please sign in to configure emergency contacts."
            } else {
                repository.getContacts(uid) { contactResult ->
                    if (isCurrent()) {
                        contactResult.fold(
                            onSuccess = { loadedContacts ->
                                db.collection("users")
                                    .document(uid)
                                    .collection("emergencySettings")
                                    .document("preset")
                                    .get(Source.SERVER)
                                    .addOnSuccessListener { settings ->
                                        if (isCurrent()) {
                                            contacts = loadedContacts.sortedBy {
                                                it.name.lowercase()
                                            }

                                            val initialIds = loadedContacts
                                                .filter { it.emergencyContact }
                                                .map { it.id }
                                                .toSet()

                                            val initialMessage =
                                                settings.getString("message")
                                                    ?: "Please check on me. I need help."

                                            selectedIds = initialIds
                                            savedIds = initialIds
                                            message = initialMessage
                                            savedMessage = initialMessage

                                            receiveInApp =
                                                settings.getBoolean("receiveInApp")
                                                    ?: false

                                            loading = false
                                            loaded = true
                                        }
                                    }
                                    .addOnFailureListener {
                                        if (isCurrent()) {
                                            loading = false
                                            error =
                                                "Unable to load emergency settings. Check your connection and retry."
                                        }
                                    }
                            },
                            onFailure = {
                                loading = false
                                error = "Unable to load contacts. Please retry."
                            }
                        )
                    }
                }
            }
        }

        auth.addAuthStateListener(listener)

        onDispose {
            disposed = true
            generation++
            auth.removeAuthStateListener(listener)
        }
    }

    fun requestBack() {
        if (!saving) {
            if (dirty) {
                showDiscard = true
            } else {
                onBack()
            }
        }
    }

    BackHandler {
        requestBack()
    }

    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Discard changes?") },
            text = {
                Text("Your unsaved selections and message will be lost.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscard = false
                        onBack()
                    }
                ) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text("Keep editing")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(
            onClick = { requestBack() },
            enabled = !saving
        ) {
            Text("Back")
        }

        Text(
            text = "Emergency setup",
            style = MaterialTheme.typography.headlineMedium
        )

        Text("Choose trusted contacts and prepare your help message.")
        OutlinedButton(
            onClick = onOpenInbox,
            enabled = !saving && !dirty,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open emergency inbox")
        }

        OutlinedButton(
            onClick = onOpenSend,
            enabled = loaded && !saving && !dirty,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Prepare in-app alert")
        }

        if (dirty) {
            Text(
                text = "Save your changes before opening the inbox.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Text(
            text = "Saving settings and changing delivery preferences do not send an alert.",
            style = MaterialTheme.typography.bodyMedium
        )

        if (loading) {
            CircularProgressIndicator()
        }

        error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (!loading && !loaded) {
            Button(onClick = { reload++ }) {
                Text("Retry")
            }
        }

        if (loaded) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Receive in-app emergency alerts",
                            modifier = Modifier.weight(1f)
                        )

                        Switch(
                            checked = receiveInApp,
                            enabled = !saving,
                            onCheckedChange = { enabled ->
                                val uid = activeUid

                                if (
                                    uid == null ||
                                    auth.currentUser?.uid != uid
                                ) {
                                    error = "Please sign in again."
                                } else {
                                    val requestGeneration = generation

                                    saving = true
                                    error = null
                                    notice = null

                                    repository.setReceiveEmergencyInApp(
                                        uid = uid,
                                        enabled = enabled
                                    ) { result ->
                                        if (
                                            generation == requestGeneration &&
                                            auth.currentUser?.uid == uid
                                        ) {
                                            saving = false

                                            result.onSuccess {
                                                receiveInApp = enabled
                                                notice =
                                                    "Receiving preference saved."
                                            }

                                            result.onFailure {
                                                error =
                                                    "Could not confirm the receiving preference. Reopen this screen to check before retrying."
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }

                    Text(
                        text = "Allow other users who have you linked as an emergency contact to send you in-app alerts. This does not enable phone push notifications.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Text(
                text = "Save contact selections and message changes before changing a contact's delivery switch. Delivery switches save immediately.",
                style = MaterialTheme.typography.bodySmall
            )

            if (contacts.isEmpty()) {
                Text("Add a trusted contact in Contacts first.")
            }

            contacts.forEach { contact ->
                val linkedToOtherAccount =
                    !contact.linkedUserId.isNullOrBlank() &&
                            contact.linkedUserId != activeUid

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = contact.id in selectedIds,
                                enabled = !saving,
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + contact.id
                                    } else {
                                        selectedIds - contact.id
                                    }

                                    notice = null
                                    error = null
                                }
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    contact.name.ifBlank {
                                        "Unnamed contact"
                                    }
                                )

                                Text(
                                    text = when {
                                        contact.linkedUserId.isNullOrBlank() ->
                                            "No linked app account"

                                        contact.linkedUserId == activeUid ->
                                            "Linked to your own account"

                                        else ->
                                            "Linked app account"
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Send in-app alerts",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium
                            )

                            Switch(
                                checked = contact.emergencyInAppOptIn,
                                enabled = !saving &&
                                        !dirty &&
                                        (
                                                contact.emergencyInAppOptIn ||
                                                        (
                                                                contact.id in savedIds &&
                                                                        linkedToOtherAccount
                                                                )
                                                ),
                                onCheckedChange = { enabled ->
                                    val uid = activeUid

                                    if (
                                        uid == null ||
                                        auth.currentUser?.uid != uid
                                    ) {
                                        error = "Please sign in again."
                                    } else {
                                        val requestGeneration = generation

                                        saving = true
                                        error = null
                                        notice = null

                                        repository.setContactEmergencyInApp(
                                            uid = uid,
                                            contactId = contact.id,
                                            enabled = enabled
                                        ) { result ->
                                            if (
                                                generation == requestGeneration &&
                                                auth.currentUser?.uid == uid
                                            ) {
                                                saving = false

                                                result.onSuccess {
                                                    contacts = contacts.map {
                                                        if (it.id == contact.id) {
                                                            it.copy(
                                                                emergencyInAppOptIn =
                                                                    enabled
                                                            )
                                                        } else {
                                                            it
                                                        }
                                                    }

                                                    notice =
                                                        "Contact delivery preference saved."
                                                }

                                                result.onFailure {
                                                    error =
                                                        "Could not confirm the delivery preference. Reopen this screen to check before retrying."
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Text("${selectedIds.size} contact(s) selected")

            Text(
                text = "Deselecting a contact also turns off their emergency delivery preferences when you save. Selecting them again does not automatically re-enable those preferences.",
                style = MaterialTheme.typography.bodySmall
            )

            OutlinedTextField(
                value = message,
                onValueChange = {
                    if (it.length <= 1000) {
                        message = it
                        notice = null
                        error = null
                    }
                },
                enabled = !saving,
                label = { Text("Help message") },
                supportingText = {
                    Text("${message.length}/1000")
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving && message.isNotBlank(),
                onClick = {
                    val uid = activeUid

                    if (
                        uid == null ||
                        auth.currentUser?.uid != uid
                    ) {
                        error = "Please sign in again."
                    } else {
                        val requestGeneration = generation
                        val idsToSave = selectedIds.toSet()
                        val messageToSave = message.trim()

                        saving = true
                        error = null
                        notice = null

                        repository.saveEmergencySetup(
                            uid = uid,
                            selectedContactIds = idsToSave,
                            message = messageToSave
                        ) { result ->
                            if (
                                generation == requestGeneration &&
                                auth.currentUser?.uid == uid
                            ) {
                                saving = false

                                result.onSuccess {
                                    savedIds = idsToSave
                                    savedMessage = messageToSave
                                    message = messageToSave

                                    contacts = contacts.map { contact ->
                                        val selected = contact.id in idsToSave

                                        contact.copy(
                                            emergencyContact = selected,
                                            emergencyInAppOptIn =
                                                selected &&
                                                        contact.emergencyInAppOptIn,
                                            emergencyEmailOptIn =
                                                selected &&
                                                        contact.emergencyEmailOptIn,
                                            emergencySmsRecipient =
                                                selected &&
                                                        contact.emergencySmsRecipient
                                        )
                                    }

                                    notice = "Emergency settings saved."
                                }

                                result.onFailure {
                                    error =
                                        "Could not confirm saving. Check your connection and contact list, then retry."
                                }
                            }
                        }
                    }
                }
            ) {
                Text(
                    if (saving) {
                        "Saving…"
                    } else {
                        "Save settings"
                    }
                )
            }

            notice?.let {
                Text(it)
            }
        }

        // Leave room for the existing global Quick Exit button.
        Spacer(modifier = Modifier.height(80.dp))
    }
}