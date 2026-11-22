package com.example.androidapp

import com.example.androidapp.pages.weather.*
import com.example.androidapp.repository.DailyWeather
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class WeatherPreferencesTest {
    private val date = LocalDate.of(2026, 11, 3)
    private val forecast = listOf(
        DailyWeather(date, 0, -2, 8, date.atTime(7, 30), date.atTime(17, 0))
    )

    @Test
    fun automaticThemeChangesAtSunriseAndSunset() {
        assertTrue(isWeatherNight(WeatherTheme.AUTO, date.atTime(7, 29), forecast))
        assertFalse(isWeatherNight(WeatherTheme.AUTO, date.atTime(7, 30), forecast))
        assertFalse(isWeatherNight(WeatherTheme.AUTO, date.atTime(16, 59), forecast))
        assertTrue(isWeatherNight(WeatherTheme.AUTO, date.atTime(17, 0), forecast))
    }

    @Test
    fun staleForecastUsesClockFallback() {
        val tomorrow = date.plusDays(1)
        assertTrue(isWeatherNight(WeatherTheme.AUTO, tomorrow.atTime(5, 59), forecast))
        assertFalse(isWeatherNight(WeatherTheme.AUTO, tomorrow.atTime(6, 0), forecast))
        assertTrue(isWeatherNight(WeatherTheme.AUTO, tomorrow.atTime(18, 0), forecast))
    }

    @Test
    fun manualThemeOverridesDaylight() {
        assertFalse(isWeatherNight(WeatherTheme.DAY, date.atTime(23, 0), forecast))
        assertTrue(isWeatherNight(WeatherTheme.NIGHT, date.atTime(12, 0), forecast))
    }

    @Test
    fun temperatureConversionHandlesFreezingAndNegativeValues() {
        val settings = WeatherPreferences(fahrenheit = true)
        assertEquals("32°", settings.temperature(0))
        assertEquals("-40°", settings.temperature(-40))
        assertEquals("68°", settings.temperature(20))
        assertEquals("-5°", WeatherPreferences().temperature(-5))
    }

    @Test
    fun sameNamePlacesHaveSeparateForecastCaches() {
        val canada = WeatherLocation("London", 42.9849, -81.2453, "America/Toronto")
        val england = WeatherLocation("London", 51.5074, -0.1278, "Europe/London")
        assertNotEquals(canada.cacheKey, england.cacheKey)
        assertEquals(canada.cacheKey, canada.copy(label = "London, Ontario").cacheKey)
    }

    @Test
    fun mapCoordinatesUseLocaleIndependentLabels() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.GERMANY)
            val point = WeatherLocation.fromCoordinates(-33.8688, 151.2093)
            assertEquals("-33.8688, 151.2093", point.label)
            assertEquals(-33.8688, point.latitude, 0.0)
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidCoordinatesAreRejected() {
        WeatherLocation.fromCoordinates(91.0, 0.0)
    }

    @Test
    fun windConversionUsesSelectedUnit() {
        assertEquals("16 km/h", WeatherPreferences().windSpeed(16.09344))
        assertEquals("10 mph", WeatherPreferences(milesPerHour = true).windSpeed(16.09344))
    }
}
