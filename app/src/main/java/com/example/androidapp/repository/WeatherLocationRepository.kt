package com.example.androidapp.repository

import com.example.androidapp.pages.weather.WeatherLocation

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class WeatherLocationRepository {
    /** Name lookup is independent of the forecast; failures leave the selected coordinates intact. */
    @Suppress("DEPRECATION")
    suspend fun reverse(context: Context, location: WeatherLocation): String? =
        withContext(Dispatchers.IO) {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(context.applicationContext, Locale.ENGLISH)
            val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                withTimeoutOrNull(8.seconds) {
                    suspendCancellableCoroutine<List<Address>> { continuation ->
                        geocoder.getFromLocation(location.latitude, location.longitude, 1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (continuation.isActive) continuation.resume(addresses)
                                }

                                override fun onError(errorMessage: String?) {
                                    if (continuation.isActive) continuation.resume(emptyList())
                                }
                            })
                    }
                }.orEmpty()
            } else {
                geocoder.getFromLocation(location.latitude, location.longitude, 1).orEmpty()
            }
            val place = addresses.firstOrNull() ?: return@withContext null
            val name = listOf(place.locality, place.subLocality, place.subAdminArea,
                place.adminArea, place.countryName).firstOrNull { !it.isNullOrBlank() }
            listOfNotNull(name, place.adminArea, place.countryName)
                .filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { null }
        }

    suspend fun search(query: String): List<WeatherLocation> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query.trim(), "UTF-8")
        val connection = URL(
            "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=10&language=en&format=json"
        ).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Location search failed" }
            val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
            val results = json.optJSONArray("results")
            buildList {
                if (results != null) {
                    for (index in 0 until results.length()) {
                        val place = results.getJSONObject(index)
                        val label = listOf(
                            place.getString("name"),
                            place.optString("admin1"),
                            place.optString("country")
                        ).filter { it.isNotBlank() }.distinct().joinToString(", ")
                        add(WeatherLocation(
                            label, place.getDouble("latitude"), place.getDouble("longitude"),
                            place.optString("timezone", "UTC")
                        ))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
