package com.example.androidapp.pages.weather

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.androidapp.security.LocalPrivacyExternalInteraction
import com.example.androidapp.security.voice.VoicePhraseKind
import com.example.androidapp.security.voice.VoicePhraseStore
import com.example.androidapp.security.voice.VoicePhraseTester

import com.example.androidapp.repository.DailyWeather
import com.example.androidapp.repository.HourlyWeather
import com.example.androidapp.repository.WeatherData
import com.example.androidapp.repository.WeatherResult

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel

// Kept for existing weather components that reference this colour.

@Composable
fun WeatherScreen(
    onContactUsClick: () -> Unit = {},
    onVoiceAccess: () -> Unit = {},
    onVoiceDuress: () -> Unit = {},
    onVoiceCancel: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val externalInteraction = LocalPrivacyExternalInteraction.current
    val latestAccess by rememberUpdatedState(onVoiceAccess)
    val latestDuress by rememberUpdatedState(onVoiceDuress)
    val latestCancel by rememberUpdatedState(onVoiceCancel)
    val tester = remember(context) { VoicePhraseTester(context) }
    val phraseStore = remember(context) { VoicePhraseStore(context) }
    var listening by remember { mutableStateOf(false) }
    var requestingPermission by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("") }

    fun stopListening() {
        tester.cancel()
        listening = false
        voiceStatus = ""
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        requestingPermission = false
        externalInteraction(false)
        voiceStatus = if (granted) "Microphone ready. Tap Voice again."
        else "Microphone permission is needed for voice input."
    }

    DisposableEffect(lifecycleOwner, tester) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                tester.cancel()
                listening = false
                voiceStatus = ""
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tester.cancel()
        }
    }

    fun startListening() {
        if (listening || requestingPermission ||
            !lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return

        val phrases = runCatching { phraseStore.load() }.getOrNull()
        if (phrases == null || phrases.validationError() != null) {
            voiceStatus = "Voice input isn't configured."
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestingPermission = true
            externalInteraction(true)
            try {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } catch (_: Exception) {
                requestingPermission = false
                externalInteraction(false)
                voiceStatus = "Could not request microphone access."
            }
            return
        }

        listening = true
        voiceStatus = "Starting microphone…"
        tester.startForPhrases(
            phrases = phrases,
            onStatus = { voiceStatus = it },
            onMatched = { kind ->

                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                    voiceStatus = "Voice input ended."
                    when (kind) {
                        VoicePhraseKind.ACCESS -> {
                            voiceStatus = ""
                            latestAccess()
                        }
                        VoicePhraseKind.DURESS -> latestDuress()
                        VoicePhraseKind.CANCEL -> latestCancel()
                    }
                }
            },
            onFinished = {
                listening = false
                // Do not expose configured phrases or internal setup details on WeatherNow.
                voiceStatus = "Voice input ended."
            }
        )
    }

    val model: WeatherDataViewModel = viewModel()
    val preferences by model.preferences.collectAsState()
    val state by model.uiState.collectAsState()
    val refreshing by model.isRefreshing.collectAsState()
    val searchState by model.locationSearch.collectAsState()
    val night = rememberWeatherNight(preferences, state)
    WeatherContent(
        preferences = preferences,
        state = state,
        night = night,
        isRefreshing = refreshing,
        onPreferencesChange = model::updatePreferences,
        onRefresh = model::refreshWeather,
        onContactUsClick = {
            stopListening()
            onContactUsClick()
        },
        onWeatherInteraction = { stopListening() },
        voiceControls = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = !requestingPermission,
                    onClick = { if (listening) stopListening() else startListening() }
                ) {
                    Icon(Icons.Default.Mic, contentDescription = null)
                    Text(if (listening) "Stop" else "Voice")
                }
                Text(
                    text = voiceStatus,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        searchState = searchState,
        onSearchQueryChange = model::searchLocations,
        onLocationSelected = model::selectLocation,
        onMapPointSelected = model::selectMapLocation
    )
}

