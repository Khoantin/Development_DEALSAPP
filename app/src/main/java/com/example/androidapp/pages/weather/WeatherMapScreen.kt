package com.example.androidapp.pages.weather

import com.example.androidapp.repository.weatherDescription

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun WeatherMapScreen(
    location: WeatherLocation,
    state: WeatherUiState,
    night: Boolean,
    isRefreshing: Boolean,
    onPointSelected: (WeatherLocation) -> Unit,
    onSearch: () -> Unit,
    onViewForecast: () -> Unit,
    onRefresh: () -> Unit
) {
    val preferences = LocalWeatherPreferences.current

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Weather map", color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Pan, zoom, and tap a point",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalIconButton(onClick = onSearch) {
                Icon(Icons.Default.Search, contentDescription = "Search for a location")
            }
        }

        KeylessWeatherMap(
            location = location,
            night = night,
            onPointSelected = onPointSelected,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        Surface(tonalElevation = 4.dp) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(location.label, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                val coordinates = WeatherLocation.fromCoordinates(location.latitude, location.longitude).label
                if (location.label != coordinates) {
                    Text(coordinates, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (state) {
                    WeatherUiState.Loading -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text("Loading weather for this point…")
                    }
                    is WeatherUiState.Error -> {
                        Text(state.message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onRefresh) { Text("Retry") }
                    }
                    is WeatherUiState.Success -> {
                        val weather = state.result.weather
                        Text(
                            "${preferences.temperature(weather.currentTemperature)} • ${weatherDescription(weather.currentWeatherCode)}",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text("Feels like ${preferences.temperature(weather.feelsLike)} • High ${preferences.temperature(weather.todayHigh)} • Low ${preferences.temperature(weather.todayLow)}")
                        if (state.result.isOffline) Text("Offline — showing the last saved forecast.")
                        if (isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
                        Button(onClick = onViewForecast, modifier = Modifier.fillMaxWidth()) {
                            Text("View full forecast")
                        }
                    }
                }
            }
        }
    }
}
