package com.example.androidapp.pages.locationsharing
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel


@Composable
fun LocationSharingScreen(
    onBackClick: () -> Unit,
    viewModel: LocationSharingViewModel =
        viewModel(),
    onPermissionRequestActiveChange: (Boolean) -> Unit = {}
) {
    BackHandler(onBack = onBackClick)

    val state =
        viewModel.uiState


    val context =
        LocalContext.current


    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestMultiplePermissions()
        ) { permissions ->
            onPermissionRequestActiveChange(false)


            val fineGranted =
                permissions[
                    Manifest.permission
                        .ACCESS_FINE_LOCATION
                ] == true


            val coarseGranted =
                permissions[
                    Manifest.permission
                        .ACCESS_COARSE_LOCATION
                ] == true


            if (
                fineGranted ||
                coarseGranted
            ) {

                viewModel
                    .getCurrentLocation()

            } else {

                viewModel
                    .locationPermissionDenied()
            }
        }


    fun requestOrGetLocation() {

        val fineGranted =
            ContextCompat
                .checkSelfPermission(
                    context,
                    Manifest.permission
                        .ACCESS_FINE_LOCATION
                ) ==
                    PackageManager
                        .PERMISSION_GRANTED


        val coarseGranted =
            ContextCompat
                .checkSelfPermission(
                    context,
                    Manifest.permission
                        .ACCESS_COARSE_LOCATION
                ) ==
                    PackageManager
                        .PERMISSION_GRANTED


        if (
            fineGranted ||
            coarseGranted
        ) {

            viewModel
                .getCurrentLocation()

        } else {

            onPermissionRequestActiveChange(true)
            try {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } catch (exception: RuntimeException) {
                onPermissionRequestActiveChange(false)
                viewModel.locationPermissionDenied()
                Toast.makeText(
                    context,
                    "Unable to request location permission. Please try again.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(
                start = 24.dp,
                end = 24.dp,
                top = 8.dp,
                bottom = 96.dp
            )
    ) {

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )


        TextButton(
            onClick = onBackClick,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Back")
        }


        Text(
            text =
                "Location Sharing",

            fontSize =
                32.sp,

            fontWeight =
                FontWeight.Bold,

            color =
                MaterialTheme
                    .colorScheme
                    .primary
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Text(
            text =
                "Choose who can receive your location.",

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )


        Spacer(
            modifier =
                Modifier.height(28.dp)
        )


        when {

            state.isLoading -> {

                CircularProgressIndicator()
            }


            state.contacts.isEmpty() -> {

                Text(
                    text =
                        "No trusted contacts with access are available.",

                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }


            else -> {

                state.contacts
                    .forEach { contact ->


                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    enabled =
                                        !state
                                            .sharingActive
                                ) {

                                    viewModel
                                        .toggleContact(
                                            contact.id
                                        )
                                }
                                .padding(
                                    vertical =
                                        12.dp
                                ),

                            verticalAlignment =
                                Alignment
                                    .CenterVertically
                        ) {


                            Checkbox(
                                checked =
                                    state
                                        .selectedContactIds
                                        .contains(
                                            contact.id
                                        ),

                                enabled =
                                    !state
                                        .sharingActive,

                                onCheckedChange = {

                                    viewModel
                                        .toggleContact(
                                            contact.id
                                        )
                                }
                            )


                            Column(
                                modifier =
                                    Modifier
                                        .padding(
                                            start =
                                                12.dp
                                        )
                            ) {


                                Text(
                                    text =
                                        contact.name,

                                    fontWeight =
                                        FontWeight
                                            .SemiBold,

                                    fontSize =
                                        17.sp
                                )


                                Text(
                                    text =
                                        contact.relationship,

                                    fontSize =
                                        14.sp,

                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant
                                )


                                Text(
                                    text =
                                        contact.phoneNumber,

                                    fontSize =
                                        14.sp,

                                    color =
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant
                                )
                            }
                        }
                    }
            }
        }


        Spacer(
            modifier =
                Modifier.height(20.dp)
        )


        Button(
            onClick = {

                viewModel
                    .saveSelection()
            },

            enabled =
                !state.isSaving &&
                        !state.sharingActive &&
                        state.contacts
                            .isNotEmpty(),

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
        ) {


            Text(
                text =
                    if (
                        state.isSaving
                    ) {

                        "Saving..."

                    } else {

                        "Save Selection"
                    }
            )
        }


        Spacer(
            modifier =
                Modifier.height(28.dp)
        )


        HorizontalDivider()


        Spacer(
            modifier =
                Modifier.height(24.dp)
        )


        Text(
            text =
                "Your Location",

            fontSize =
                20.sp,

            fontWeight =
                FontWeight.Bold
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        Text(
            text =
                "Get your current location before sharing it with selected contacts.",

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )


        Spacer(
            modifier =
                Modifier.height(16.dp)
        )


        OutlinedButton(
            onClick = {

                requestOrGetLocation()
            },

            enabled =
                !state
                    .isGettingLocation &&
                        !state
                            .sharingActive,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
        ) {


            Text(
                text =
                    if (
                        state.isGettingLocation
                    ) {

                        "Getting Location..."

                    } else {

                        "Get Current Location"
                    }
            )
        }


        if (
            state.latitude != null &&
            state.longitude != null
        ) {

            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            Text(
                text =
                    "Location found",

                fontWeight =
                    FontWeight.Bold,

                color =
                    MaterialTheme
                        .colorScheme
                        .primary
            )


            Spacer(
                modifier =
                    Modifier.height(6.dp)
            )


            Text(
                text =
                    "Latitude: ${state.latitude}"
            )


            Text(
                text =
                    "Longitude: ${state.longitude}"
            )
        }


        state.locationError
            ?.let { error ->

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )


                Text(
                    text = error,

                    color =
                        MaterialTheme
                            .colorScheme
                            .error
                )
            }


        Spacer(
            modifier =
                Modifier.height(28.dp)
        )


        HorizontalDivider()


        Spacer(
            modifier =
                Modifier.height(24.dp)
        )


        // -----------------------------------------------------
        // SHARING STATUS
        // -----------------------------------------------------

        Text(
            text =
                "Sharing Status",

            fontSize =
                20.sp,

            fontWeight =
                FontWeight.Bold
        )


        Spacer(
            modifier =
                Modifier.height(8.dp)
        )


        if (
            state.sharingActive
        ) {

            Text(
                text =
                    "Your location is currently being shared.",

                color =
                    MaterialTheme
                        .colorScheme
                        .primary,

                fontWeight =
                    FontWeight.SemiBold
            )


            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            OutlinedButton(
                onClick = {

                    viewModel
                        .stopSharing()
                },

                enabled =
                    !state
                        .isStoppingShare,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
            ) {


                Text(
                    text =
                        if (
                            state.isStoppingShare
                        ) {

                            "Stopping..."

                        } else {

                            "Stop Sharing"
                        }
                )
            }

        } else {

            Text(
                text =
                    "Location sharing is currently off.",

                color =
                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
            )


            Spacer(
                modifier =
                    Modifier.height(16.dp)
            )


            Button(
                onClick = {

                    viewModel
                        .startSharing()
                },

                enabled =
                    !state
                        .isStartingShare &&
                            state
                                .selectedContactIds
                                .isNotEmpty() &&
                            state.latitude != null &&
                            state.longitude != null,

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
            ) {


                Text(
                    text =
                        if (
                            state.isStartingShare
                        ) {

                            "Starting..."

                        } else {

                            "Start Sharing Location"
                        }
                )
            }
        }


        state.errorMessage
            ?.let { error ->

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )


                Text(
                    text = error,

                    color =
                        MaterialTheme
                            .colorScheme
                            .error
                )
            }


        state.message
            ?.let { message ->

                Spacer(
                    modifier =
                        Modifier.height(16.dp)
                )


                Text(
                    text = message,

                    color =
                        MaterialTheme
                            .colorScheme
                            .primary,

                    fontWeight =
                        FontWeight.Medium
                )
            }


        Spacer(
            modifier =
                Modifier.height(40.dp)
        )
    }
}