@Composable
private fun WeatherContent(
    preferences: WeatherPreferences,
    state: WeatherUiState,
    night: Boolean,
    isRefreshing: Boolean = false,
    onPreferencesChange: (WeatherPreferences) -> Unit = {},
    onRefresh: () -> Unit = {},
    onContactUsClick: () -> Unit = {},
    searchState: LocationSearchState = LocationSearchState(),
    onSearchQueryChange: (String) -> Unit = {},
    onLocationSelected: (WeatherLocation) -> Unit = {},
    onMapPointSelected: (WeatherLocation) -> Unit = onLocationSelected,
    initialTab: Int = 0,
    onWeatherInteraction: () -> Unit = {},
    voiceControls: @Composable () -> Unit = {}
) {
    val activity = LocalActivity.current as? ComponentActivity
    DisposableEffect(activity, night) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= 29) {
            activity?.window?.isNavigationBarContrastEnforced = false
        }
        onDispose { activity?.enableEdgeToEdge() }
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }
    var showLocationPicker by rememberSaveable { mutableStateOf(false) }
    val tabState = rememberSaveableStateHolder()
    val colors = weatherColorScheme(night)
    WeatherPageTheme(preferences, night) {
            Scaffold(
                containerColor = colors.background,
                bottomBar = {
                    Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                        voiceControls()
                        WeatherBottomNavigation(selectedTab) {
                            onWeatherInteraction()
                            selectedTab = it
                        }
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                    .background(colors.background)) {
                    tabState.SaveableStateProvider(selectedTab) {
                    when (selectedTab) {
                        0 -> WeatherHomeScreen(state = state, onRefresh = onRefresh,
                            isRefreshing = isRefreshing, night = night,
                            onChangeLocation = { onWeatherInteraction(); showLocationPicker = true })
                        2 -> WeatherSettingsScreen(
                            onContactUsClick = onContactUsClick,
                            preferences = preferences,
                            onPreferencesChange = onPreferencesChange,
                            onChangeLocation = { onWeatherInteraction(); showLocationPicker = true },
                            onRefresh = onRefresh,
                            isRefreshing = isRefreshing,
                            refreshMessage = when (state) {
                                is WeatherUiState.Error -> state.message
                                is WeatherUiState.Success -> if (state.result.isOffline) {
                                    "Offline — showing the last saved forecast."
                                } else "Weather is up to date."
                                else -> null
                            }
                        )
                        1 -> WeatherMapScreen(
                            location = preferences.city,
                            state = state,
                            night = night,
                            isRefreshing = isRefreshing,
                            onPointSelected = onMapPointSelected,
                            onSearch = { onWeatherInteraction(); showLocationPicker = true },
                            onViewForecast = { onWeatherInteraction(); selectedTab = 0 },
                            onRefresh = onRefresh
                        )
                    }
                    }
                }
            }
            if (showLocationPicker) {
                WeatherLocationPicker(
                    state = searchState,
                    onQueryChange = onSearchQueryChange,
                    onSelect = {
                        onLocationSelected(it)
                        showLocationPicker = false
                    },
                    onDismiss = { showLocationPicker = false }
                )
            }
    }
}

@Composable
fun WeatherBottomNavigation(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        NavigationBarItem(
            selected = selectedTab == 0, onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home") }
        )
        NavigationBarItem(
            selected = selectedTab == 1, onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.Map, contentDescription = "Map") },
            label = { Text("Map") }
        )
        NavigationBarItem(
            selected = selectedTab == 2, onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WeatherDayPreview() {
    WeatherContent(WeatherPreferences(), previewWeather(), night = false)
}

@Preview(showBackground = true)
@Composable
private fun WeatherNightPreview() {
    WeatherContent(WeatherPreferences(), previewWeather(), night = true)
}

@Preview(showBackground = true)
@Composable
private fun WeatherMapPreview() {
    WeatherContent(WeatherPreferences(), previewWeather(), night = false, initialTab = 1)
}

private fun previewWeather(): WeatherUiState {
    val date = java.time.LocalDate.of(2026, 11, 3)
    val sunrise = date.atTime(7, 30)
    val sunset = date.atTime(17, 0)
    val daily = DailyWeather(date, 0, -2, 8, sunrise, sunset)
    return WeatherUiState.Success(
        WeatherResult(
            WeatherData(
                currentTemperature = 5, feelsLike = 2, currentWeatherCode = 0,
                todayHigh = 8, todayLow = -2, sunrise = sunrise, sunset = sunset,
                hourlyForecast = (0..7).map { HourlyWeather(date.atTime(12, 0).plusHours(it.toLong()), 5 - it, 0) },
                dailyForecast = listOf(daily), windSpeedKmh = 16.0
            ),
            isOffline = false, lastUpdatedMillis = 0
        )
    )
}
