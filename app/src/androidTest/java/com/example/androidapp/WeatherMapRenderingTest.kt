package com.example.androidapp

import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.androidapp.pages.weather.*
import com.example.androidapp.repository.WeatherLocationRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Live network smoke test: verifies actual visible tiles on the device, then the selection bridge. */
@RunWith(AndroidJUnit4::class)
class WeatherMapRenderingTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun tilesAreVisibleAndMapTapSelectsCoordinatesInBothThemes() {
        val night = mutableStateOf(false)
        val selected = AtomicReference<WeatherLocation?>()
        val location = mutableStateOf(WeatherCity.EDMONTON.location)
        compose.setContent {
            WeatherPageTheme(WeatherPreferences(), night.value) {
                WeatherMapScreen(
                    location = location.value,
                    state = WeatherUiState.Error("Test weather panel"),
                    night = night.value,
                    isRefreshing = false,
                    onPointSelected = { selected.set(it); location.value = it },
                    onSearch = {},
                    onViewForecast = {},
                    onRefresh = {}
                )
            }
        }
        var webView: WebView? = null
        compose.runOnIdle {
            webView = findWebView(compose.activity.findViewById(android.R.id.content))
        }
        val mapView = requireNotNull(webView)
        awaitJavascript(mapView, """
            Boolean(window.map && document.querySelector('.leaflet-control-zoom') &&
              Array.from(document.querySelectorAll('.leaflet-tile-loaded')).some(function(tile) {
                var r = tile.getBoundingClientRect();
                return tile.complete && tile.naturalWidth > 0 &&
                  r.right > 0 && r.bottom > 0 && r.left < innerWidth && r.top < innerHeight;
              }))
        """.trimIndent())

        awaitJavascript(mapView, """
            Boolean(document.querySelector('.weather-pin') &&
              document.querySelector('.weather-pin').getBoundingClientRect().width > 15)
        """.trimIndent())
        assertMapPixelsVisible(mapView)
        compose.runOnIdle { night.value = true }
        awaitJavascript(mapView, "document.body.classList.contains('night')")
        // Theme changes must preserve the map and its visible tiles.
        awaitJavascript(mapView, """
            Boolean(document.querySelector('.leaflet-control-attribution') &&
              Array.from(document.querySelectorAll('.leaflet-tile-loaded')).some(function(tile) {
                var r = tile.getBoundingClientRect();
                return tile.naturalWidth > 0 && r.right > 0 && r.bottom > 0 &&
                  r.left < innerWidth && r.top < innerHeight;
              }))
        """.trimIndent())
        assertMapPixelsVisible(mapView)
        evaluate(mapView, "map.fire('click', {latlng:L.latLng(53.54, -113.49)}); true")
        val deadline = SystemClock.elapsedRealtime() + 5_000
        while (selected.get() == null && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(100)
        }
        val point = requireNotNull(selected.get()) { "Map tap did not reach the Weather selection callback" }
        assertEquals(53.54, point.latitude, 1e-9)
        assertEquals(-113.49, point.longitude, 1e-9)
        awaitJavascript(mapView, "Boolean(window.map && document.querySelector('.leaflet-control-zoom'))")
        awaitJavascript(mapView, """
            Math.abs(marker.getLatLng().lat - 53.54) < 0.000001 &&
              Math.abs(marker.getLatLng().lng + 113.49) < 0.000001
        """.trimIndent())
    }

    @Test
    fun selectedCoordinatesResolveToAPlaceName() {
        val point = WeatherCity.EDMONTON.location
        val label = runBlocking {
            WeatherLocationRepository().reverse(compose.activity, point)
        }
        assertNotNull("The connected device did not return a place name", label)
        assertTrue("Expected Edmonton in resolved name: $label", label!!.contains("Edmonton"))
    }

    private fun assertMapPixelsVisible(webView: WebView) {
        // Check the rendered window, not just DOM nodes: a blank WebView can still load tiles.
        val bitmap = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
        val finished = CountDownLatch(1)
        val status = AtomicReference<Int>()
        compose.runOnIdle {
            val position = IntArray(2)
            webView.getLocationInWindow(position)
            val bounds = Rect(position[0], position[1],
                position[0] + webView.width, position[1] + webView.height)
            PixelCopy.request(compose.activity.window, bounds, bitmap, {
                status.set(it)
                finished.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        assertTrue("Map screenshot timed out", finished.await(5, TimeUnit.SECONDS))
        assertEquals(PixelCopy.SUCCESS, status.get().toInt())
        val colors = mutableSetOf<Int>()
        // Sample away from map buttons and attribution, which could mask a blank tile pane.
        for (x in bitmap.width / 4 until bitmap.width * 3 / 4 step 10) {
            for (y in bitmap.height / 4 until bitmap.height * 3 / 4 step 10) {
                colors += bitmap.getPixel(x, y) and 0x00F8F8F8
            }
        }
        bitmap.recycle()
        assertTrue("Map is blank: only ${colors.size} rendered colors", colors.size >= 8)
    }

    private fun awaitJavascript(webView: WebView, expression: String) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        var result: String?
        do {
            result = evaluate(webView, expression)
            if (result == "true") return
            SystemClock.sleep(250)
        } while (SystemClock.elapsedRealtime() < deadline)
        val diagnostic = evaluate(webView, """
            JSON.stringify({url:location.href, base:document.baseURI, body:document.body.className,
              size:[innerWidth,innerHeight], leaflet:typeof L,
              mapBounds:document.getElementById('map')?.getBoundingClientRect().toJSON(),
              tiles:document.querySelectorAll('.leaflet-tile-loaded').length,
              styles:Array.from(document.styleSheets).map(function(s) {return s.href;}),
              scroll:[scrollX,scrollY]})
        """.trimIndent())
        fail("Map did not render correctly. Result: $result; diagnostics: $diagnostic")
    }

    private fun evaluate(webView: WebView, expression: String): String? {
        val result = AtomicReference<String?>()
        val finished = CountDownLatch(1)
        compose.runOnIdle {
            webView.evaluateJavascript(expression) {
                result.set(it)
                finished.countDown()
            }
        }
        assertTrue("WebView JavaScript did not respond", finished.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private fun findWebView(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                findWebView(view.getChildAt(index))?.let { return it }
            }
        }
        return null
    }
}
