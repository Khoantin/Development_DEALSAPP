package com.example.androidapp.pages.staffmessaging

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidapp.data.AdminAccount
import com.example.androidapp.data.OutgoingStaffMessage
import com.example.androidapp.data.StaffBubbleStatus
import com.example.androidapp.data.StaffChatItem
import com.example.androidapp.data.StaffMessage
import com.example.androidapp.data.StaffMessagingLogic
import com.example.androidapp.data.StaffThread
import com.example.androidapp.pages.admin.AdminScreenHeader
import com.example.androidapp.pages.admin.CardPadding
import com.example.androidapp.pages.admin.CardShape
import com.example.androidapp.pages.admin.ErrorCard
import com.example.androidapp.pages.admin.GridSpacing
import com.example.androidapp.pages.admin.QuickExitClearance
import com.example.androidapp.pages.admin.ScreenHorizontalPadding
import com.example.androidapp.pages.admin.ScreenTopSpacing
import com.example.androidapp.pages.admin.SectionTitle
import com.example.androidapp.pages.admin.tileColors
import com.example.androidapp.ui.theme.AndroidAppTheme
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

const val ADMIN_ANONYMITY_HINT =
    "Support staff see your messages as '${StaffMessagingLogic.ADMIN_LABEL_FOR_SUPPORT}'. " +
        "Don't include your name."

// -----------------------------------------------------------------
// SHARED PIECES
// -----------------------------------------------------------------

/** Unread count pill used on dashboards and inbox rows. Hidden at zero. */
@Composable
internal fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.error,
        contentColor = MaterialTheme.colorScheme.onError,
        modifier = modifier.clearAndSetSemantics {
            contentDescription = unreadDescription(count)
        }
    ) {
        Text(
            text = StaffMessagingLogic.badgeText(count),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

internal fun unreadDescription(count: Int) =
    if (count == 1) "1 unread message" else "$count unread messages"

@Composable
private fun BackLink(label: String, onBack: () -> Unit) {
    TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

@Composable
private fun OfflineNotice() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = "You're offline. Showing saved messages; new ones will appear when you reconnect.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatTime(millis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

/** Inbox time: clock time today, otherwise the short date. */
private fun formatInboxTime(millis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val day = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val sameDay = day.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
        day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    return if (sameDay) formatTime(millis)
    else DateFormat.getDateInstance(DateFormat.SHORT).format(Date(millis))
}

// -----------------------------------------------------------------
// ADMIN INBOX
// -----------------------------------------------------------------

@Composable
fun StaffInboxPage(
    state: StaffInboxUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onOpenThread: (supportUid: String, supportName: String) -> Unit,
    onNewMessage: () -> Unit,
    onClosePicker: () -> Unit,
    onRetry: () -> Unit
) {
    BackHandler {
        if (state.picker.isOpen) onClosePicker() else onBack()
    }

    val shown = StaffMessagingLogic.filterThreads(state.threads, state.query)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = ScreenHorizontalPadding)
    ) {
        item(key = "header") {
            Column {
                Spacer(Modifier.height(ScreenTopSpacing - 48.dp))
                BackLink("Admin dashboard", onBack)
                AdminScreenHeader(title = "Staff messages")
                Spacer(Modifier.height(16.dp))
                Text(
                    text = ADMIN_ANONYMITY_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onNewMessage,
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("New message")
                }
                Spacer(Modifier.height(GridSpacing))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text("Search by name") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.isOffline) OfflineNotice()
                Spacer(Modifier.height(24.dp))
                SectionTitle("Conversations")
            }
        }

        when {
            state.errorMessage != null -> item(key = "error") {
                ErrorCard(message = state.errorMessage, retryEnabled = true, onRetry = onRetry)
            }

            state.isLoading -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        Modifier.semantics { contentDescription = "Loading conversations" }
                    )
                }
            }

            shown.isEmpty() -> item(key = "empty") {
                Text(
                    text = if (state.threads.isEmpty()) {
                        "No staff conversations yet. Tap New message to contact a support account."
                    } else {
                        "No conversations match \"${state.query.trim()}\"."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> items(shown, key = { it.supportUid }) { thread ->
                ThreadRow(thread, onClick = {
                    onOpenThread(thread.supportUid, thread.supportDisplayName)
                })
                Spacer(Modifier.height(12.dp))
            }
        }

        item(key = "bottom") {
            Spacer(Modifier.height(QuickExitClearance))
            Spacer(Modifier.navigationBarsPadding())
        }
    }

    if (state.picker.isOpen) {
        SupportPickerDialog(
            picker = state.picker,
            onPick = { account -> onOpenThread(account.uid, account.displayName) },
            onRetry = onNewMessage,
            onDismiss = onClosePicker
        )
    }
}

