package com.example.androidapp.pages.admin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick as semanticsOnClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.androidapp.data.AdminAccount
import com.example.androidapp.data.AdminAccountOptions
import com.example.androidapp.data.AdminAccountsLogic
import com.example.androidapp.ui.theme.AndroidAppTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun AdminAccountsPage(
    viewModel: AdminAccountsViewModel,
    onBack: () -> Unit
) {
    AdminAccountsContent(
        state = viewModel.state,
        onBack = onBack,
        onRefresh = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onRoleFilter = viewModel::setRoleFilter,
        onStatusFilter = viewModel::setStatusFilter,
        onQueryChange = viewModel::setQuery,
        onSelect = viewModel::select,
        onDismissDetails = viewModel::dismissDetails,
        onStartChange = viewModel::startStatusChange,
        onCancelChange = viewModel::cancelStatusChange,
        onConfirmChange = viewModel::confirmStatusChange,
        onStartRoleChange = viewModel::startRoleChange,
        onCancelRoleChange = viewModel::cancelRoleChange,
        onConfirmRoleChange = viewModel::confirmRoleChange,
        onDismissMessage = viewModel::clearMessage
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AdminAccountsContent(
    state: AdminAccountsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onRoleFilter: (String?) -> Unit,
    onStatusFilter: (String?) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismissDetails: () -> Unit,
    onStartChange: (String) -> Unit,
    onCancelChange: () -> Unit,
    onConfirmChange: (String) -> Unit,
    onStartRoleChange: () -> Unit,
    onCancelRoleChange: () -> Unit,
    onConfirmRoleChange: (role: String, reason: String) -> Unit,
    onDismissMessage: () -> Unit
) {
    // Close the dialog, then the sheet, before leaving the screen.
    BackHandler {
        when {
            state.isSaving -> Unit
            state.pending != null -> onCancelChange()
            state.pendingRole != null -> onCancelRoleChange()
            state.selectedUid != null -> onDismissDetails()
            else -> onBack()
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = ScreenHorizontalPadding
            )
        ) {
            // ---------------------------------------------------------
            // HEADER
            // ---------------------------------------------------------

            item(key = "header") {
                Column {
                    // Back sits in the top spacing so the title lines up
                    // with the dashboard title (ScreenTopSpacing = 96dp).
                    Spacer(Modifier.height(ScreenTopSpacing - 48.dp))

                    TextButton(
                        onClick = onBack,
                        enabled = !state.isSaving,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Admin dashboard")
                    }

                    AdminScreenHeader(title = "Accounts")

                    Spacer(Modifier.height(24.dp))
                }
            }

            state.message?.let { message ->
                item(key = "message") {
                    MessageCard(message = message, onDismiss = onDismissMessage)
                    Spacer(Modifier.height(GridSpacing))
                }
            }

            // ---------------------------------------------------------
            // FILTERS AND SEARCH
            // ---------------------------------------------------------

            item(key = "filters") {
                Column {
                    SectionTitle("Filters")

                    FilterGroup(
                        label = "Role",
                        options = AdminAccountOptions.roles,
                        selected = state.roleFilter,
                        enabled = !state.isSaving,
                        onSelect = onRoleFilter
                    )

                    Spacer(Modifier.height(12.dp))

                    FilterGroup(
                        label = "Status",
                        options = AdminAccountOptions.statuses,
                        selected = state.statusFilter,
                        enabled = !state.isSaving,
                        onSelect = onStatusFilter
                    )

                    Spacer(Modifier.height(GridSpacing))

                    OutlinedTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        label = { Text("Search loaded accounts") },
                        placeholder = { Text("Name or email") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null)
                        },
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

                    Spacer(Modifier.height(24.dp))
                }
            }

            // ---------------------------------------------------------
            // ACCOUNTS
            // ---------------------------------------------------------

            item(key = "list-title") {
                Column {
                    SectionTitle("Accounts")

                    if (state.hasLoaded && state.error == null && !state.isLoading) {
                        Text(
                            text = loadedSummary(state),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }

            val visible = state.visibleAccounts

            when {
                state.isLoading && state.accounts.isEmpty() -> {
                    item(key = "loading") { AccountListSkeleton() }
                }

                state.error != null -> {
                    item(key = "error") {
                        ErrorCard(
                            message = state.error,
                            retryEnabled = !state.isLoading,
                            onRetry = onRefresh
                        )
                    }
                }

                state.hasLoaded && visible.isEmpty() -> {
                    item(key = "empty") {
                        EmptyCard(
                            text = if (state.query.isNotBlank()) {
                                "No loaded accounts match \"${state.query.trim()}\"."
                            } else {
                                "No accounts match these filters."
                            }
                        )
                    }
                }

                else -> {
                    items(visible, key = { it.uid }) { account ->
                        AccountRow(
                            account = account,
                            isSelf = account.uid == state.currentUid,
                            onClick = { onSelect(account.uid) }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            // ---------------------------------------------------------
            // PAGINATION
            // ---------------------------------------------------------

            if (state.error == null && state.nextCursor != null) {
                item(key = "load-more") {
                    LoadMoreFooter(state = state, onLoadMore = onLoadMore)
                }
            }

            item(key = "bottom") {
                Spacer(Modifier.height(QuickExitClearance))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }

    state.selectedAccount?.let { account ->
        AccountDetailsSheet(
            account = account,
            isSaving = state.isSaving,
            onDismiss = onDismissDetails,
            onStartChange = onStartChange,
            onStartRoleChange = onStartRoleChange
        )
    }

    state.pending?.let { pending ->
        StatusChangeDialog(
            pending = pending,
            isSaving = state.isSaving,
            error = state.actionError,
            onCancel = onCancelChange,
            onConfirm = onConfirmChange
        )
    }

    state.pendingRole?.let { pending ->
        RoleChangeDialog(
            pending = pending,
            isSaving = state.isSaving,
            error = state.actionError,
            onCancel = onCancelRoleChange,
            onConfirm = onConfirmRoleChange
        )
    }
}

private fun loadedSummary(state: AdminAccountsUiState): String {
    val loaded = state.accounts.size
    val shown = state.visibleAccounts.size
    val more = if (state.nextCursor != null) " More available." else ""

    return if (state.query.isBlank()) {
        "$loaded loaded.$more"
    } else {
        "Showing $shown of $loaded loaded.$more"
    }
}

// -----------------------------------------------------------------
// FILTERS
// -----------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(
    label: String,
    options: Map<String, String>,
    selected: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterOption("All", selected == null, enabled) { onSelect(null) }

            options.forEach { (value, text) ->
                FilterOption(text, selected == value, enabled) { onSelect(value) }
            }
        }
    }
}

@Composable
private fun FilterOption(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(text) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        } else {
            null
        }
    )
}

// -----------------------------------------------------------------
// ROWS
// -----------------------------------------------------------------

@Composable
private fun AccountRow(
    account: AdminAccount,
    isSelf: Boolean,
    onClick: () -> Unit
) {
    val status = accountStatusStyle(account.accountStatus)
    val role = AdminAccountOptions.roleLabel(account.role)
    val name = account.displayName.ifBlank { "No name" }

    val description = buildString {
        append("$name, ${account.email}, $role, ${status.label}")
        if (isSelf) append(". This is you. Your own account can't be changed here.")
    }

    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (isSelf) {
                    Spacer(Modifier.width(8.dp))
                    YouBadge()
                }
            }

            Text(
                text = account.email.ifBlank { "No email" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = role,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))

                StatusBadge(status)
            }
        }
    }

    if (isSelf) {
        // No actions on your own account, so the row isn't clickable.
        Card(
            shape = CardShape,
            colors = tileColors(),
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { contentDescription = description }
        ) {
            body()
        }
    } else {
        Card(
            onClick = onClick,
            shape = CardShape,
            colors = tileColors(),
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics {
                    contentDescription = "$description. Opens details and actions."
                    // clearAndSetSemantics drops the click action; add it back.
                    semanticsOnClick(label = "Open details") {
                        onClick()
                        true
                    }
                }
        ) {
            body()
        }
    }
}

