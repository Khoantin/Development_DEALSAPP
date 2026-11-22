package com.example.androidapp

import com.example.androidapp.pages.weather.weatherMapSelection
import org.junit.Assert.*
import org.junit.Test

class WeatherMapSelectionTest {
    @Test
    fun mapTapSelectsExactCoordinates() {
        val selected = requireNotNull(weatherMapSelection("53.5461", "-113.4938"))
        assertEquals(53.5461, selected.latitude, 0.0)
        assertEquals(-113.4938, selected.longitude, 0.0)
        assertEquals("53.5461, -113.4938", selected.label)
    }

    @Test
    fun equatorAndDateLineAreValidSelections() {
        assertNotNull(weatherMapSelection("0", "0"))
        assertNotNull(weatherMapSelection("-90", "-180"))
        assertNotNull(weatherMapSelection("90", "180"))
    }

    @Test
    fun malformedOrMissingMapEventsAreIgnored() {
        assertNull(weatherMapSelection(null, "0"))
        assertNull(weatherMapSelection("0", null))
        assertNull(weatherMapSelection("not-a-number", "0"))
        assertNull(weatherMapSelection("0);alert(1)", "0"))
    }

    @Test
    fun nonFiniteAndOutOfRangeCoordinatesAreIgnored() {
        listOf("NaN", "Infinity", "-Infinity", "90.01", "-90.01").forEach {
            assertNull(weatherMapSelection(it, "0"))
        }
        listOf("NaN", "Infinity", "180.01", "-180.01").forEach {
            assertNull(weatherMapSelection("0", it))
        }
    }
}
