package com.example.androidapp.pages.weather

import com.example.androidapp.repository.DailyWeather

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.LocalDateTime
import kotlin.math.roundToInt

enum class WeatherCity(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String
) {
    EDMONTON("Edmonton", 53.5461, -113.4938, "America/Edmonton"),
    CALGARY("Calgary", 51.0447, -114.0719, "America/Edmonton"),
    VANCOUVER("Vancouver", 49.2827, -123.1207, "America/Vancouver"),
    TORONTO("Toronto", 43.6532, -79.3832, "America/Toronto"),
    MONTREAL("Montreal", 45.5019, -73.5674, "America/Toronto");

    val location: WeatherLocation
        get() = WeatherLocation(label, latitude, longitude, timeZone)
}

data class WeatherLocation(
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String = "UTC"
) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
        java.time.ZoneId.of(timeZone)
    }

    // Forecasts belong to coordinates, not names: two places can have the same name.
    val cacheKey: String get() = "${latitude}_${longitude}"

    companion object {
        fun fromCoordinates(latitude: Double, longitude: Double) = WeatherLocation(
            label = String.format(java.util.Locale.US, "%.4f, %.4f", latitude, longitude),
            latitude = latitude,
            longitude = longitude
        )
    }
}

enum class WeatherTheme(val label: String) {
    AUTO("Auto"), DAY("Day"), NIGHT("Night")
}

data class WeatherPreferences(
    val fahrenheit: Boolean = false,
    val milesPerHour: Boolean = false,
    val city: WeatherLocation = WeatherCity.EDMONTON.location,
    val theme: WeatherTheme = WeatherTheme.AUTO
) {
    fun temperature(celsius: Int): String =
        "${if (fahrenheit) (celsius * 9.0 / 5.0 + 32).roundToInt() else celsius}°"

    fun windSpeed(kilometresPerHour: Double): String =
        "${(if (milesPerHour) kilometresPerHour / 1.609344 else kilometresPerHour).roundToInt()} ${if (milesPerHour) "mph" else "km/h"}"
}

val LocalWeatherPreferences = staticCompositionLocalOf { WeatherPreferences() }

fun isWeatherNight(
    theme: WeatherTheme,
    now: LocalDateTime,
    forecast: List<DailyWeather>
): Boolean = when (theme) {
    WeatherTheme.DAY -> false
    WeatherTheme.NIGHT -> true
    WeatherTheme.AUTO -> {
        val today = forecast.firstOrNull { it.date == now.toLocalDate() }
        if (today != null) {
            now.isBefore(today.sunrise) || !now.isBefore(today.sunset)
        } else {
            // Used before the first forecast loads or when the offline cache is old.
            now.hour < 6 || now.hour >= 18
        }
    }
}
