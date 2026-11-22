package com.example.androidapp.pages.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@Composable
fun WeatherContactSupportScreen(
    onBackClick: () -> Unit,
    onSecretAccess: () -> Unit,
    weatherModel: WeatherDataViewModel = viewModel()
) {
    val preferences by weatherModel.preferences.collectAsState()
    val state by weatherModel.uiState.collectAsState()
    val night = rememberWeatherNight(preferences, state)
    WeatherPageTheme(preferences, night) {
        WeatherContactSupportContent(onBackClick, onSecretAccess)
    }
}

@Composable
private fun WeatherContactSupportContent(
    onBackClick: () -> Unit,
    onSecretAccess: () -> Unit
) {
    var name by remember {
        mutableStateOf("")
    }

    var email by remember {
        mutableStateOf("")
    }

    var subject by remember {
        mutableStateOf("")
    }

    var message by remember {
        mutableStateOf("")
    }

    var selectedCategory by remember {
        mutableStateOf("Weather data")
    }

    var confirmationMessage by remember {
        mutableStateOf<String?>(null)
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                horizontal = 20.dp
            )
    ) {

        Spacer(
            modifier = Modifier.height(12.dp)
        )


        TextButton(
            onClick = onBackClick
        ) {

            Text(
                text = "←",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold
            )
        }


        Spacer(
            modifier = Modifier.height(4.dp)
        )


        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Card(
            modifier = Modifier.fillMaxWidth(),

            shape = RoundedCornerShape(
                24.dp
            ),

            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.primaryContainer
            )
        ) {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Surface(
                        shape = CircleShape,

                        color =
                            MaterialTheme.colorScheme.primary
                    ) {

                        Icon(
                            imageVector =
                                Icons.Outlined.Cloud,

                            contentDescription =
                                null,

                            modifier = Modifier
                                .padding(12.dp)
                                .size(28.dp),

                            tint =
                                MaterialTheme.colorScheme.onPrimary
                        )
                    }


                    Spacer(
                        modifier = Modifier.size(14.dp)
                    )


                    Column {

                        Text(
                            text = "WeatherNow Support",

                            fontSize = 24.sp,

                            fontWeight =
                                FontWeight.Bold,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onPrimaryContainer
                        )


                        Text(
                            text =
                                "We're here to help",

                            fontSize = 14.sp,

                            color =
                                MaterialTheme
                                    .colorScheme
                                    .onPrimaryContainer
                                    .copy(
                                        alpha = 0.75f
                                    )
                        )
                    }
                }


                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )


                Text(
                    text =
                        "Having trouble with the app or weather information? Send us a message and our support team will review it.",

                    fontSize = 15.sp,

                    color =
                        MaterialTheme
                            .colorScheme
                            .onPrimaryContainer
                )
            }
        }


        Spacer(
            modifier = Modifier.height(20.dp)
        )


        // ---------------------------------------------------------
        // SUPPORT STATUS
        // ---------------------------------------------------------

        Card(
            modifier = Modifier.fillMaxWidth(),

            shape = RoundedCornerShape(
                18.dp
            ),

            colors =
                CardDefaults.cardColors(
                    containerColor =
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                )
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Surface(
                    shape = CircleShape,

                    color =
                        MaterialTheme.colorScheme.primaryContainer
                ) {

                    Icon(
                        imageVector =
                            Icons.Outlined.SupportAgent,

                        contentDescription =
                            null,

                        modifier =
                            Modifier.padding(9.dp),

                        tint =
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }


                Spacer(
                    modifier = Modifier.size(12.dp)
                )


                Column {

                    Text(
                        text = "Support available",

                        fontWeight =
                            FontWeight.SemiBold,

                        fontSize = 15.sp
                    )


                    Text(
                        text =
                            "Typical response time: within 24 hours",

                        fontSize = 13.sp,

                        color =
                            MaterialTheme
                                .colorScheme
                                .onSurfaceVariant
                    )
                }
            }
        }


        Spacer(
            modifier = Modifier.height(24.dp)
        )


        Text(
            text = "What can we help with?",

            fontSize = 18.sp,

            fontWeight = FontWeight.Bold
        )


        Spacer(
            modifier = Modifier.height(12.dp)
        )


        // ---------------------------------------------------------
        // CATEGORY ROW 1
        // ---------------------------------------------------------

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            SupportCategoryChip(
                text = "Weather data",

                selected =
                    selectedCategory ==
                            "Weather data",

                modifier =
                    Modifier.weight(1f),

                onClick = {
                    selectedCategory =
                        "Weather data"
                }
            )


            SupportCategoryChip(
                text = "Location",

                selected =
                    selectedCategory ==
                            "Location",

                modifier =
                    Modifier.weight(1f),

                onClick = {
                    selectedCategory =
                        "Location"
                }
            )
        }


        Spacer(
            modifier = Modifier.height(10.dp)
        )


        // ---------------------------------------------------------
        // CATEGORY ROW 2
        // ---------------------------------------------------------

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            SupportCategoryChip(
                text = "Notifications",

                selected =
                    selectedCategory ==
                            "Notifications",

                modifier =
                    Modifier.weight(1f),

                onClick = {
                    selectedCategory =
                        "Notifications"
                }
            )


            SupportCategoryChip(
                text = "App issue",

                selected =
                    selectedCategory ==
                            "App issue",

                modifier =
                    Modifier.weight(1f),

                onClick = {
                    selectedCategory =
                        "App issue"
                }
            )
        }


        Spacer(
            modifier = Modifier.height(24.dp)
        )


        // ---------------------------------------------------------
        // FORM
        // ---------------------------------------------------------

        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(22.dp),

            elevation =
                CardDefaults.cardElevation(
                    defaultElevation = 2.dp
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {

                Text(
                    text = "Send us a message",

                    fontSize = 19.sp,

                    fontWeight =
                        FontWeight.Bold
                )


                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )


                OutlinedTextField(
                    value = name,

                    onValueChange = {
                        name = it
                    },

                    label = {
                        Text("Name")
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    singleLine = true
                )


                Spacer(
                    modifier =
                        Modifier.height(14.dp)
                )


                OutlinedTextField(
                    value = email,

                    onValueChange = {
                        email = it
                    },

                    label = {
                        Text("Email")
                    },

                    leadingIcon = {

                        Icon(
                            imageVector =
                                Icons.Outlined.Email,

                            contentDescription =
                                null
                        )
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    singleLine = true
                )


                Spacer(
                    modifier =
                        Modifier.height(14.dp)
                )


                OutlinedTextField(
                    value = subject,

                    onValueChange = {
                        subject = it
                    },

                    label = {
                        Text("Subject")
                    },

                    modifier =
                        Modifier.fillMaxWidth(),

                    singleLine = true
                )


                Spacer(
                    modifier =
                        Modifier.height(14.dp)
                )


                OutlinedTextField(
                    value = message,

                    onValueChange = {
                        message = it
                    },

                    label = {
                        Text(
                            "Describe your issue"
                        )
                    },

                    placeholder = {
                        Text(
                            "Tell us what happened..."
                        )
                    },

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),

                    minLines = 5
                )


                Spacer(
                    modifier =
                        Modifier.height(20.dp)
                )


                Button(
                    onClick = {

                        val secretPhrase =
                            "clear skies ahead"


                        if (
                            message
                                .trim()
                                .equals(
                                    secretPhrase,
                                    ignoreCase = true
                                )
                        ) {

                            confirmationMessage =
                                null

                            onSecretAccess()

                        } else {

                            confirmationMessage =
                                "Thanks for contacting WeatherNow Support. Your request has been submitted successfully."

                            name = ""
                            email = ""
                            subject = ""
                            message = ""
                        }
                    },

                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),

                    shape =
                        RoundedCornerShape(
                            16.dp
                        ),

                    colors =
                        ButtonDefaults
                            .buttonColors(
                                containerColor =
                                    MaterialTheme
                                        .colorScheme
                                        .primary
                            )
                ) {

                    Icon(
                        imageVector =
                            Icons.Outlined.Send,

                        contentDescription =
                            null
                    )


                    Spacer(
                        modifier =
                            Modifier.size(8.dp)
                    )


                    Text(
                        text = "Send Message",

                        fontSize = 16.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )
                }
            }
        }


        confirmationMessage
            ?.let { confirmation ->

                Spacer(
                    modifier =
                        Modifier.height(18.dp)
                )


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
                                MaterialTheme.colorScheme.primaryContainer
                        )
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Icon(
                            imageVector =
                                Icons.Outlined.CheckCircle,

                            contentDescription =
                                null,

                            tint =
                                MaterialTheme.colorScheme.primary
                        )


                        Spacer(
                            modifier =
                                Modifier.size(10.dp)
                        )


                        Text(
                            text = confirmation,

                            fontSize = 14.sp,

                            color =
                                MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }


        Spacer(
            modifier = Modifier.height(32.dp)
        )
    }
}


