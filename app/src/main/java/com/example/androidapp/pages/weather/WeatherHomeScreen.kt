package com.example.androidapp.pages.weather

import com.example.androidapp.repository.weatherDescription
import com.example.androidapp.repository.weatherEmoji

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter


@Composable
fun WeatherHomeScreen(
    state: WeatherUiState,
    onRefresh: () -> Unit = {},
    isRefreshing: Boolean = false,
    night: Boolean = false,
    onChangeLocation: () -> Unit = {}
) {
    val uiState = state
    val preferences = LocalWeatherPreferences.current

    var localTime by remember(preferences.city) {
        mutableStateOf(
            getWeatherTime(preferences.city.timeZone)
        )
    }


    // Keep the selected city's clock live,
    // even without internet.
    LaunchedEffect(preferences.city) {

        while (true) {

            localTime =
                getWeatherTime(preferences.city.timeZone)

            delay(1.seconds)
        }
    }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(
                horizontal = 20.dp
            )
    ) {

        Spacer(
            modifier =
                Modifier.height(30.dp)
        )


        // LOCATION + CLOCK + REFRESH
        Box(
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Column(
                modifier =
                    Modifier.align(
                        Alignment.Center
                    ).padding(horizontal = 48.dp),

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Row(
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.LocationOn,

                        contentDescription =
                            "Location",

                        tint =
                            MaterialTheme.colorScheme.onBackground,

                        modifier =
                            Modifier.size(22.dp)
                    )


                    Spacer(
                        modifier =
                            Modifier.width(5.dp)
                    )


                    Text(
                        text = preferences.city.label,
                        maxLines = 2,

                        color =
                            MaterialTheme.colorScheme.onBackground,

                        fontSize =
                            22.sp,

                        fontWeight =
                            FontWeight.Medium
                    )
                }


                Spacer(
                    modifier =
                        Modifier.height(3.dp)
                )


                Text(
                    text =
                        localTime,

                    color =
                        MaterialTheme.colorScheme.onBackground.copy(
                            alpha = 0.85f
                        ),

                    fontSize =
                        14.sp
                )
            }


            IconButton(
                enabled = !isRefreshing,
                onClick = {
                    onRefresh()
                },

                modifier =
                    Modifier.align(
                        Alignment.CenterEnd
                    )
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Refresh,

                    contentDescription =
                        if (isRefreshing) "Refreshing weather" else "Refresh weather",

                    tint =
                        MaterialTheme.colorScheme.onBackground
                )
            }
        }


        Spacer(
            modifier =
                Modifier.height(12.dp)
        )


        TextButton(
            onClick = onChangeLocation,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Change location", color = MaterialTheme.colorScheme.onBackground)
        }

        // OFFLINE NOTICE
        val currentState = uiState

        if (
            currentState
                    is WeatherUiState.Success &&
            currentState.result.isOffline
        ) {

            Text(
                text =
                    "⚠ Offline • Showing weather from ${
                        formatLastUpdated(
                            currentState
                                .result
                                .lastUpdatedMillis,
                            preferences.city.timeZone
                        )
                    }",

                color =
                    MaterialTheme.colorScheme.onBackground.copy(
                        alpha = 0.9f
                    ),

                fontSize =
                    13.sp,

                modifier =
                    Modifier.align(
                        Alignment.CenterHorizontally
                    )
            )
        }


        Spacer(
            modifier =
                Modifier.height(15.dp)
        )


        when (
            val state = uiState
        ) {

            WeatherUiState.Loading -> {

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(80.dp),

                    contentAlignment =
                        Alignment.Center
                ) {

                    CircularProgressIndicator(
                        color =
                            MaterialTheme.colorScheme.onBackground
                    )
                }
            }


            is WeatherUiState.Error -> {

                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(40.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text =
                            "Weather unavailable",

                        color =
                            MaterialTheme.colorScheme.onBackground,

                        fontSize =
                            20.sp,

                        fontWeight =
                            FontWeight.SemiBold
                    )


                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )


                    Text(
                        text =
                            state.message,

                        color =
                            MaterialTheme.colorScheme.onBackground.copy(
                                alpha = 0.8f
                            )
                    )
                }
            }


            is WeatherUiState.Success -> {

                val weather =
                    state.result.weather


                Column(
                    modifier =
                        Modifier.fillMaxWidth(),

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text =
                            if (night && weather.currentWeatherCode in 0..1) "🌙" else weatherEmoji(weather.currentWeatherCode),

                        fontSize =
                            58.sp
                    )


                    Text(
                        text =
                            preferences.temperature(weather.currentTemperature),

                        color =
                            MaterialTheme.colorScheme.onBackground,

                        fontSize =
                            72.sp,

                        fontWeight =
                            FontWeight.Light
                    )


                    Text(
                        text =
                            weatherDescription(
                                weather
                                    .currentWeatherCode
                            ),

                        color =
                            MaterialTheme.colorScheme.onBackground,

                        fontSize =
                            22.sp,

                        fontWeight =
                            FontWeight.Medium
                    )


                    Spacer(
                        modifier =
                            Modifier.height(14.dp)
                    )


                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(
                                35.dp
                            )
                    ) {

                        Text(
                            text =
                                "↑ ${preferences.temperature(weather.todayHigh)}",

                            color =
                                MaterialTheme.colorScheme.onBackground,

                            fontSize =
                                18.sp
                        )


                        Text(
                            text =
                                "↓ ${preferences.temperature(weather.todayLow)}",

                            color =
                                MaterialTheme.colorScheme.onBackground,

                            fontSize =
                                18.sp
                        )
                    }


                    Spacer(
                        modifier =
                            Modifier.height(8.dp)
                    )


                    Text(
                        text =
                            "Feels like ${preferences.temperature(weather.feelsLike)}",

                        color =
                            MaterialTheme.colorScheme.onBackground.copy(
                                alpha = 0.85f
                            ),

                        fontSize =
                            15.sp
                    )
                }


                Spacer(
                    modifier =
                        Modifier.height(32.dp)
                )


                weather.windSpeedKmh?.let { wind ->
                    Text(
                        text = "Wind ${preferences.windSpeed(wind)}",
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    Spacer(Modifier.height(16.dp))
                }

                HourlyForecastCard(
                    hourlyForecast =
                        weather.hourlyForecast
                )


                Spacer(
                    modifier =
                        Modifier.height(20.dp)
                )


                SunCard(
                    sunrise =
                        weather.sunrise,

                    sunset =
                        weather.sunset
                )


                Spacer(
                    modifier =
                        Modifier.height(20.dp)
                )


                WeeklyForecastCard(
                    forecast =
                        weather.dailyForecast
                )


                Spacer(
                    modifier =
                        Modifier.height(30.dp)
                )
            }
        }
    }
}


private fun getWeatherTime(timeZone: String): String {

    return ZonedDateTime
        .now(
            ZoneId.of(
                timeZone
            )
        )
        .format(
            DateTimeFormatter.ofPattern(
                "EEE, MMM d • h:mm:ss a"
            )
        )
}


private fun formatLastUpdated(
    millis: Long,
    timeZone: String
): String {

    if (millis == 0L) {
        return "earlier"
    }

    return Instant
        .ofEpochMilli(millis)
        .atZone(
            ZoneId.of(
                timeZone
            )
        )
        .format(
            DateTimeFormatter
                .ofPattern(
                    "h:mm a"
                )
        )
}