package com.example.androidapp.pages.weather

data class LocationSearchState(
    val query: String = "",
    val results: List<WeatherLocation> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false
)
