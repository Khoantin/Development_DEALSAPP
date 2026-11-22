package com.example.androidapp.repository

import com.example.androidapp.pages.weather.WeatherLocation
import com.example.androidapp.pages.weather.WeatherCity

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.roundToInt


data class HourlyWeather(
    val time: LocalDateTime,
    val temperature: Int,
    val weatherCode: Int
)


data class DailyWeather(
    val date: LocalDate,
    val weatherCode: Int,
    val low: Int,
    val high: Int,
    val sunrise: LocalDateTime,
    val sunset: LocalDateTime
)


data class WeatherData(
    val currentTemperature: Int,
    val feelsLike: Int,
    val currentWeatherCode: Int,
    val todayHigh: Int,
    val todayLow: Int,
    val sunrise: LocalDateTime,
    val sunset: LocalDateTime,
    val hourlyForecast: List<HourlyWeather>,
    val dailyForecast: List<DailyWeather>,
    val windSpeedKmh: Double? = null
)


data class WeatherResult(
    val weather: WeatherData,
    val isOffline: Boolean,
    val lastUpdatedMillis: Long,
    val timeZone: String = "America/Edmonton"
)


class WeatherRepository(
    context: Context
) {

    private val preferences =
        context.getSharedPreferences(
            "weather_cache",
            Context.MODE_PRIVATE
        )

    companion object {
        private const val CACHED_JSON = "cached_weather_json"
        private const val LAST_UPDATED = "weather_last_updated"
    }


    suspend fun getWeather(city: WeatherLocation = WeatherCity.EDMONTON.location): WeatherResult =
        withContext(Dispatchers.IO) {

            try {
                val json = downloadWeather(city)
                currentCoroutineContext().ensureActive()
                val weather = parseWeather(json, city)

                val now = System.currentTimeMillis()

                // Save the last successful weather response.
                cacheWeather(city, json, now)

                WeatherResult(
                    weather = weather,
                    isOffline = false,
                    lastUpdatedMillis = now,
                    timeZone = JSONObject(json).optString("timezone", city.timeZone)
                )

            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {

                // Internet failed. Try the most recent saved weather.
                val cachedJson =
                    preferences.getString(
                        "${CACHED_JSON}_${city.cacheKey}",
                        null
                    )

                if (cachedJson != null) {

                    WeatherResult(
                        weather = parseWeather(cachedJson, city),
                        isOffline = true,
                        lastUpdatedMillis =
                            preferences.getLong(
                                "${LAST_UPDATED}_${city.cacheKey}",
                                0L
                            ),
                        timeZone = JSONObject(cachedJson).optString("timezone", city.timeZone)
                    )

                } else {
                    // No internet AND the app has never successfully
                    // downloaded weather before.
                    throw e
                }
            }
        }


    private fun cacheWeather(location: WeatherLocation, json: String, updated: Long) {
        // Map exploration must not grow SharedPreferences without a limit.
        val previous = preferences.getString("recent_locations", "").orEmpty()
            .split("|").filter { it.isNotBlank() }
        val recent = (listOf(location.cacheKey) + previous).distinct().take(12)
        preferences.edit {
            putString("recent_locations", recent.joinToString("|"))
            putString("${CACHED_JSON}_${location.cacheKey}", json)
            putLong("${LAST_UPDATED}_${location.cacheKey}", updated)
            previous.filterNot { it in recent }.forEach {
                remove("${CACHED_JSON}_$it")
                remove("${LAST_UPDATED}_$it")
            }
        }
    }

    private fun downloadWeather(city: WeatherLocation): String {

        val urlString =
            "https://api.open-meteo.com/v1/forecast" +
                    "?latitude=${city.latitude}" +
                    "&longitude=${city.longitude}" +
                    "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                    "&hourly=temperature_2m,weather_code" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset" +
                    "&timezone=auto" +
                    "&forecast_days=7"

        val connection =
            URL(urlString)
                .openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (
                connection.responseCode
                != HttpURLConnection.HTTP_OK
            ) {
                throw Exception(
                    "Weather request failed."
                )
            }

            return connection.inputStream
                .bufferedReader()
                .use { it.readText() }

        } finally {
            connection.disconnect()
        }
    }


    private fun parseWeather(
        jsonString: String,
        city: WeatherLocation
    ): WeatherData {

        val json =
            JSONObject(jsonString)

        // CURRENT WEATHER
        val current =
            json.getJSONObject("current")

        val currentTemperature =
            current
                .getDouble("temperature_2m")
                .roundToInt()

        val feelsLike =
            current
                .getDouble("apparent_temperature")
                .roundToInt()

        val currentWeatherCode =
            current.getInt("weather_code")


        // DAILY WEATHER
        val daily =
            json.getJSONObject("daily")

        val dates =
            daily.getJSONArray("time")

        val codes =
            daily.getJSONArray("weather_code")

        val highs =
            daily.getJSONArray(
                "temperature_2m_max"
            )

        val lows =
            daily.getJSONArray(
                "temperature_2m_min"
            )

        val sunrises =
            daily.getJSONArray("sunrise")

        val sunsets =
            daily.getJSONArray("sunset")


        val dailyForecast =
            mutableListOf<DailyWeather>()


        for (i in 0 until dates.length()) {

            dailyForecast.add(
                DailyWeather(
                    date =
                        LocalDate.parse(
                            dates.getString(i)
                        ),

                    weatherCode =
                        codes.getInt(i),

                    low =
                        lows.getDouble(i)
                            .roundToInt(),

                    high =
                        highs.getDouble(i)
                            .roundToInt(),

                    sunrise =
                        LocalDateTime.parse(
                            sunrises.getString(i)
                        ),

                    sunset =
                        LocalDateTime.parse(
                            sunsets.getString(i)
                        )
                )
            )
        }


        // HOURLY WEATHER
        val hourly =
            json.getJSONObject("hourly")

        val hourlyTimes =
            hourly.getJSONArray("time")

        val hourlyTemps =
            hourly.getJSONArray(
                "temperature_2m"
            )

        val hourlyCodes =
            hourly.getJSONArray(
                "weather_code"
            )


        val allHours =
            mutableListOf<HourlyWeather>()


        for (
        i in 0 until hourlyTimes.length()
        ) {

            allHours.add(
                HourlyWeather(
                    time =
                        LocalDateTime.parse(
                            hourlyTimes.getString(i)
                        ),

                    temperature =
                        hourlyTemps
                            .getDouble(i)
                            .roundToInt(),

                    weatherCode =
                        hourlyCodes.getInt(i)
                )
            )
        }


        val now =
            ZonedDateTime
                .now(
                    ZoneId.of(
                        json.optString("timezone", city.timeZone)
                    )
                )
                .toLocalDateTime()
                .withMinute(0)
                .withSecond(0)
                .withNano(0)


        val nextHours =
            allHours
                .filter {
                    !it.time.isBefore(now)
                }
                .take(8)


        val today =
            dailyForecast.first()


        return WeatherData(
            currentTemperature =
                currentTemperature,

            feelsLike =
                feelsLike,

            currentWeatherCode =
                currentWeatherCode,

            todayHigh =
                today.high,

            todayLow =
                today.low,

            sunrise =
                today.sunrise,

            sunset =
                today.sunset,

            hourlyForecast =
                nextHours,

            dailyForecast =
                dailyForecast,
            windSpeedKmh = if (current.has("wind_speed_10m") && !current.isNull("wind_speed_10m")) {
                current.getDouble("wind_speed_10m")
            } else null
        )
    }
}


fun weatherDescription(
    code: Int
): String {

    return when (code) {
        0 -> "Clear"
        1 -> "Mostly Clear"
        2 -> "Partly Cloudy"
        3 -> "Cloudy"

        45, 48 ->
            "Foggy"

        51, 53, 55, 56, 57 ->
            "Drizzle"

        61, 63, 65, 66, 67 ->
            "Rain"

        71, 73, 75, 77 ->
            "Snow"

        80, 81, 82 ->
            "Rain Showers"

        85, 86 ->
            "Snow Showers"

        95, 96, 99 ->
            "Thunderstorm"

        else ->
            "Cloudy"
    }
}


fun weatherEmoji(
    code: Int
): String {

    return when (code) {
        0 -> "☀️"
        1 -> "🌤️"
        2 -> "⛅"
        3 -> "☁️"

        45, 48 ->
            "🌫️"

        51, 53, 55, 56, 57 ->
            "🌦️"

        61, 63, 65, 66, 67 ->
            "🌧️"

        71, 73, 75, 77 ->
            "🌨️"

        80, 81, 82 ->
            "🌧️"

        85, 86 ->
            "🌨️"

        95, 96, 99 ->
            "⛈️"

        else ->
            "☁️"
    }
}