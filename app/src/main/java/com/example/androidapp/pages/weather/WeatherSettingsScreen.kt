package com.example.androidapp.pages.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WeatherSettingsScreen(
    onContactUsClick: () -> Unit = {},
    preferences: WeatherPreferences = LocalWeatherPreferences.current,
    onPreferencesChange: (WeatherPreferences) -> Unit = {},
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    refreshMessage: String? = null,
    onChangeLocation: () -> Unit = {}
) {
    var dialog by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 30.dp)
    ) {
        Text("Settings", color = MaterialTheme.colorScheme.onBackground, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(30.dp))
        SettingsSectionTitle("UNITS")
        SettingsRow("Temperature", if (preferences.fahrenheit) "°F" else "°C") {
            dialog = "temperature"
        }
        SettingsRow("Wind speed", if (preferences.milesPerHour) "mph" else "km/h") {
            dialog = "wind"
        }
        Spacer(Modifier.height(25.dp))
        SettingsSectionTitle("WEATHER")
        SettingsRow("Location", preferences.city.label, onChangeLocation)
        SettingsRow("Refresh weather", if (isRefreshing) "Refreshing…" else "↻",
            onClick = if (isRefreshing) null else onRefresh)
        refreshMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.height(25.dp))
        SettingsSectionTitle("APPEARANCE")
        SettingsRow("Theme", preferences.theme.label) { dialog = "theme" }
        Spacer(Modifier.height(25.dp))
        SettingsSectionTitle("ABOUT")
        SettingsRow("Weather information", "›") { dialog = "information" }
        SettingsRow("Contact us", "›", onContactUsClick)
        SettingsRow("App version", "1.0") { dialog = "version" }
    }

    val choices = when (dialog) {
        "temperature" -> listOf("Celsius (°C)", "Fahrenheit (°F)")
        "wind" -> listOf("Kilometres per hour (km/h)", "Miles per hour (mph)")
        "theme" -> listOf("Auto — sunrise and sunset", "Day", "Night")
        else -> emptyList()
    }
    val selectedIndex = when (dialog) {
        "temperature" -> if (preferences.fahrenheit) 1 else 0
        "wind" -> if (preferences.milesPerHour) 1 else 0
        "theme" -> preferences.theme.ordinal
        else -> -1
    }
    if (choices.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(when (dialog) {
                "temperature" -> "Temperature unit"
                "wind" -> "Wind speed unit"
                else -> "Weather theme"
            }) },
            text = {
                Column {
                    choices.forEachIndexed { index, label ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onPreferencesChange(when (dialog) {
                                    "temperature" -> preferences.copy(fahrenheit = index == 1)
                                    "wind" -> preferences.copy(milesPerHour = index == 1)
                                    else -> preferences.copy(theme = WeatherTheme.entries[index])
                                })
                                dialog = null
                            }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = index == selectedIndex, onClick = null)
                            Text(label, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } }
        )
    } else if (dialog == "information" || dialog == "version") {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (dialog == "version") "App version" else "Weather information") },
            text = { Text(if (dialog == "version") {
                "Weather ${androidx.compose.ui.platform.LocalContext.current.packageManager.getPackageInfo(
                    androidx.compose.ui.platform.LocalContext.current.packageName, 0
                ).versionName}"
            } else {
                "Forecasts are provided by Open-Meteo. Weather refreshes every 30 minutes while the weather screen is open. You can also refresh manually. Offline mode shows the last saved forecast for the selected city. Auto theme uses that city's sunrise and sunset, with 6 AM–6 PM as a fallback when today's forecast is unavailable."
            }) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Close") } }
        )
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(title, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.60f),
        fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(5.dp))
}

@Composable
fun SettingsRow(title: String, value: String, onClick: (() -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
        )
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = MaterialTheme.colorScheme.onBackground, fontSize = 17.sp, modifier = Modifier.weight(1f))
            Text(value, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f), fontSize = 16.sp,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 190.dp).padding(start = 12.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f))
    }
}
