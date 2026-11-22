package com.example.androidapp.pages.messagingpage

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun MessagingRoot(
    viewModel: MessagingViewModel = viewModel(),
    onBackClick: () -> Unit = {},
    onQuickExit: () -> Unit = {}
) {
    val context = LocalContext.current
    val inbox = viewModel.inbox
    val openId = viewModel.openConversationId
    val open = inbox.conversations.firstOrNull { it.id == openId }

    // If the connection disappears while a thread is open, return to the inbox.
    LaunchedEffect(openId, open, inbox.isLoading) {
        if (openId != null && open == null && !inbox.isLoading) {
            viewModel.closeConversation()
        }
    }

    if (open == null) {
        BackHandler(onBack = onBackClick)
        ConversationListScreen(
            state = inbox,
            onConversationClick = { viewModel.openConversation(it.id) },
            onBackClick = onBackClick,
            onQuickExit = onQuickExit
        )
    } else {
        BackHandler { viewModel.closeConversation() }
        MessageThreadScreen(
            contactName = open.otherName,
            state = viewModel.thread,
            onBack = { viewModel.closeConversation() },
            onQuickExit = onQuickExit,
            onSend = { text, attachment, onAccepted ->
                viewModel.sendMessage(text, attachment, onAccepted)
            },
            onOpenAttachment = { attachment, onReady ->
                viewModel.openAttachment(attachment, context.cacheDir, onReady)
            },
            onDismissSendError = { viewModel.clearSendError() }
        )
    }
}