@Composable
private fun YouBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    ) {
        Text(
            text = "You",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun AccountListSkeleton() {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Loading accounts"
        }
    ) {
        repeat(3) {
            Card(
                shape = CardShape,
                colors = tileColors(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(CardPadding),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SkeletonBlock(Modifier.width(140.dp).height(18.dp))
                    SkeletonBlock(Modifier.width(200.dp).height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SkeletonBlock(Modifier.width(56.dp).height(14.dp))
                        SkeletonBlock(Modifier.width(88.dp).height(24.dp), RoundedCornerShape(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        shape = CardShape,
        colors = tileColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(CardPadding)
        )
    }
}

@Composable
private fun MessageCard(message: String, onDismiss: () -> Unit) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite }
    ) {
        Row(
            modifier = Modifier.padding(start = CardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = "Dismiss message")
            }
        }
    }
}

@Composable
private fun LoadMoreFooter(
    state: AdminAccountsUiState,
    onLoadMore: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.query.isNotBlank()) {
            Text(
                text = "Search only covers loaded accounts. Load more to search further.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        state.loadMoreError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (state.isLoadingMore) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(32.dp)
                    .semantics { contentDescription = "Loading more accounts" }
            )
        } else {
            OutlinedButton(
                onClick = onLoadMore,
                enabled = !state.isLoading && !state.isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(if (state.loadMoreError != null) "Retry loading more" else "Load more")
            }
        }
    }
}

