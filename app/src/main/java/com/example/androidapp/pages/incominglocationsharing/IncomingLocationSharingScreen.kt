package com.example.androidapp.pages.incominglocationsharing
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.safeDrawingPadding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidapp.data.IncomingLocationShare
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import java.text.DateFormat
import java.util.Locale


@Composable
fun IncomingLocationSharingScreen(
    onBackClick: () -> Unit,
    viewModel: IncomingLocationSharingViewModel =
        viewModel()
) {
    BackHandler(onBack = onBackClick)

    val state =
        viewModel.uiState


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
                Modifier.height(12.dp)
        )


        TextButton(
            onClick = onBackClick,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("Back")
        }


        Text(
            text =
                "Incoming Locations",

            fontSize =
                30.sp,

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
                "View live locations currently shared with you.",

            fontSize =
                15.sp,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )


        Spacer(
            modifier =
                Modifier.height(24.dp)
        )


        when {

            state.isLoading -> {

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                vertical = 48.dp
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    CircularProgressIndicator()
                }
            }


            state.errorMessage != null -> {

                Card(
                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(
                            18.dp
                        ),

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .errorContainer
                        )
                ) {

                    Text(
                        text =
                            state.errorMessage,

                        modifier =
                            Modifier.padding(
                                18.dp
                            ),

                        color =
                            MaterialTheme
                                .colorScheme
                                .onErrorContainer
                    )
                }
            }


            state.shares.isEmpty() -> {

                Card(
                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(
                            20.dp
                        ),

                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .surfaceVariant
                        )
                ) {

                    Column(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    28.dp
                                ),

                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Icon(
                            imageVector =
                                Icons.Outlined.LocationOn,

                            contentDescription =
                                null,

                            modifier =
                                Modifier.padding(
                                    bottom = 12.dp
                                ),

                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )


                        Text(
                            text =
                                "No active locations",

                            fontSize =
                                18.sp,

                            fontWeight =
                                FontWeight.Bold
                        )


                        Spacer(
                            modifier =
                                Modifier.height(
                                    6.dp
                                )
                        )


                        Text(
                            text =
                                "When someone shares their live location with you, it will appear here.",

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


            else -> {

                state.shares
                    .forEach { share ->

                        LiveLocationShareCard(
                            share =
                                share
                        )


                        Spacer(
                            modifier =
                                Modifier.height(
                                    20.dp
                                )
                        )
                    }
            }
        }


        Spacer(
            modifier =
                Modifier.height(
                    32.dp
                )
        )
    }
}


