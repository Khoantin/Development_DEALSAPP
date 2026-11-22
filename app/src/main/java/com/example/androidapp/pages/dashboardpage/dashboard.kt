package com.example.androidapp.pages.dashboardpage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidapp.pages.quickexit.ExitButtonViewModel
import com.example.androidapp.pages.quickexit.QuickExitButton
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.ui.theme.EmergencyRed


@Preview
@Composable
fun DashboardPage(
    modifier: Modifier = Modifier,
    exitButtonViewModel: ExitButtonViewModel = viewModel(),
    onSettingsClick: () -> Unit = {},
    onNotesClick: () -> Unit = {},
    onMessagesClick: () -> Unit = {},
    onContactsClick: () -> Unit = {},
    onResourcesClick: () -> Unit = {},
    onDocumentsClick: () -> Unit = {},
    onLocationSharingClick: () -> Unit = {},
    onIncomingLocationSharingClick: () -> Unit = {},
    onEmergencyClick: () -> Unit = {},
    onQuickExit: () -> Unit = {},
    userName: String = ""
) {

    LaunchedEffect(exitButtonViewModel.quickExitRequested) {

        if (exitButtonViewModel.quickExitRequested) {

            onQuickExit()

            exitButtonViewModel
                .clearQuickExitRequest()
        }
    }


    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
    ) {

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(
                    horizontal = 24.dp
                )
        ) {

            Spacer(
                modifier =
                    Modifier.height(96.dp)
            )


            Text(
                text =
                    if (userName.isBlank()) {

                        "Your dashboard"

                    } else {

                        "$userName's dashboard"
                    },

                fontSize = 20.sp,

                fontWeight =
                    FontWeight.Bold,

                color =
                    MaterialTheme
                        .colorScheme
                        .primary
            )


            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            // -------------------------------------------------
            // EMERGENCY
            // -------------------------------------------------

            Button(
                onClick =
                    onEmergencyClick,

                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(
                        min = 64.dp
                    ),

                shape =
                    RoundedCornerShape(
                        20.dp
                    ),

                colors =
                    ButtonDefaults.buttonColors(

                        containerColor =
                            EmergencyRed,

                        contentColor =
                            MaterialTheme
                                .colorScheme
                                .onError
                    ),

                contentPadding =
                    PaddingValues(

                        horizontal =
                            24.dp,

                        vertical =
                            16.dp
                    )
            ) {

                Icon(
                    imageVector =
                        Icons.Outlined.WarningAmber,

                    contentDescription =
                        null,

                    modifier =
                        Modifier.size(
                            28.dp
                        )
                )


                Spacer(
                    modifier =
                        Modifier.width(
                            12.dp
                        )
                )


                Text(
                    text =
                        "Emergency",

                    fontSize =
                        20.sp,

                    fontWeight =
                        FontWeight.Bold
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )


            // -------------------------------------------------
            // NOTES + MESSAGES
            // -------------------------------------------------

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        16.dp
                    )
            ) {

                DashboardTile(
                    title =
                        "Notes",

                    subtitle =
                        "Notes and incidents",

                    icon =
                        Icons.Outlined.EditNote,

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    onClick =
                        onNotesClick
                )


                DashboardTile(
                    title =
                        "Messages",

                    subtitle =
                        "Stay connected",

                    icon =
                        Icons.Outlined.ChatBubbleOutline,

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    onClick =
                        onMessagesClick
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )


            // -------------------------------------------------
            // CONTACTS + RESOURCES
            // -------------------------------------------------

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        16.dp
                    )
            ) {

                DashboardTile(
                    title =
                        "Contacts",

                    subtitle =
                        "Your trusted people",

                    icon =
                        Icons.Outlined.PeopleOutline,

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    onClick =
                        onContactsClick
                )


                DashboardTile(
                    title =
                        "Resources",

                    subtitle =
                        "Find local support",

                    icon =
                        Icons.Outlined.MenuBook,

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    onClick =
                        onResourcesClick
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )


            // -------------------------------------------------
            // DOCUMENTS + SETTINGS
            // -------------------------------------------------

            Row(
                modifier =
                    Modifier.fillMaxWidth(),

                horizontalArrangement =
                    Arrangement.spacedBy(
                        16.dp
                    )
            ) {

                DashboardTile(
                    title =
                        "Documents",

                    subtitle =
                        "Files and sharing",

                    icon =
                        Icons.Outlined.FolderOpen,

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    onClick =
                        onDocumentsClick
                )


                DashboardTile(
                    title =
                        "Settings",

                    subtitle =
                        "Manage settings",

                    icon =
                        Icons.Outlined.Settings,

                    modifier =
                        Modifier.weight(
                            1f
                        ),

                    onClick =
                        onSettingsClick
                )
            }


            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )


            // -------------------------------------------------
            // LOCATION SHARING
            // -------------------------------------------------

            DashboardTile(
                title =
                    "Location Sharing",

                subtitle =
                    "Choose who can see your location",

                icon =
                    Icons.Outlined.PeopleOutline,

                modifier =
                    Modifier.fillMaxWidth(),

                onClick =
                    onLocationSharingClick
            )


            Spacer(
                modifier =
                    Modifier.height(
                        16.dp
                    )
            )


            // -------------------------------------------------
            // INCOMING LOCATION SHARES
            // -------------------------------------------------

            DashboardTile(
                title =
                    "Incoming Locations",

                subtitle =
                    "View locations shared with you",

                icon =
                    Icons.Filled.LocationOn,

                modifier =
                    Modifier.fillMaxWidth(),

                onClick =
                    onIncomingLocationSharingClick
            )








            Spacer(
                modifier =
                    Modifier.height(
                        24.dp
                    )
            )
        }
    }
}


@Composable
internal fun DashboardTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    Card(
        onClick =
            onClick,

        modifier =
            modifier,

        shape =
            RoundedCornerShape(
                20.dp
            ),

        colors =
            CardDefaults
                .cardColors(

                    containerColor =
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant,

                    contentColor =
                        MaterialTheme
                            .colorScheme
                            .onSurface
                )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    min = 172.dp
                )
                .padding(
                    16.dp
                ),

            verticalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {

            Surface(
                shape =
                    RoundedCornerShape(
                        14.dp
                    ),

                color =
                    MaterialTheme
                        .colorScheme
                        .primaryContainer,

                contentColor =
                    MaterialTheme
                        .colorScheme
                        .onPrimaryContainer
            ) {

                Icon(
                    imageVector =
                        icon,

                    contentDescription =
                        null,

                    modifier =
                        Modifier
                            .padding(
                                10.dp
                            )
                            .size(
                                26.dp
                            )
                )
            }


            Column {

                Text(
                    text =
                        title,

                    fontSize =
                        16.sp,

                    fontWeight =
                        FontWeight.SemiBold
                )


                Spacer(
                    modifier =
                        Modifier.height(
                            4.dp
                        )
                )


                Text(
                    text =
                        subtitle,

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,

                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }
        }
    }
}


@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 892
)
@Composable
fun DashboardPagePreview() {

    AndroidAppTheme {

        DashboardPage(

            exitButtonViewModel =
                remember {
                    ExitButtonViewModel()
                },

            onSettingsClick = {},

            onNotesClick = {},

            onMessagesClick = {},

            onContactsClick = {},

            onResourcesClick = {},

            onDocumentsClick = {},

            onEmergencyClick = {},

            onQuickExit = {}
        )
    }
}