@Composable
private fun ThreadRow(thread: StaffThread, onClick: () -> Unit) {
    val name = thread.supportDisplayName.ifBlank { "Support staff" }
    val unread = thread.adminUnread
    val preview = StaffMessagingLogic.adminPreview(thread)
    val time = thread.lastMessageAtMillis?.let { formatInboxTime(it) }.orEmpty()

    Card(
        onClick = onClick,
        shape = CardShape,
        colors = tileColors(),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clearAndSetSemantics {
                contentDescription = buildString {
                    append("$name. $preview. $time.")
                    if (unread > 0) append(" ${unreadDescription(unread)}.")
                    append(" Opens the conversation.")
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontSize = 16.sp,
                    fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = time,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                UnreadBadge(unread)
            }
        }
    }
}

@Composable
private fun SupportPickerDialog(
    picker: StaffSupportPickerState,
    onPick: (AdminAccount) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New message") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    picker.isLoading -> CircularProgressIndicator(
                        Modifier.semantics { contentDescription = "Loading support accounts" }
                    )

                    picker.errorMessage != null -> {
                        Text(picker.errorMessage, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("Try again")
                        }
                    }

                    picker.accounts.isEmpty() -> Text("There are no active support accounts.")

                    else -> {
                        Text(
                            "Choose an active support account.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        picker.accounts.forEach { account ->
                            val name = account.displayName.ifBlank { account.email }
                            TextButton(
                                onClick = { onPick(account) },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            ) {
                                Text(name, modifier = Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Cancel")
            }
        }
    )
}

// -----------------------------------------------------------------
// THREAD (admin and support)
// -----------------------------------------------------------------

/**
 * One staff conversation. Admins see "Administration" on the right and the
 * support name on the left; support sees "D.E.A.L.S. Administrator" on the
 * left and their own messages on the right.
 */