@Composable
private fun SupportCategoryChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {

    Surface(
        modifier =
            modifier
                .clickable {
                    onClick()
                },

        shape =
            RoundedCornerShape(
                50.dp
            ),

        color =
            if (selected) {

                MaterialTheme
                    .colorScheme
                    .primary

            } else {

                MaterialTheme
                    .colorScheme
                    .surfaceVariant
            }
    ) {

        Text(
            text = text,

            modifier =
                Modifier.padding(
                    horizontal = 12.dp,
                    vertical = 11.dp
                ),

            fontSize =
                13.sp,

            fontWeight =
                if (selected) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },

            color =
                if (selected) {

                    MaterialTheme
                        .colorScheme
                        .onPrimary

                } else {

                    MaterialTheme
                        .colorScheme
                        .onSurfaceVariant
                }
        )
    }
}

@Preview(name = "Contact us — day", widthDp = 400, heightDp = 1000)
@Composable
private fun WeatherContactDayPreview() {
    WeatherPageTheme(WeatherPreferences(theme = WeatherTheme.DAY), night = false) {
        WeatherContactSupportContent(onBackClick = {}, onSecretAccess = {})
    }
}

@Preview(name = "Contact us — night", widthDp = 400, heightDp = 1000)
@Composable
private fun WeatherContactNightPreview() {
    WeatherPageTheme(WeatherPreferences(theme = WeatherTheme.NIGHT), night = true) {
        WeatherContactSupportContent(onBackClick = {}, onSecretAccess = {})
    }
}