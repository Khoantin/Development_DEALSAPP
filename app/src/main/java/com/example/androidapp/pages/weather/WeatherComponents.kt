package com.example.androidapp.pages.weather

import com.example.androidapp.repository.DailyWeather
import com.example.androidapp.repository.HourlyWeather
import com.example.androidapp.repository.weatherEmoji

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter


// ---------------------------------------------------------
// HOURLY FORECAST
// ---------------------------------------------------------

@Composable
fun HourlyForecastCard(
    hourlyForecast: List<HourlyWeather>
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {

        Column(
            modifier = Modifier.padding(18.dp)
        ) {

            Text(
                text = "Hourly Forecast",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(18.dp)
            )

            Row(
                modifier = Modifier.horizontalScroll(
                    rememberScrollState()
                )
            ) {

                hourlyForecast.forEachIndexed {
                        index,
                        hour ->

                    HourWeather(
                        time =
                            if (index == 0) {
                                "Now"
                            } else {
                                formatHour(hour.time)
                            },

                        icon =
                            weatherEmoji(
                                hour.weatherCode
                            ),

                        temperature =
                            LocalWeatherPreferences.current.temperature(hour.temperature)
                    )
                }
            }
        }
    }
}


@Composable
fun HourWeather(
    time: String,
    icon: String,
    temperature: String
) {

    Column(
        modifier = Modifier.width(70.dp),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text = time,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Text(
            text = icon,
            fontSize = 26.sp
        )

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = temperature,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium
        )
    }
}


// ---------------------------------------------------------
// SUNRISE / SUNSET
// ---------------------------------------------------------

@Composable
fun SunCard(
    sunrise: LocalDateTime,
    sunset: LocalDateTime
) {

    val sunriseText =
        formatSunTime(sunrise)

    val sunsetText =
        formatSunTime(sunset)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {

        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Text(
                text = "☀️",
                fontSize = 32.sp
            )

            Spacer(
                modifier = Modifier.height(5.dp)
            )

            Text(
                text = "Sunset $sunsetText",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(20.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = "🌅",
                    fontSize = 22.sp
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp)
                        .height(2.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.65f
                            )
                        )
                )

                Text(
                    text = "🌇",
                    fontSize = 22.sp
                )
            }

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Column {

                    Text(
                        text = "Sunrise",
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.75f
                            ),
                        fontSize = 13.sp
                    )

                    Text(
                        text = sunriseText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                }

                Column(
                    horizontalAlignment =
                        Alignment.End
                ) {

                    Text(
                        text = "Sunset",
                        color =
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.75f
                            ),
                        fontSize = 13.sp
                    )

                    Text(
                        text = sunsetText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}


// ---------------------------------------------------------
// 7 DAY FORECAST
// ---------------------------------------------------------

@Composable
fun WeeklyForecastCard(
    forecast: List<DailyWeather>
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {

        Column(
            modifier = Modifier.padding(18.dp)
        ) {

            Text(
                text = "7-Day Forecast",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(
                modifier = Modifier.height(10.dp)
            )

            forecast.forEachIndexed {
                    index,
                    day ->

                ForecastRow(
                    day =
                        if (index == 0) {
                            "Today"
                        } else {
                            day.date.format(
                                DateTimeFormatter
                                    .ofPattern("EEEE")
                            )
                        },

                    icon =
                        weatherEmoji(
                            day.weatherCode
                        ),

                    low =
                        LocalWeatherPreferences.current.temperature(day.low),

                    high =
                        LocalWeatherPreferences.current.temperature(day.high)
                )
            }
        }
    }
}


@Composable
fun ForecastRow(
    day: String,
    icon: String,
    low: String,
    high: String
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Text(
            text = day,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = icon,
            fontSize = 22.sp,
            modifier = Modifier.weight(0.5f)
        )

        Text(
            text = low,
            color =
                MaterialTheme.colorScheme.onSurfaceVariant.copy(
                    alpha = 0.65f
                ),
            fontSize = 15.sp,
            modifier = Modifier.width(45.dp)
        )

        Text(
            text = high,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(45.dp)
        )
    }
}


// ---------------------------------------------------------
// FORMATTING
// ---------------------------------------------------------

private fun formatHour(
    time: LocalDateTime
): String {

    return time.format(
        DateTimeFormatter.ofPattern(
            "h a"
        )
    )
}


private fun formatSunTime(
    time: LocalDateTime
): String {

    return time.format(
        DateTimeFormatter.ofPattern(
            "h:mm a"
        )
    )
}