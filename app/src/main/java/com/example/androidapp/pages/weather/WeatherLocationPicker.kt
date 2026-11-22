package com.example.androidapp.pages.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun WeatherLocationPicker(
    state: LocationSearchState,
    onQueryChange: (String) -> Unit,
    onSelect: (WeatherLocation) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(),
        title = { Text("Choose a location") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    label = { Text("City or postal code") },
                    placeholder = { Text("e.g. London, Ontario") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    when {
                        state.isSearching -> {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Searching…", Modifier.padding(vertical = 12.dp))
                        }
                        state.error != null -> {
                            Text(state.error, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = { onQueryChange(state.query) }) { Text("Retry") }
                        }
                        state.hasSearched && state.results.isEmpty() ->
                            Text("No matching places. Try a nearby city or add a region.")
                        state.results.isNotEmpty() -> state.results.forEach { location ->
                            LocationChoice(location, onSelect)
                        }
                        else -> {
                            Text("Search worldwide, or choose a nearby city.",
                                style = MaterialTheme.typography.bodySmall)
                            WeatherCity.entries.forEach { LocationChoice(it.location, onSelect) }
                        }
                    }
                }
                Text("Location search: Open-Meteo / GeoNames",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun LocationChoice(location: WeatherLocation, onSelect: (WeatherLocation) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable { onSelect(location) }.padding(vertical = 12.dp)
    ) {
        Text(location.label, style = MaterialTheme.typography.bodyLarge)
        Text(
            String.format(java.util.Locale.US, "%.3f, %.3f", location.latitude, location.longitude),
            style = MaterialTheme.typography.bodySmall
        )
    }
    HorizontalDivider()
}