@Composable
fun StaffThreadPage(
    state: StaffThreadUiState,
    supportName: String,
    onBack: () -> Unit,
    onSend: (String) -> Boolean,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit,
    onRetryLoad: () -> Unit,
    nowMillis: Long = System.currentTimeMillis()
) {
    BackHandler(onBack = onBack)

    val isAdmin = state.viewerRole == StaffMessagingLogic.ADMIN
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val items = remember(state.messages, state.outgoing, state.viewerRole, supportName) {
        StaffMessagingLogic.buildItems(
            messages = state.messages,
            outgoing = state.outgoing,
            viewerRole = state.viewerRole,
            supportName = supportName,
            nowMillis = nowMillis
        )
    }

    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = QuickExitClearance)
    ) {
        Column(Modifier.padding(horizontal = ScreenHorizontalPadding)) {
            Spacer(Modifier.height(ScreenTopSpacing - 48.dp))
            if (isAdmin) {
                BackLink("Staff messages", onBack)
                AdminScreenHeader(title = supportName.ifBlank { "Support staff" })
            } else {
                BackLink("Dashboard", onBack)
                // Same title treatment as SupportDashboardPage.
                Text(
                    text = "Message administration",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() }
                )
            }
            if (state.isOffline) OfflineNotice()
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.errorMessage != null -> Box(Modifier.padding(ScreenHorizontalPadding)) {
                    ErrorCard(message = state.errorMessage, retryEnabled = true, onRetry = onRetryLoad)
                }

                state.isLoading -> CircularProgressIndicator(
                    Modifier
                        .align(Alignment.Center)
                        .semantics { contentDescription = "Loading messages" }
                )

                items.isEmpty() -> Text(
                    text = if (isAdmin) {
                        "No messages yet. Send the first message to ${supportName.ifBlank { "this support account" }}."
                    } else {
                        "Need help from the D.E.A.L.S. team? Send a message to the " +
                            "administration team here and an administrator will reply " +
                            "in this conversation."
                    },
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(ScreenHorizontalPadding)
                )

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp)
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is StaffChatItem.DayHeader -> DayHeader(item.label)
                            is StaffChatItem.Bubble -> StaffBubble(item, onRetry, onDiscard)
                        }
                    }
                }
            }
        }

        Composer(
            draft = draft,
            onDraftChange = { draft = it.take(StaffMessagingLogic.TEXT_MAX) },
            showAdminHint = isAdmin,
            enabled = state.errorMessage == null && !state.isLoading,
            onSend = { if (onSend(draft)) draft = "" }
        )
    }
}

@Composable
private fun DayHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .semantics { heading() }
    )
}