// -----------------------------------------------------------------
// DETAILS SHEET
// -----------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountDetailsSheet(
    account: AdminAccount,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onStartChange: (String) -> Unit,
    onStartRoleChange: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        AccountDetailsContent(
            account = account,
            isSaving = isSaving,
            onStartChange = onStartChange,
            onStartRoleChange = onStartRoleChange
        )
    }
}

/** Sheet body, separate from the sheet so it can be previewed. */
@Composable
private fun AccountDetailsContent(
    account: AdminAccount,
    isSaving: Boolean,
    onStartChange: (String) -> Unit,
    onStartRoleChange: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenHorizontalPadding)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = account.displayName.ifBlank { "No name" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = account.email.ifBlank { "No email" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = AdminAccountOptions.roleLabel(account.role),
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.weight(1f))
            StatusBadge(accountStatusStyle(account.accountStatus))
        }

        DetailLine("Created", formatDate(account.createdAtMillis))
        DetailLine("Status last changed", formatDate(account.statusUpdatedAtMillis))
        DetailLine("Role last changed", formatDate(account.roleUpdatedAtMillis))

        // ---------------- account status ----------------

        HorizontalDivider()

        SheetSectionTitle("Account status")

        AdminAccountsLogic.availableTargets(account.accountStatus).forEach { target ->
            StatusActionButton(
                target = target,
                enabled = !isSaving,
                onClick = { onStartChange(target) }
            )
        }

        // ---------------- role ----------------

        HorizontalDivider()

        SheetSectionTitle("Role")

        val isAdmin = account.role == "admin"
        val isActive = account.accountStatus == "active"

        Text(
            text = when {
                isAdmin ->
                    "Admins can manage accounts and roles, regional resources " +
                            "and emergency guidance."
                isActive ->
                    "Making this account an admin gives it full admin access. " +
                            "Any support connections must be unlinked before its " +
                            "role can change."
                else ->
                    "Only active accounts can be made admins. Reactivate this " +
                            "account first."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedButton(
            onClick = onStartRoleChange,
            enabled = !isSaving && (isAdmin || isActive),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Text(if (isAdmin) "Remove admin rights" else "Make admin")
        }

        Text(
            text = "Each change asks for a reason and is recorded in the admin audit log.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun SheetSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.semantics { heading() }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(12.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun actionLabel(target: String): String = when (target) {
    "active" -> "Reactivate"
    "suspended" -> "Suspend"
    else -> "Disable"
}

@Composable
private fun StatusActionButton(
    target: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 48.dp)

    when (target) {
        "active" -> Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(actionLabel(target))
        }

        "suspended" -> FilledTonalButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(actionLabel(target))
        }

        else -> Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text(actionLabel(target))
        }
    }
}

