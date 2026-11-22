package com.example.androidapp.pages.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat

// Shared look for the admin screens (AdminDashboardPage, AdminAccountsPage).
// Layout values match DashboardPage (pages/dashboardpage/dashboard.kt) and
// SupportDashboardPage so all role dashboards line up.

internal val ScreenHorizontalPadding = 24.dp
internal val ScreenTopSpacing = 96.dp
internal val GridSpacing = 16.dp
internal val CardShape = RoundedCornerShape(20.dp)
internal val IconContainerShape = RoundedCornerShape(14.dp)
internal val CardPadding = 16.dp

// Keeps the last row clear of the shared floating Quick Exit button
// (48dp tall, 24dp from the bottom, drawn by AppNavigation).
internal val QuickExitClearance = 96.dp

// -----------------------------------------------------------------
// HEADER
// -----------------------------------------------------------------

/** Title in the shared dashboard style, the "Administrator" chip, and a trailing action. */
@Composable
internal fun AdminScreenHeader(
    title: String,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Same title treatment as DashboardPage and SupportDashboardPage.
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )

            RoleChip(text = "Administrator")
        }

        trailing()
    }
}

@Composable
internal fun RoleChip(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.clearAndSetSemantics {
            contentDescription = "Role: $text"
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.AdminPanelSettings,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier
            .padding(bottom = 12.dp)
            .semantics { heading() }
    )
}

// -----------------------------------------------------------------
// CARDS
// -----------------------------------------------------------------

/** Two equal-width columns whose cards stretch to the taller one's height. */
@Composable
internal fun TwoColumnRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        content = content
    )
}

/** Icon block used by DashboardTile: primaryContainer, 14dp corners, 26dp icon. */
@Composable
internal fun IconBlock(icon: ImageVector) {
    Surface(
        shape = IconContainerShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Icon(
            imageVector = icon,
            // Decorative: the tile's own content description names it.
            contentDescription = null,
            modifier = Modifier
                .padding(10.dp)
                .size(26.dp)
        )
    }
}

@Composable
internal fun tileColors() = CardDefaults.cardColors(
    containerColor = MaterialTheme.colorScheme.surfaceVariant,
    contentColor = MaterialTheme.colorScheme.onSurface
)

@Composable
internal fun SkeletonBlock(
    modifier: Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(6.dp)
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))
    )
}

@Composable
internal fun ErrorCard(
    message: String,
    retryEnabled: Boolean,
    onRetry: () -> Unit
) {
    Card(
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(CardPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = "Error",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Button(
                onClick = onRetry,
                enabled = retryEnabled,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Retry")
            }
        }
    }
}

// -----------------------------------------------------------------
// COUNTS
// -----------------------------------------------------------------

/**
 * Shared count style for the stat tiles and the account status rows, with
 * tabular figures ("tnum") so digits line up vertically. Both sit inside
 * CardPadding, so they share the same end padding too.
 */
@Composable
internal fun CountText(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontWeight = FontWeight.Bold,
            fontFeatureSettings = "tnum"
        ),
        maxLines = 1,
        softWrap = false,
        modifier = modifier
    )
}

internal fun formatCount(value: Long): String =
    NumberFormat.getIntegerInstance().format(value)

// -----------------------------------------------------------------
// ACCOUNT STATUS BADGES
// -----------------------------------------------------------------

internal data class AccountStatusStyle(
    val label: String,
    val badgeColor: Color,
    val onBadgeColor: Color,
    val barColor: Color
)

/**
 * One colour mapping for account statuses, used by the dashboard's status
 * card and the accounts list. Colour roles come from the app theme only.
 */
@Composable
internal fun accountStatusStyle(status: String?): AccountStatusStyle {
    val colors = MaterialTheme.colorScheme

    return when (status) {
        "active" -> AccountStatusStyle(
            "Active", colors.primaryContainer, colors.onPrimaryContainer, colors.primary
        )
        "inactive" -> AccountStatusStyle(
            "Inactive", colors.secondaryContainer, colors.onSecondaryContainer, colors.secondary
        )
        "suspended" -> AccountStatusStyle(
            "Suspended", colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary
        )
        "disabled" -> AccountStatusStyle(
            "Disabled", colors.errorContainer, colors.onErrorContainer, colors.error
        )
        else -> AccountStatusStyle(
            "Other / unspecified", colors.surface, colors.onSurface, colors.outline
        )
    }
}

/** Pill badge with a coloured dot. The width cap lets long labels wrap at large font sizes. */
@Composable
internal fun StatusBadge(
    style: AccountStatusStyle,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = style.badgeColor,
        contentColor = style.onBadgeColor,
        modifier = modifier.widthIn(max = 200.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(style.barColor)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = style.label,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