@Composable
private fun StaffBubble(
    bubble: StaffChatItem.Bubble,
    onRetry: (String) -> Unit,
    onDiscard: (String) -> Unit
) {
    val time = formatTime(bubble.timeMillis)
    val statusText = when (bubble.status) {
        StaffBubbleStatus.SENT -> time
        StaffBubbleStatus.SENDING -> "Sending…"
        StaffBubbleStatus.FAILED -> "Not sent"
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (bubble.isMine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (bubble.isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = when {
                    bubble.status == StaffBubbleStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                    bubble.isMine -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${bubble.senderLabel}, $statusText: ${bubble.text}"
                }
            ) {
                val textColor = when {
                    bubble.status == StaffBubbleStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                    bubble.isMine -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Column(Modifier.padding(12.dp)) {
                    Text(
                        text = bubble.senderLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(text = bubble.text, color = textColor)
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }

            if (bubble.status == StaffBubbleStatus.FAILED && bubble.localId != null) {
                Text(
                    text = bubble.errorMessage.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite }
                )
                Row {
                    TextButton(
                        onClick = { onDiscard(bubble.localId) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Delete") }
                    TextButton(
                        onClick = { onRetry(bubble.localId) },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) { Text("Retry") }
                }
            }
        }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    showAdminHint: Boolean,
    enabled: Boolean,
    onSend: () -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (showAdminHint) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = ADMIN_ANONYMITY_HINT,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                enabled = enabled,
                label = { Text("Message") },
                supportingText = {
                    Text(
                        text = "${draft.length} / ${StaffMessagingLogic.TEXT_MAX}",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                },
                shape = RoundedCornerShape(24.dp),
                maxLines = 5,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onSend,
                enabled = enabled && StaffMessagingLogic.sendableText(draft) != null,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send message")
            }
        }
    }
}

// -----------------------------------------------------------------
// PREVIEWS (sample data only)
// -----------------------------------------------------------------

private const val PREVIEW_NOW = 1_795_000_000_000L

private val previewThreads = listOf(
    StaffThread("s1", "Sam Support", PREVIEW_NOW - 60_000, "Thanks, that worked.", "support", 0, 2),
    StaffThread("s2", "Pat Support", PREVIEW_NOW - 90_000_000, "Please review the new guide.", "admin", 1, 0)
)

private val previewMessages = listOf(
    StaffMessage("m1", "admin", "Hi Sam, could you check the shelter list for Edmonton?", PREVIEW_NOW - 90_000_000),
    StaffMessage("m2", "support", "Sure, I'll look at it today.", PREVIEW_NOW - 89_000_000),
    StaffMessage("m3", "support", "Two numbers were out of date. Can you update them?", PREVIEW_NOW - 120_000)
)

private val previewOutgoing = listOf(
    OutgoingStaffMessage("o1", "On it, thanks!", PREVIEW_NOW - 30_000),
    OutgoingStaffMessage(
        "o2", "Also fixed the hours.", PREVIEW_NOW - 10_000,
        errorMessage = StaffMessagingLogic.errorMessage("RESOURCE_EXHAUSTED", null)
    )
)

@Preview(name = "Admin inbox", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun StaffInboxPreview() {
    AndroidAppTheme(dynamicColor = false) {
        StaffInboxPage(
            state = StaffInboxUiState(isLoading = false, threads = previewThreads),
            onBack = {}, onQueryChange = {}, onOpenThread = { _, _ -> },
            onNewMessage = {}, onClosePicker = {}, onRetry = {}
        )
    }
}

@Preview(name = "Admin inbox, large font", showBackground = true, widthDp = 412, heightDp = 1400, fontScale = 2f)
@Composable
private fun StaffInboxLargeFontPreview() {
    AndroidAppTheme(dynamicColor = false) {
        StaffInboxPage(
            state = StaffInboxUiState(isLoading = false, threads = previewThreads, isOffline = true),
            onBack = {}, onQueryChange = {}, onOpenThread = { _, _ -> },
            onNewMessage = {}, onClosePicker = {}, onRetry = {}
        )
    }
}

@Preview(name = "Admin thread", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun AdminThreadPreview() {
    AndroidAppTheme(dynamicColor = false) {
        StaffThreadPage(
            state = StaffThreadUiState(
                viewerRole = "admin", supportUid = "s1", isLoading = false,
                messages = previewMessages, outgoing = previewOutgoing
            ),
            supportName = "Sam Support",
            onBack = {}, onSend = { true }, onRetry = {}, onDiscard = {}, onRetryLoad = {},
            nowMillis = PREVIEW_NOW
        )
    }
}

@Preview(name = "Support thread", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun SupportThreadPreview() {
    AndroidAppTheme(dynamicColor = false) {
        StaffThreadPage(
            state = StaffThreadUiState(
                viewerRole = "support", supportUid = "s1", isLoading = false, messages = previewMessages
            ),
            supportName = "Sam Support",
            onBack = {}, onSend = { true }, onRetry = {}, onDiscard = {}, onRetryLoad = {},
            nowMillis = PREVIEW_NOW
        )
    }
}

@Preview(name = "Support thread, empty, large font", showBackground = true, widthDp = 412, heightDp = 900, fontScale = 1.8f)
@Composable
private fun SupportThreadEmptyPreview() {
    AndroidAppTheme(dynamicColor = false) {
        StaffThreadPage(
            state = StaffThreadUiState(viewerRole = "support", supportUid = "s1", isLoading = false),
            supportName = "Sam Support",
            onBack = {}, onSend = { true }, onRetry = {}, onDiscard = {}, onRetryLoad = {},
            nowMillis = PREVIEW_NOW
        )
    }
}

@Preview(name = "Inactive account", showBackground = true, widthDp = 412, heightDp = 700)
@Composable
private fun ThreadErrorPreview() {
    AndroidAppTheme(dynamicColor = false) {
        StaffThreadPage(
            state = StaffThreadUiState(
                viewerRole = "support", isLoading = false,
                errorMessage = StaffMessagingLogic.errorMessage("PERMISSION_DENIED", null)
            ),
            supportName = "",
            onBack = {}, onSend = { false }, onRetry = {}, onDiscard = {}, onRetryLoad = {},
            nowMillis = PREVIEW_NOW
        )
    }
}
