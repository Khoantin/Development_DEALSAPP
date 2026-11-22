package com.example.androidapp.pages.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.androidapp.data.AdminDashboardStats
import com.example.androidapp.data.AdminEmergencyStats
import com.example.androidapp.pages.staffmessaging.UnreadBadge
import com.example.androidapp.pages.staffmessaging.unreadDescription
import com.example.androidapp.ui.theme.AndroidAppTheme
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

// Shared layout values (ScreenHorizontalPadding, CardShape, ...) live in AdminUi.kt.
private val ToolCardMinHeight = 172.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardPage(
    state: AdminDashboardUiState,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onManageResources: () -> Unit,
    onManageGuidance: () -> Unit = {},
    onManageAccounts: () -> Unit = {},
    onStaffMessages: () -> Unit = {},
    staffUnread: Int = 0,
    emergencyState: AdminEmergencyUiState = AdminEmergencyUiState(),
    onRetryEmergency: () -> Unit = {}
) {
    var showSignOutDialog by rememberSaveable { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = state.isLoading || emergencyState.isLoading,
        onRefresh = onRefresh,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenHorizontalPadding)
        ) {
            Spacer(Modifier.height(ScreenTopSpacing))

            AdminHeader(onSignOutClick = { showSignOutDialog = true })

            Spacer(Modifier.height(24.dp))

            // -------------------------------------------------
            // OVERVIEW
            // -------------------------------------------------

            SectionTitle("Overview")

            val stats = state.stats

            when {
                stats != null -> OverviewGrid(stats)

                state.errorMessage != null -> ErrorCard(
                    message = state.errorMessage,
                    retryEnabled = !state.isLoading,
                    onRetry = onRefresh
                )

                else -> StatTilesSkeleton("Loading account statistics")
            }

            Spacer(Modifier.height(24.dp))

            // -------------------------------------------------
            // EMERGENCY ACTIVITY (anonymous counts only)
            // -------------------------------------------------

            SectionTitle("Emergency activity")

            val emergency = emergencyState.stats

            when {
                emergency != null -> EmergencyActivity(emergency)

                emergencyState.errorMessage != null -> ErrorCard(
                    message = emergencyState.errorMessage,
                    retryEnabled = !emergencyState.isLoading,
                    onRetry = onRetryEmergency
                )

                else -> StatTilesSkeleton("Loading emergency activity")
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Counts only. Administrators can't see who sent an alert " +
                    "or where they are.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(
                onClick = onManageGuidance,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.HealthAndSafety,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text("Emergency guidance")
            }

            Spacer(Modifier.height(24.dp))

            // -------------------------------------------------
            // ADMIN TOOLS
            // -------------------------------------------------

            SectionTitle("Admin tools")

            TwoColumnRow {
                AdminToolCard(
                    title = "Regional resources",
                    description = "Edit published support listings",
                    icon = Icons.Outlined.Map,
                    onClick = onManageResources,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                AdminToolCard(
                    title = "Emergency guidance",
                    description = "Write, review and publish guides",
                    icon = Icons.Outlined.HealthAndSafety,
                    onClick = onManageGuidance,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(Modifier.height(GridSpacing))

            TwoColumnRow {
                AdminToolCard(
                    title = "Accounts",
                    description = "Review and manage accounts",
                    icon = Icons.Outlined.ManageAccounts,
                    onClick = onManageAccounts,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )

                AdminToolCard(
                    title = "Staff messages",
                    description = "Message support staff",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    onClick = onStaffMessages,
                    badgeCount = staffUnread,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                )
            }

            Spacer(Modifier.height(24.dp))

            // -------------------------------------------------
            // ACCOUNT STATUS
            // -------------------------------------------------

            SectionTitle("Account status")

            when {
                stats != null -> AccountStatusCard(stats)

                state.errorMessage != null -> Text(
                    text = "Status counts will appear once statistics load.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                else -> AccountStatusSkeleton()
            }

            if (stats != null) {
                Spacer(Modifier.height(16.dp))

                Text(
                    text = "Last updated " + DateFormat
                        .getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(stats.generatedAtMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(QuickExitClearance))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Logout,
                    contentDescription = null
                )
            },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "This also clears your passcode and app settings on this " +
                        "device. You'll need to sign in again to return."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSignOutDialog = false
                        onLogout()
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Sign out")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignOutDialog = false },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

// -----------------------------------------------------------------
// HEADER
// -----------------------------------------------------------------

@Composable
private fun AdminHeader(onSignOutClick: () -> Unit) {
    AdminScreenHeader(title = "Admin dashboard") {
        TextButton(
            onClick = onSignOutClick,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Sign out")
        }
    }
}

// -----------------------------------------------------------------
// OVERVIEW
// -----------------------------------------------------------------

@Composable
private fun OverviewGrid(stats: AdminDashboardStats) {
    val users = stats.roles["user"] ?: 0L
    val support = stats.roles["support"] ?: 0L
    val admins = stats.roles["admin"] ?: 0L
    val otherRoles = stats.roles["other"] ?: 0L

    TwoColumnRow {
        StatTile(
            label = "Total accounts",
            value = stats.totalAccounts,
            icon = Icons.Outlined.Groups,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        StatTile(
            label = "Users",
            value = users,
            icon = Icons.Outlined.Person,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }

    Spacer(Modifier.height(GridSpacing))

    TwoColumnRow {
        StatTile(
            label = "Support",
            value = support,
            icon = Icons.Outlined.SupportAgent,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        StatTile(
            label = "Admins",
            value = admins,
            icon = Icons.Outlined.AdminPanelSettings,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }

    // The backend also counts profiles with a missing or unknown role.
    // Mention them so the tiles always add up to the total.
    if (otherRoles > 0) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = "${formatCount(otherRoles)} " +
                (if (otherRoles == 1L) "account has" else "accounts have") +
                " another or missing role.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatTile(
    label: String,
    value: Long,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    val formatted = formatCount(value)

    Card(
        shape = CardShape,
        colors = tileColors(),
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "$label: $formatted"
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconBlock(icon)

            Column {
                CountText(text = formatted)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatTilesSkeleton(description: String) {
    Column(
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = description
        }
    ) {
        repeat(2) { row ->
            if (row > 0) Spacer(Modifier.height(GridSpacing))

            TwoColumnRow {
                repeat(2) {
                    Card(
                        shape = CardShape,
                        colors = tileColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(CardPadding),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SkeletonBlock(Modifier.size(46.dp), IconContainerShape)
                            SkeletonBlock(Modifier.width(56.dp).height(28.dp))
                            SkeletonBlock(Modifier.width(88.dp).height(12.dp))
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------
// EMERGENCY ACTIVITY
// -----------------------------------------------------------------

@Composable
private fun EmergencyActivity(stats: AdminEmergencyStats) {
    TwoColumnRow {
        StatTile(
            label = "Active now",
            value = stats.activeNow,
            icon = Icons.Outlined.NotificationsActive,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        StatTile(
            label = "Last 7 days",
            value = stats.startedLast7Days,
            icon = Icons.Outlined.DateRange,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }

    Spacer(Modifier.height(GridSpacing))

    TwoColumnRow {
        StatTile(
            label = "Last 30 days",
            value = stats.startedLast30Days,
            icon = Icons.Outlined.CalendarMonth,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        StatTile(
            label = "Cancelled (30 days)",
            value = stats.cancelledLast30Days,
            icon = Icons.Outlined.Cancel,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }

    Spacer(Modifier.height(GridSpacing))

    Card(
        shape = CardShape,
        colors = tileColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "By trigger, last 30 days",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { heading() }
            )
            TriggerRow("Emergency button", stats.byTrigger["emergency_button"] ?: 0L)
            TriggerRow("Voice duress", stats.byTrigger["voice_duress"] ?: 0L)
        }
    }
}

@Composable
private fun TriggerRow(label: String, count: Long) {
    val formatted = formatCount(count)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .clearAndSetSemantics { contentDescription = "$label: $formatted" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatted,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum"
            ),
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

// -----------------------------------------------------------------
// ADMIN TOOLS
// -----------------------------------------------------------------

/**
 * Mirrors DashboardTile's look. A null [onClick] marks a tool that isn't
 * built yet: the card is dimmed, shows a "Coming soon" badge and isn't clickable.
 */
@Composable
private fun AdminToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0
) {
    val available = onClick != null

    val body: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = ToolCardMinHeight)
                .padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(Modifier.alpha(if (available) 1f else 0.5f)) {
                    IconBlock(icon)
                }

                if (!available) {
                    ComingSoonBadge()
                } else {
                    UnreadBadge(badgeCount)
                }
            }

            Column(Modifier.alpha(if (available) 1f else 0.6f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (onClick != null) {
        Card(
            onClick = onClick,
            shape = CardShape,
            colors = tileColors(),
            modifier = modifier.semantics {
                contentDescription = "$title. $description" +
                    if (badgeCount > 0) ". ${unreadDescription(badgeCount)}" else ""
            }
        ) {
            body()
        }
    } else {
        // Not clickable, so TalkBack reads it as plain content.
        Card(
            shape = CardShape,
            colors = tileColors(),
            modifier = modifier.clearAndSetSemantics {
                contentDescription = "$title. Coming soon, not available yet."
            }
        ) {
            body()
        }
    }
}

@Composable
private fun ComingSoonBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        Text(
            text = "Coming soon",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// -----------------------------------------------------------------
// ACCOUNT STATUS
// -----------------------------------------------------------------

private data class StatusEntry(
    val style: AccountStatusStyle,
    val count: Long
) {
    val label: String get() = style.label
    val barColor: Color get() = style.barColor
}

@Composable
private fun statusEntries(stats: AdminDashboardStats): List<StatusEntry> {
    // Colours come from accountStatusStyle() in AdminUi.kt, shared with the
    // accounts list so a status always looks the same.
    val entries = listOf("active", "inactive", "suspended", "disabled").map { status ->
        StatusEntry(accountStatusStyle(status), stats.statuses[status] ?: 0L)
    }

    val other = stats.statuses["other"] ?: 0L

    return if (other > 0) {
        entries + StatusEntry(accountStatusStyle("other"), other)
    } else {
        entries
    }
}

@Composable
private fun AccountStatusCard(stats: AdminDashboardStats) {
    val entries = statusEntries(stats)
    val total = entries.sumOf { it.count }

    Card(
        shape = CardShape,
        colors = tileColors(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thin proportional bar. Decorative: the rows below read out each count.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .clearAndSetSemantics { }
            ) {
                if (total > 0) {
                    entries.filter { it.count > 0 }.forEach { entry ->
                        Box(
                            modifier = Modifier
                                .weight(entry.count.toFloat())
                                .fillMaxHeight()
                                .background(entry.barColor)
                        )
                    }
                }
            }

            entries.forEach { entry ->
                StatusRow(entry)
            }

            Text(
                text = "Active is an account status, not an online indicator.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun StatusRow(entry: StatusEntry) {
    val formatted = formatCount(entry.count)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "${entry.label}: $formatted"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Badge on the left, flexible space, count flush with the card's
        // inner padding edge. The badge has no weight, so it never shares
        // the free space with the spacer. StatusBadge caps its width so long
        // labels wrap at large font sizes instead of squeezing the count.
        StatusBadge(entry.style)

        Spacer(Modifier.weight(1f))

        CountText(
            text = formatted,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

@Composable
private fun AccountStatusSkeleton() {
    Card(
        shape = CardShape,
        colors = tileColors(),
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "Loading account status"
            }
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBlock(Modifier.fillMaxWidth().height(8.dp), RoundedCornerShape(4.dp))
            repeat(4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SkeletonBlock(Modifier.width(96.dp).height(24.dp), RoundedCornerShape(12.dp))
                    SkeletonBlock(Modifier.width(32.dp).height(20.dp))
                }
            }
        }
    }
}


// -----------------------------------------------------------------
// PREVIEWS (sample data for the design preview only)
// -----------------------------------------------------------------

private val previewStats = AdminDashboardStats(
    totalAccounts = 42,
    roles = mapOf("user" to 31L, "support" to 8L, "admin" to 2L, "other" to 1L),
    statuses = mapOf(
        "active" to 35L,
        "inactive" to 3L,
        "suspended" to 2L,
        "disabled" to 1L,
        "other" to 1L
    ),
    generatedAtMillis = 1_795_000_000_000L
)

private val previewEmergency = AdminEmergencyStats(
    activeNow = 2,
    startedLast7Days = 5,
    startedLast30Days = 14,
    cancelledLast30Days = 4,
    byTrigger = mapOf("emergency_button" to 9L, "voice_duress" to 5L),
    generatedAtMillis = 1_795_000_000_000L
)

@Preview(name = "Loaded", showBackground = true, widthDp = 412, heightDp = 1500)
@Composable
private fun AdminDashboardLoadedPreview() {
    AndroidAppTheme(dynamicColor = false) {
        AdminDashboardPage(
            state = AdminDashboardUiState(stats = previewStats),
            onRefresh = {},
            onLogout = {},
            onManageResources = {},
            onManageGuidance = {},
            staffUnread = 3,
            emergencyState = AdminEmergencyUiState(stats = previewEmergency)
        )
    }
}

@Preview(
    name = "Loaded, large font",
    showBackground = true,
    widthDp = 412,
    heightDp = 2000,
    fontScale = 2f
)
@Composable
private fun AdminDashboardLargeFontPreview() {
    AndroidAppTheme(dynamicColor = false) {
        AdminDashboardPage(
            state = AdminDashboardUiState(stats = previewStats),
            onRefresh = {},
            onLogout = {},
            onManageResources = {},
            onManageGuidance = {},
            emergencyState = AdminEmergencyUiState(stats = previewEmergency)
        )
    }
}

@Preview(name = "Loading", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
private fun AdminDashboardLoadingPreview() {
    AndroidAppTheme(dynamicColor = false) {
        AdminDashboardPage(
            state = AdminDashboardUiState(isLoading = true),
            onRefresh = {},
            onLogout = {},
            onManageResources = {},
            emergencyState = AdminEmergencyUiState(isLoading = true)
        )
    }
}

@Preview(name = "Error", showBackground = true, widthDp = 412, heightDp = 1200)
@Composable
private fun AdminDashboardErrorPreview() {
    AndroidAppTheme(dynamicColor = false) {
        AdminDashboardPage(
            state = AdminDashboardUiState(
                errorMessage = "Unable to load dashboard statistics. Please try again."
            ),
            onRefresh = {},
            onLogout = {},
            onManageResources = {},
            emergencyState = AdminEmergencyUiState(
                errorMessage = "Unable to load emergency activity. Please try again."
            )
        )
    }
}

// Account stats loaded, emergency stats failed: each section has its own state.
@Preview(name = "Emergency error only", showBackground = true, widthDp = 412, heightDp = 1500)
@Composable
private fun AdminDashboardEmergencyErrorPreview() {
    AndroidAppTheme(dynamicColor = false) {
        AdminDashboardPage(
            state = AdminDashboardUiState(stats = previewStats),
            onRefresh = {},
            onLogout = {},
            onManageResources = {},
            emergencyState = AdminEmergencyUiState(
                errorMessage = "Unable to load emergency activity. Please try again."
            )
        )
    }
}
