package com.example.androidapp.pages.messagingpage

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.androidapp.data.ChatAttachment
import com.example.androidapp.data.ChatMessage
import com.example.androidapp.data.MAX_MESSAGE_LENGTH
import com.example.androidapp.data.PendingAttachment
import com.example.androidapp.data.attachmentError
import com.example.androidapp.data.canSend
import com.example.androidapp.data.formatFileSize
import com.example.androidapp.data.formatMessageTime
import com.example.androidapp.security.LocalPrivacyExternalInteraction
import com.example.androidapp.utils.readPendingAttachment
import java.io.File

@Composable
fun MessageThreadScreen(
    contactName: String,
    state: ThreadUiState,
    onBack: () -> Unit,
    onQuickExit: () -> Unit,
    onSend: (String, PendingAttachment?, () -> Unit) -> Unit,
    onOpenAttachment: (ChatAttachment, (File, String) -> Unit) -> Unit,
    onDismissSendError: () -> Unit
) {
    val context = LocalContext.current
    val externalInteraction = LocalPrivacyExternalInteraction.current

    var draft by remember { mutableStateOf("") }
    var pending by remember { mutableStateOf<PendingAttachment?>(null) }
    var localError by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    // Opened files are only kept while this screen is showing.
    DisposableEffect(Unit) {
        onDispose { File(context.cacheDir, "message_files").deleteRecursively() }
    }

    // The system picker and the viewer leave the app. Tell the privacy guard,
    // exactly like the Notes and Documents screens do, so it doesn't quick-exit.
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        externalInteraction(false)
        if (uri != null) {
            val picked = readPendingAttachment(context, uri)
            val problem = attachmentError(picked)
            if (problem != null) {
                localError = problem
            } else {
                localError = null
                pending = picked
            }
        }
    }

    val viewer = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { externalInteraction(false) }

    fun launchPicker() {
        externalInteraction(true)
        try {
            picker.launch("*/*")
        } catch (_: Exception) {
            externalInteraction(false)
            localError = "Unable to open the file picker. Please try again."
        }
    }

    fun openFile(file: File, mimeType: String) {
        try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            externalInteraction(true)
            viewer.launch(intent)
        } catch (_: Exception) {
            externalInteraction(false)
            localError = "No app available to open this file."
        }
    }

    val shownError = state.sendError ?: localError

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = 96.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "←",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = contactName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

        }

        if (state.isOffline) {
            Box(Modifier.padding(horizontal = 24.dp)) { OfflineBanner() }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                state.errorMessage != null -> Text(
                    text = state.errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )

                state.messages.isEmpty() -> Text(
                    text = "No messages yet. Say hello to $contactName.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            otherName = contactName,
                            isOpening = message.attachment?.path == state.openingPath,
                            onOpenAttachment = { attachment ->
                                localError = null
                                onOpenAttachment(attachment) { file, mime -> openFile(file, mime) }
                            }
                        )
                    }
                }
            }
        }

        shownError?.let { error ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    localError = null
                    onDismissSendError()
                }) { Text("Dismiss") }
            }
        }

        // The file waiting to be sent.
        pending?.let { file ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (state.isUploading) "Uploading ${file.name}…"
                    else "📎 ${file.name} (${formatFileSize(file.sizeBytes)})",
                    fontSize = 14.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    enabled = !state.isUploading,
                    onClick = { pending = null }
                ) { Text("Remove") }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = !state.isUploading,
                onClick = { launchPicker() }
            ) {
                Icon(Icons.Default.AttachFile, contentDescription = "Attach a file")
            }
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(MAX_MESSAGE_LENGTH) },
                placeholder = { Text("Message") },
                shape = RoundedCornerShape(24.dp),
                maxLines = 4,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                enabled = canSend(draft, pending != null) && !state.isUploading,
                onClick = {
                    onSend(draft, pending) {
                        draft = ""
                        pending = null
                    }
                }
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    otherName: String,
    isOpening: Boolean = false,
    onOpenAttachment: (ChatAttachment) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (message.isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (message.isMine) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            val textColor = if (message.isMine) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant

            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = if (message.isMine) "You" else otherName,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )

                if (message.text.isNotBlank()) {
                    Text(text = message.text, color = textColor)
                }

                message.attachment?.let { attachment ->
                    Text(
                        text = "📎 ${attachment.name}",
                        fontWeight = FontWeight.SemiBold,
                        color = textColor
                    )
                    Text(
                        text = formatFileSize(attachment.sizeBytes),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor
                    )
                    // A file that is still being written can't be opened by the other side yet.
                    if (!message.isPending) {
                        TextButton(
                            enabled = !isOpening,
                            onClick = { onOpenAttachment(attachment) }
                        ) {
                            Text(
                                text = if (isOpening) "Opening…" else "Open",
                                color = textColor
                            )
                        }
                    }
                }

                Text(
                    text = if (message.isPending) "Sending…"
                    else formatMessageTime(message.createdAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
            }
        }
    }
}