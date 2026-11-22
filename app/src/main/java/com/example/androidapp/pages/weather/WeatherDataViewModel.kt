package com.example.androidapp.pages.weather

import com.example.androidapp.repository.WeatherRepository
import com.example.androidapp.repository.WeatherLocationRepository
import com.example.androidapp.repository.WeatherResult

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.milliseconds
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class WeatherUiState {
    object Loading : WeatherUiState()
    data class Success(val result: WeatherResult) : WeatherUiState()
    data class Error(val message: String) : WeatherUiState()
}

class WeatherDataViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = WeatherRepository(application)
    private val locationRepository = WeatherLocationRepository()
    private val prefs = application.getSharedPreferences("weather_settings", Context.MODE_PRIVATE)

    private fun savedLocation(): WeatherLocation {
        val previous = WeatherCity.entries.firstOrNull { it.name == prefs.getString("city", null) }
            ?.location ?: WeatherCity.EDMONTON.location
        return runCatching {
            WeatherLocation(
                prefs.getString("location_label", previous.label) ?: previous.label,
                prefs.getString("location_latitude", null)?.toDouble() ?: previous.latitude,
                prefs.getString("location_longitude", null)?.toDouble() ?: previous.longitude,
                prefs.getString("location_timezone", previous.timeZone) ?: previous.timeZone
            )
        }.getOrDefault(previous)
    }

    private val _preferences = MutableStateFlow(
        WeatherPreferences(
            fahrenheit = prefs.getBoolean("fahrenheit", false),
            milesPerHour = prefs.getBoolean("miles_per_hour", false),
            city = savedLocation(),
            theme = WeatherTheme.entries.firstOrNull { it.name == prefs.getString("theme", null) }
                ?: WeatherTheme.AUTO
        )
    )
    val preferences: StateFlow<WeatherPreferences> = _preferences.asStateFlow()
    private val _uiState = MutableStateFlow<WeatherUiState>(WeatherUiState.Loading)
    val uiState: StateFlow<WeatherUiState> = _uiState.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _locationSearch = MutableStateFlow(LocationSearchState())
    val locationSearch: StateFlow<LocationSearchState> = _locationSearch.asStateFlow()
    private var refreshJob: Job? = null
    private var searchJob: Job? = null
    private var requestVersion = 0
    private var placeNameJob: Job? = null
    private var selectionVersion = 0
    private val placeNames = linkedMapOf<String, String>()

    init {
        refreshWeather()
        val saved = _preferences.value.city
        if (saved.label == WeatherLocation.fromCoordinates(saved.latitude, saved.longitude).label) {
            resolvePlaceName(saved)
        }
        viewModelScope.launch {
            while (true) {
                delay(30.minutes)
                refreshWeather()
            }
        }
    }

    fun updatePreferences(value: WeatherPreferences) {
        val locationChanged = value.city.cacheKey != _preferences.value.city.cacheKey
        _preferences.value = value
        prefs.edit {
            putBoolean("fahrenheit", value.fahrenheit)
            putBoolean("miles_per_hour", value.milesPerHour)
            putString("location_label", value.city.label)
            putString("location_latitude", value.city.latitude.toString())
            putString("location_longitude", value.city.longitude.toString())
            putString("location_timezone", value.city.timeZone)
            putString("theme", value.theme.name)
        }
        if (locationChanged) {
            refreshJob?.cancel()
            _uiState.value = WeatherUiState.Loading
            refreshWeather()
        }
    }

    fun selectLocation(location: WeatherLocation) {
        placeNameJob?.cancel()
        selectionVersion++
        updatePreferences(_preferences.value.copy(city = location))
    }

    fun selectMapLocation(location: WeatherLocation) {
        selectLocation(location)
        resolvePlaceName(location)
    }

    private fun resolvePlaceName(location: WeatherLocation) {
        val version = selectionVersion
        placeNameJob = viewModelScope.launch {
            // Rapid taps should resolve only the final point.
            delay(400.milliseconds)
            val label = try {
                placeNames[location.cacheKey] ?: locationRepository.reverse(getApplication(), location)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
            if (label != null && version == selectionVersion &&
                _preferences.value.city.cacheKey == location.cacheKey
            ) {
                placeNames[location.cacheKey] = label
                if (placeNames.size > 32) placeNames.remove(placeNames.keys.first())
                // Keep the exact tapped coordinates and the timezone returned by the forecast.
                val current = _preferences.value
                updatePreferences(current.copy(city = current.city.copy(label = label)))
            }
        }
    }

    fun searchLocations(query: String) {
        searchJob?.cancel()
        val cleanQuery = query.trim()
        _locationSearch.value = LocationSearchState(query = query, isSearching = cleanQuery.length >= 2)
        if (cleanQuery.length < 2) return
        searchJob = viewModelScope.launch {
            delay(400.milliseconds)
            try {
                val results = locationRepository.search(cleanQuery)
                _locationSearch.value = LocationSearchState(
                    query = query, results = results, hasSearched = true
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _locationSearch.value = LocationSearchState(
                    query = query, error = "Could not search locations. Check your connection and try again.",
                    hasSearched = true
                )
            }
        }
    }

    fun refreshWeather() {
        if (refreshJob?.isActive == true) return
        val city = _preferences.value.city
        val version = ++requestVersion
        refreshJob = viewModelScope.launch {
            _isRefreshing.value = true
            try {
                val result = repository.getWeather(city)
                if (version == requestVersion) {
                    // Map coordinates do not know their timezone until the forecast returns.
                    updatePreferences(_preferences.value.copy(
                        city = _preferences.value.city.copy(timeZone = result.timeZone)
                    ))
                    _uiState.value = WeatherUiState.Success(result)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (version == requestVersion) {
                    _uiState.value = WeatherUiState.Error(
                        "Weather unavailable for this location. Check your connection or choose another point."
                    )
                }
            } finally {
                if (version == requestVersion) _isRefreshing.value = false
            }
        }
    }
}