// -----------------------------------------------------------------
// CONFIRMATION DIALOG
// -----------------------------------------------------------------

private fun effectText(target: String, name: String): String {
    val sessions = "If they're signed in right now, they'll be signed out within " +
            "about an hour, when their current session expires. Their data isn't deleted."

    return when (target) {
        "active" ->
            "$name will be able to sign in again with their existing email and " +
                    "password, and their account status will be Active."

        "suspended" ->
            "$name won't be able to sign in or use the app while suspended. " +
                    "Use this for a temporary hold. You can reactivate the account later.\n\n$sessions"

        else ->
            "$name won't be able to sign in or use the app. Use this for an account " +
                    "that should stay closed. It can still be reactivated later.\n\n$sessions"
    }
}

@Composable
internal fun StatusChangeDialog(
    pending: PendingStatusChange,
    isSaving: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // Kept per change, so a retry after an error reuses the same reason.
    var reason by rememberSaveable(pending.account.uid, pending.toStatus) {
        mutableStateOf("")
    }
    var touched by rememberSaveable(pending.account.uid, pending.toStatus) {
        mutableStateOf(false)
    }

    val valid = AdminAccountsLogic.isValidReason(reason)
    val name = pending.account.displayName.ifBlank { pending.account.email }
    val action = actionLabel(pending.toStatus)

    AlertDialog(
        onDismissRequest = { if (!isSaving) onCancel() },
        title = { Text("$action account?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(effectText(pending.toStatus, name))

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it.take(AdminAccountOptions.REASON_MAX + 50)
                        touched = true
                    },
                    label = { Text("Reason (required)") },
                    enabled = !isSaving,
                    isError = touched && !valid,
                    minLines = 3,
                    maxLines = 6,
                    supportingText = {
                        Text(
                            "${reason.trim().length}/${AdminAccountOptions.REASON_MAX} · " +
                                    "At least ${AdminAccountOptions.REASON_MIN} characters. " +
                                    "Saved in the admin audit log."
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason) },
                enabled = valid && !isSaving,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .semantics { contentDescription = "Saving" },
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (error != null) "Try again" else action)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isSaving,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

// -----------------------------------------------------------------
// ROLE DIALOG
// -----------------------------------------------------------------

private fun roleEffectText(pending: PendingRoleChange, toRole: String, name: String): String =
    if (pending.isPromotion) {
        "$name will get full admin access, including account management. " +
                "They'll be able to see every account, suspend, disable or " +
                "reactivate accounts, change roles (including other admins'), " +
                "and edit regional resources and emergency guidance.\n\n" +
                "They'll see the admin dashboard the next time they open the app.\n\n" +
                "If the account has any support connections, including pending " +
                "or declined requests, they must be unlinked before its role " +
                "can change."
    } else {
        "$name will lose admin access right away. Admin tools stop working " +
                "for them on their next action, even if the app is open. " +
                "They'll see the ${AdminAccountOptions.roleLabel(toRole)} " +
                "dashboard the next time they open the app.\n\n" +
                "Their account status and data don't change."
    }

@Composable
internal fun RoleChangeDialog(
    pending: PendingRoleChange,
    isSaving: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onConfirm: (role: String, reason: String) -> Unit
) {
    // Kept per change, so a retry after an error reuses the same input.
    var reason by rememberSaveable(pending.account.uid, pending.isPromotion) {
        mutableStateOf("")
    }
    var touched by rememberSaveable(pending.account.uid, pending.isPromotion) {
        mutableStateOf(false)
    }
    var toRole by rememberSaveable(pending.account.uid, pending.isPromotion) {
        mutableStateOf(pending.choices.first())
    }

    val valid = AdminAccountsLogic.isValidReason(reason)
    val name = pending.account.displayName.ifBlank { pending.account.email }
    val action = if (pending.isPromotion) "Make admin" else "Remove admin rights"

    AlertDialog(
        onDismissRequest = { if (!isSaving) onCancel() },
        title = {
            Text(if (pending.isPromotion) "Make $name an admin?" else "Remove admin rights?")
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(roleEffectText(pending, toRole, name))

                if (!pending.isPromotion) {
                    Text(
                        text = "New role",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.semantics { heading() }
                    )

                    Column(Modifier.selectableGroup()) {
                        pending.choices.forEach { choice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .selectable(
                                        selected = toRole == choice,
                                        enabled = !isSaving,
                                        role = Role.RadioButton,
                                        onClick = { toRole = choice }
                                    ),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = toRole == choice,
                                    onClick = null,
                                    enabled = !isSaving
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(AdminAccountOptions.roleLabel(choice))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it.take(AdminAccountOptions.REASON_MAX + 50)
                        touched = true
                    },
                    label = { Text("Reason (required)") },
                    enabled = !isSaving,
                    isError = touched && !valid,
                    minLines = 3,
                    maxLines = 6,
                    supportingText = {
                        Text(
                            "${reason.trim().length}/${AdminAccountOptions.REASON_MAX} · " +
                                    "At least ${AdminAccountOptions.REASON_MIN} characters. " +
                                    "Saved in the admin audit log."
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Backend messages explain support links, the last admin, or
                // an unchanged role.
                error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(toRole, reason) },
                enabled = valid && !isSaving,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(18.dp)
                            .semantics { contentDescription = "Saving" },
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(if (error != null) "Try again" else action)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                enabled = !isSaving,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Cancel")
            }
        }
    )
}

private fun formatDate(millis: Long?): String =
    millis?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))
    } ?: "Not recorded"

// -----------------------------------------------------------------
// PREVIEWS (sample data for the design preview only)
// -----------------------------------------------------------------

private val previewAccounts = listOf(
    AdminAccount("admin-1", "Priya Admin", "priya@example.com", "admin", "active", 1_760_000_000_000, null),
    AdminAccount("admin-2", "Casey Admin", "casey@example.com", "admin", "active", 1_761_000_000_000, null, 1_792_000_000_000),
    AdminAccount("user-1", "Alex Morgan", "alex@example.com", "user", "active", 1_765_000_000_000, null),
    AdminAccount("support-1", "Sam Rivera", "sam.rivera@example.org", "support", "suspended", 1_766_000_000_000, 1_790_000_000_000),
    AdminAccount("user-2", "Jordan Lee", "jordan@example.com", "user", "disabled", 1_767_000_000_000, 1_791_000_000_000),
    AdminAccount("user-3", "", "no-name@example.com", "user", "inactive", null, null)
)

@Composable
private fun PreviewContent(state: AdminAccountsUiState) {
    AndroidAppTheme(dynamicColor = false) {
        AdminAccountsContent(
            state = state,
            onBack = {}, onRefresh = {}, onLoadMore = {},
            onRoleFilter = {}, onStatusFilter = {}, onQueryChange = {},
            onSelect = {}, onDismissDetails = {}, onStartChange = {},
            onCancelChange = {}, onConfirmChange = {},
            onStartRoleChange = {}, onCancelRoleChange = {}, onConfirmRoleChange = { _, _ -> },
            onDismissMessage = {}
        )
    }
}

@Preview(name = "Loaded", showBackground = true, widthDp = 412, heightDp = 1600)
@Composable
private fun AdminAccountsLoadedPreview() {
    PreviewContent(
        AdminAccountsUiState(
            currentUid = "admin-1",
            accounts = previewAccounts,
            nextCursor = "user-3",
            hasLoaded = true,
            message = "Casey Admin's admin rights were removed. They now have the User role."
        )
    )
}

@Preview(name = "Filtered + search, large font", showBackground = true, widthDp = 412, heightDp = 1800, fontScale = 2f)
@Composable
private fun AdminAccountsLargeFontPreview() {
    PreviewContent(
        AdminAccountsUiState(
            currentUid = "admin-1",
            roleFilter = "user",
            query = "alex",
            accounts = previewAccounts.filter { it.role == "user" },
            nextCursor = "user-3",
            hasLoaded = true
        )
    )
}

@Preview(name = "Loading", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun AdminAccountsLoadingPreview() {
    PreviewContent(AdminAccountsUiState(isLoading = true))
}

@Preview(name = "Error", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun AdminAccountsErrorPreview() {
    PreviewContent(
        AdminAccountsUiState(
            error = "The service could not be reached. Check your connection and try again."
        )
    )
}

@Preview(name = "Empty", showBackground = true, widthDp = 412, heightDp = 1100)
@Composable
private fun AdminAccountsEmptyPreview() {
    PreviewContent(
        AdminAccountsUiState(statusFilter = "disabled", hasLoaded = true)
    )
}

@Preview(name = "Confirm suspend", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
private fun StatusChangeDialogPreview() {
    AndroidAppTheme(dynamicColor = false) {
        StatusChangeDialog(
            pending = PendingStatusChange(previewAccounts[2], "suspended"),
            isSaving = false,
            error = null,
            onCancel = {},
            onConfirm = {}
        )
    }
}

@Preview(name = "Confirm, sign-in retry needed", showBackground = true, widthDp = 412, heightDp = 800)
@Composable
private fun StatusChangeDialogRetryPreview() {
    AndroidAppTheme(dynamicColor = false) {
        StatusChangeDialog(
            pending = PendingStatusChange(previewAccounts[4], "disabled"),
            isSaving = false,
            error = "The account status was saved, but updating sign-in access failed. " +
                    "Try the same change again to finish it.",
            onCancel = {},
            onConfirm = {}
        )
    }
}

@Preview(name = "Details, user account", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun AccountDetailsUserPreview() {
    AndroidAppTheme(dynamicColor = false) {
        Surface {
            AccountDetailsContent(
                account = previewAccounts[2],
                isSaving = false,
                onStartChange = {},
                onStartRoleChange = {}
            )
        }
    }
}

@Preview(name = "Details, admin account", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun AccountDetailsAdminPreview() {
    AndroidAppTheme(dynamicColor = false) {
        Surface {
            AccountDetailsContent(
                account = previewAccounts[1],
                isSaving = false,
                onStartChange = {},
                onStartRoleChange = {}
            )
        }
    }
}

@Preview(name = "Details, suspended support account", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun AccountDetailsSuspendedPreview() {
    AndroidAppTheme(dynamicColor = false) {
        Surface {
            AccountDetailsContent(
                account = previewAccounts[3],
                isSaving = false,
                onStartChange = {},
                onStartRoleChange = {}
            )
        }
    }
}

@Preview(name = "Make admin", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun RoleChangePromotePreview() {
    AndroidAppTheme(dynamicColor = false) {
        RoleChangeDialog(
            pending = PendingRoleChange(previewAccounts[2], listOf("admin")),
            isSaving = false,
            error = null,
            onCancel = {},
            onConfirm = { _, _ -> }
        )
    }
}

@Preview(name = "Make admin, support links exist", showBackground = true, widthDp = 412, heightDp = 900)
@Composable
private fun RoleChangeLinksErrorPreview() {
    AndroidAppTheme(dynamicColor = false) {
        RoleChangeDialog(
            pending = PendingRoleChange(previewAccounts[2], listOf("admin")),
            isSaving = false,
            error = "This account has support connections (including pending or declined " +
                    "requests). They must be unlinked before its role can change.",
            onCancel = {},
            onConfirm = { _, _ -> }
        )
    }
}

@Preview(name = "Remove admin rights, last admin", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun RoleChangeDemotePreview() {
    AndroidAppTheme(dynamicColor = false) {
        RoleChangeDialog(
            pending = PendingRoleChange(previewAccounts[1], listOf("user", "support")),
            isSaving = false,
            error = "This is the last active administrator. Make another account an admin " +
                    "before removing these admin rights.",
            onCancel = {},
            onConfirm = { _, _ -> }
        )
    }
}
