
package com.example.androidapp.pages.supportdashboard
import androidx.compose.material3.OutlinedButton

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.androidapp.pages.staffmessaging.UnreadBadge
import com.example.androidapp.ui.theme.AndroidAppTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.androidapp.pages.dashboardpage.DashboardTile
@Composable
fun SupportDashboardPage(
    userName: String,
    onSettingsClick: () -> Unit,
    onQuickExit: () -> Unit,
    onLinkedUsersClick: () -> Unit = {},
    onMessagesClick: () -> Unit = {},
    onDocumentsClick: () -> Unit = {},
    onGuidanceClick: () -> Unit = {},
    onStaffMessagesClick: () -> Unit = {},
    staffUnread: Int = 0
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {

            Spacer(Modifier.height(96.dp))

            // DASHBOARD TITLE

            Text(
                text = if (userName.isBlank()) {
                    "Support dashboard"
                } else {
                    "$userName's dashboard"
                },
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            // FIRST ROW: LINKED USERS + MESSAGES

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(16.dp)
            ) {

                DashboardTile(
                    title = "Linked Users",
                    subtitle = "Your trusted connections",
                    icon = Icons.Outlined.PeopleOutline,
                    modifier = Modifier.weight(1f),
                    onClick = onLinkedUsersClick
                )

                DashboardTile(
                    title = "Messages",
                    subtitle = "Stay connected",
                    icon = Icons.Outlined.ChatBubbleOutline,
                    modifier = Modifier.weight(1f),
                    onClick = onMessagesClick
                )
            }

            Spacer(Modifier.height(16.dp))

            // SECOND ROW: DOCUMENTS + SETTINGS

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(16.dp)
            ) {

                DashboardTile(
                    title = "Shared Documents",
                    subtitle = "Files shared with you",
                    icon = Icons.Outlined.FolderOpen,
                    modifier = Modifier.weight(1f),
                    onClick = onDocumentsClick
                )

                DashboardTile(
                    title = "Settings",
                    subtitle = "Manage settings",
                    icon = Icons.Outlined.Settings,
                    modifier = Modifier.weight(1f),
                    onClick = onSettingsClick
                )
            }
            Spacer(Modifier.height(16.dp))

            // THIRD ROW: STAFF MESSAGES (half width, like the tiles above)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(16.dp)
            ) {

                Box(modifier = Modifier.weight(1f)) {
                    DashboardTile(
                        title = "Message administration",
                        subtitle = "Contact the admin team",
                        icon = Icons.Outlined.SupportAgent,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onStaffMessagesClick
                    )

                    UnreadBadge(
                        count = staffUnread,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )
                }

                Spacer(Modifier.weight(1f))
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = onGuidanceClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Emergency response guidance")
            }

            // Clear of the shared floating Quick Exit button.
            Spacer(Modifier.height(96.dp))
        }
    }
}


@Preview(name = "Support dashboard, unread staff messages", showBackground = true, widthDp = 412, heightDp = 1000)
@Composable
private fun SupportDashboardStaffUnreadPreview() {
    AndroidAppTheme(dynamicColor = false) {
        SupportDashboardPage(
            userName = "Sam",
            onSettingsClick = {},
            onQuickExit = {},
            staffUnread = 2
        )
    }
}
