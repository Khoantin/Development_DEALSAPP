package com.example.androidapp.pages.weather

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import java.time.LocalDateTime
import java.time.ZoneId
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val WeatherBlue = Color(0xFF4A90E2)

// Resolve Auto from the same selected location and forecast on every Weather route.
@Composable
internal fun rememberWeatherNight(preferences: WeatherPreferences, state: WeatherUiState): Boolean {
    var now by remember(preferences.city) {
        mutableStateOf(LocalDateTime.now(ZoneId.of(preferences.city.timeZone)))
    }
    LaunchedEffect(preferences.city) {
        while (true) {
            now = LocalDateTime.now(ZoneId.of(preferences.city.timeZone))
            delay(30.seconds)
        }
    }
    val forecast = (state as? WeatherUiState.Success)?.result?.weather?.dailyForecast.orEmpty()
    return isWeatherNight(preferences.theme, now, forecast)
}

@Composable
internal fun WeatherPageTheme(
    preferences: WeatherPreferences,
    night: Boolean,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalWeatherPreferences provides preferences) {
        MaterialTheme(colorScheme = weatherColorScheme(night)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content
            )
        }
    }
}

// Every weather tab and dialog uses the same blue/white or black/blue palette.
fun weatherColorScheme(night: Boolean) = if (night) {
    darkColorScheme(
        primary = Color(0xFF9BCBFF),
        onPrimary = Color(0xFF102E49),
        primaryContainer = Color(0xFF24364B),
        onPrimaryContainer = Color.White,
        secondary = Color(0xFF9BCBFF),
        onSecondary = Color(0xFF102E49),
        secondaryContainer = Color(0xFF24364B),
        onSecondaryContainer = Color.White,
        tertiary = Color(0xFF9BCBFF),
        onTertiary = Color(0xFF102E49),
        tertiaryContainer = Color(0xFF24364B),
        onTertiaryContainer = Color.White,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color(0xFF121212),
        onSurface = Color.White,
        surfaceVariant = Color(0xFF242424),
        onSurfaceVariant = Color(0xFFDDDDDD),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF121212),
        surfaceContainer = Color(0xFF181818),
        surfaceContainerHigh = Color(0xFF242424),
        surfaceContainerHighest = Color(0xFF303030),
        surfaceTint = Color.Transparent,
        surfaceDim = Color(0xFF121212),
        surfaceBright = Color(0xFF303030),
        inverseSurface = Color(0xFFDDDDDD),
        inverseOnSurface = Color(0xFF181818),
        inversePrimary = Color(0xFF174F87),
        outline = Color(0xFF909090),
        outlineVariant = Color(0xFF444444)
    )
} else {
    lightColorScheme(
        primary = Color.White,
        onPrimary = Color(0xFF174F87),
        primaryContainer = Color(0xFF3275BD),
        onPrimaryContainer = Color.White,
        secondary = Color.White,
        onSecondary = Color(0xFF174F87),
        secondaryContainer = Color(0xFF3275BD),
        onSecondaryContainer = Color.White,
        tertiary = Color.White,
        onTertiary = Color(0xFF174F87),
        tertiaryContainer = Color(0xFF3275BD),
        onTertiaryContainer = Color.White,
        background = WeatherBlue,
        onBackground = Color.White,
        surface = WeatherBlue,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF6BA5E7),
        onSurfaceVariant = Color.White,
        surfaceContainerLowest = WeatherBlue,
        surfaceContainerLow = Color(0xFF5095E3),
        surfaceContainer = Color(0xFF589AE4),
        surfaceContainerHigh = Color(0xFF62A0E6),
        surfaceContainerHighest = Color(0xFF6BA5E7),
        surfaceTint = Color.Transparent,
        outline = Color.White.copy(alpha = 0.65f),
        outlineVariant = Color.White.copy(alpha = 0.25f),
        error = Color(0xFFFFE0E0),
        onError = Color(0xFF721C24),
        errorContainer = Color(0xFF8F2732),
        onErrorContainer = Color.White
    )
}