@Composable
private fun LiveLocationShareCard(
    share: IncomingLocationShare
) {

    val context =
        LocalContext.current


    val senderName =
        share.ownerDisplayName
            .ifBlank {
                "User"
            }


    val location =
        LatLng(
            share.latitude,
            share.longitude
        )


    val markerState =
        rememberUpdatedMarkerState(
            position =
                location
        )


    val cameraPositionState =
        rememberCameraPositionState {

            position =
                CameraPosition.fromLatLngZoom(
                    location,
                    15.5f
                )
        }


    var mapLoaded by
    remember {
        mutableStateOf(
            false
        )
    }


    LaunchedEffect(
        share.latitude,
        share.longitude,
        mapLoaded
    ) {

        if (mapLoaded) {

            cameraPositionState.animate(
                update =
                    CameraUpdateFactory
                        .newLatLngZoom(
                            location,
                            15.5f
                        ),

                durationMs =
                    700
            )
        }
    }


    val mapProperties =
        remember {

            MapProperties(
                mapType =
                    MapType.NORMAL,

                isBuildingEnabled =
                    true
            )
        }


    val mapUiSettings =
        remember {

            MapUiSettings(
                compassEnabled =
                    true,

                zoomControlsEnabled =
                    false,

                mapToolbarEnabled =
                    false,

                scrollGesturesEnabled =
                    true,

                zoomGesturesEnabled =
                    true,

                tiltGesturesEnabled =
                    true,

                rotationGesturesEnabled =
                    true
            )
        }


    Card(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(
                24.dp
            ),

        elevation =
            CardDefaults.cardElevation(
                defaultElevation =
                    3.dp
            ),

        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme
                        .colorScheme
                        .surface
            )
    ) {

        Column(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            // -------------------------------------------------
            // HEADER
            // -------------------------------------------------

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 20.dp,
                            bottom = 16.dp
                        ),

                verticalAlignment =
                    Alignment.CenterVertically,

                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Column(
                    modifier =
                        Modifier.weight(
                            1f
                        )
                ) {

                    Text(
                        text =
                            "$senderName's Location",

                        fontSize =
                            21.sp,

                        fontWeight =
                            FontWeight.Bold,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurface
                    )


                    Spacer(
                        modifier =
                            Modifier.height(
                                4.dp
                            )
                    )


                    Row(
                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            imageVector =
                                Icons.Outlined.LocationOn,

                            contentDescription =
                                null,

                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )


                        Text(
                            text =
                                "Live location",

                            fontSize =
                                14.sp,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onSurfaceVariant
                        )
                    }
                }


                Surface(
                    shape =
                        RoundedCornerShape(
                            50.dp
                        ),

                    color =
                        Color(
                            0xFFE7F6EC
                        )
                ) {

                    Row(
                        modifier =
                            Modifier.padding(
                                horizontal = 11.dp,
                                vertical = 7.dp
                            ),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Box(
                            modifier =
                                Modifier
                                    .clip(
                                        CircleShape
                                    )
                                    .background(
                                        Color(
                                            0xFF1F9D55
                                        )
                                    )
                                    .padding(
                                        4.dp
                                    )
                        )


                        Spacer(
                            modifier =
                                Modifier.padding(
                                    horizontal = 3.dp
                                )
                        )


                        Text(
                            text =
                                "LIVE",

                            fontSize =
                                12.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                Color(
                                    0xFF14783F
                                )
                        )
                    }
                }
            }


            // -------------------------------------------------
            // GOOGLE MAP
            // -------------------------------------------------

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = 16.dp
                        )
                        .height(
                            290.dp
                        )
                        .clip(
                            RoundedCornerShape(
                                20.dp
                            )
                        )
            ) {

                GoogleMap(
                    modifier =
                        Modifier.fillMaxSize(),

                    cameraPositionState =
                        cameraPositionState,

                    properties =
                        mapProperties,

                    uiSettings =
                        mapUiSettings,

                    onMapLoaded = {
                        mapLoaded =
                            true
                    }
                ) {

                    Marker(
                        state =
                            markerState,

                        title =
                            senderName,

                        snippet =
                            "Live shared location"
                    )
                }
            }


            // -------------------------------------------------
            // LOCATION DETAILS
            // -------------------------------------------------

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            20.dp
                        )
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector =
                            Icons.Outlined.AccessTime,

                        contentDescription =
                            null,

                        tint =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )


                    Spacer(
                        modifier =
                            Modifier.padding(
                                horizontal = 4.dp
                            )
                    )


                    Text(
                        text =
                            share.updatedAt
                                ?.let { timestamp ->

                                    "Last updated ${
                                        DateFormat
                                            .getTimeInstance(
                                                DateFormat.SHORT
                                            )
                                            .format(
                                                timestamp.toDate()
                                            )
                                    }"
                                }
                                ?: "Updating location...",

                        fontSize =
                            14.sp,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }


                Spacer(
                    modifier =
                        Modifier.height(
                            14.dp
                        )
                )


                Surface(
                    modifier =
                        Modifier.fillMaxWidth(),

                    shape =
                        RoundedCornerShape(
                            14.dp
                        ),

                    color =
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                ) {

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 13.dp
                                ),

                        verticalAlignment =
                            Alignment.CenterVertically,

                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        Column {

                            Text(
                                text =
                                    "Coordinates",

                                fontSize =
                                    12.sp,

                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant
                            )


                            Spacer(
                                modifier =
                                    Modifier.height(
                                        2.dp
                                    )
                            )


                            Text(
                                text =
                                    "${
                                        String.format(
                                            Locale.US,
                                            "%.5f",
                                            share.latitude
                                        )
                                    }, ${
                                        String.format(
                                            Locale.US,
                                            "%.5f",
                                            share.longitude
                                        )
                                    }",

                                fontSize =
                                    14.sp,

                                fontWeight =
                                    FontWeight.Medium
                            )
                        }


                        Icon(
                            imageVector =
                                Icons.Outlined.LocationOn,

                            contentDescription =
                                null,

                            tint =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )
                    }
                }


                Spacer(
                    modifier =
                        Modifier.height(
                            16.dp
                        )
                )


                Button(
                    onClick = {

                        val uri =
                            Uri.parse(
                                "https://www.google.com/maps/search/?api=1&query=${share.latitude},${share.longitude}"
                            )


                        val intent =
                            Intent(
                                Intent.ACTION_VIEW,
                                uri
                            )


                        context.startActivity(
                            intent
                        )
                    },

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(
                                52.dp
                            ),

                    shape =
                        RoundedCornerShape(
                            16.dp
                        ),

                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor =
                                MaterialTheme
                                    .colorScheme
                                    .primary
                        )
                ) {

                    Icon(
                        imageVector =
                            Icons.Outlined.Map,

                        contentDescription =
                            null
                    )


                    Spacer(
                        modifier =
                            Modifier.padding(
                                horizontal = 5.dp
                            )
                    )


                    Text(
                        text =
                            "Open in Google Maps",

                        fontSize =
                            15.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }
        }
    }
}