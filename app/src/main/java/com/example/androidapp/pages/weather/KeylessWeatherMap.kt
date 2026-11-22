package com.example.androidapp.pages.weather

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Leaflet/OpenStreetMap location picker. Uses HTTPS and normal WebView HTTP caching;
 * only visible tiles are requested, with attribution and an app-specific user agent.
 * No API key, device location permission or native JavaScript interface is needed.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun KeylessWeatherMap(
    location: WeatherLocation,
    night: Boolean,
    onPointSelected: (WeatherLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    var attempt by remember { mutableIntStateOf(0) }
    var ready by remember(attempt) { mutableStateOf(false) }
    var failed by remember(attempt) { mutableStateOf(false) }
    val currentLocation by rememberUpdatedState(location)
    val currentNight by rememberUpdatedState(night)
    val currentSelection by rememberUpdatedState(onPointSelected)

    LaunchedEffect(attempt) {
        delay(20.seconds)
        if (!ready) failed = true
    }

    Box(modifier) {
        key(attempt) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        // Keep WebView's tile compositor inside Compose's drawing layer.
                        // Hardware layers can appear blank/overdraw siblings on Samsung WebView.
                        setLayerType(android.view.View.LAYER_TYPE_SOFTWARE, null)
                        setBackgroundColor(if (currentNight) android.graphics.Color.BLACK
                            else android.graphics.Color.LTGRAY)
                        isVerticalScrollBarEnabled = false
                        isHorizontalScrollBarEnabled = false
                        addOnLayoutChangeListener { view, left, top, right, bottom,
                                                    oldLeft, oldTop, oldRight, oldBottom ->
                            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                                view.post {
                                    evaluateJavascript(
                                        "if(window.resizeWeatherMap) window.resizeWeatherMap();", null
                                    )
                                }
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.userAgentString = settings.userAgentString +
                            " WeatherNow/1.0 (" + context.packageName + ")"
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String?) {
                                view.evaluateJavascript(
                                    mapUpdateScript(currentLocation, currentNight), null
                                )
                            }

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                if (!request.isForMainFrame) return true
                                val uri = request.url
                                if (uri.scheme == "weathernow-map") {
                                    when (uri.host) {
                                        "ready" -> { ready = true; failed = false }
                                        "error" -> if (!ready) { failed = true }
                                        "pick" -> {
                                            weatherMapSelection(
                                                uri.getQueryParameter("lat"),
                                                uri.getQueryParameter("lon")
                                            )?.let(currentSelection)
                                        }
                                    }
                                } else if (request.hasGesture() && uri.scheme == "https" &&
                                    uri.host in setOf("www.openstreetmap.org", "leafletjs.com")
                                ) {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    }
                                }
                                // The map stays on our local page. Never load arbitrary links into it.
                                return true
                            }
                        }
                        loadDataWithBaseURL(
                            "https://appassets.androidplatform.net/weather-map/",
                            weatherMapHtml(currentLocation, currentNight),
                            "text/html", "UTF-8", null
                        )
                    }
                },
                update = { view ->
                    view.evaluateJavascript(mapUpdateScript(location, night), null)
                },
                onReset = null,
                onRelease = { view ->
                    view.stopLoading()
                    view.destroy()
                }
            )
        }
        if (!ready || failed) {
            Surface(
                modifier = Modifier.align(Alignment.Center).padding(16.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (failed) {
                        Text("Could not load the map. Check your internet connection.")
                        TextButton(onClick = { attempt++ }) { Text("Retry map") }
                    } else {
                        CircularProgressIndicator(Modifier.size(24.dp))
                        Text("Loading map…")
                    }
                }
            }
        }
    }
}

internal fun weatherMapSelection(latitude: String?, longitude: String?): WeatherLocation? {
    val lat = latitude?.toDoubleOrNull() ?: return null
    val lon = longitude?.toDoubleOrNull() ?: return null
    if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
        return null
    }
    return WeatherLocation.fromCoordinates(lat, lon)
}

private fun mapUpdateScript(location: WeatherLocation, night: Boolean) =
    "if (window.updateWeatherMap) window.updateWeatherMap(" +
        "${location.latitude},${location.longitude},$night);"

// Only validated numbers/booleans are interpolated; labels and other user text never enter HTML.
private fun weatherMapHtml(location: WeatherLocation, night: Boolean) = """
    <!doctype html>
    <html><head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
    <meta http-equiv="Content-Security-Policy"
      content="default-src 'none'; script-src 'unsafe-inline' https://unpkg.com;
        style-src 'unsafe-inline' https://unpkg.com;
        img-src data: https://tile.openstreetmap.org; base-uri 'none'; form-action 'none'">
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
      integrity="sha256-p4NxAoJBhIIN+hmNHrzRCf9tD/miZyoHS5obTRR9BMY=" crossorigin="">
    <style>
      html, body { height:100%; width:100%; margin:0; overflow:hidden; background:#e5e7eb; }
      #map { position:fixed; inset:0; background:#e5e7eb; }
      body.night #map { background:#121212; }
      body.night .leaflet-tile-pane { filter:grayscale(.8) invert(.92) hue-rotate(180deg) brightness(.8); }
      body.night .leaflet-control-zoom a { background:#242424; color:#9bcbff; border-color:#444; }
      .leaflet-control-attribution { font:11px sans-serif; }
      .weather-pointer { background:none; border:0; }
      .weather-pin { display:block; position:absolute; left:3px; top:3px;
        width:24px; height:24px; box-sizing:border-box; background:#2583e5;
        border:3px solid white; border-radius:50% 50% 50% 0; transform:rotate(-45deg);
        box-shadow:0 2px 6px #0008; }
      .weather-pin::after { content:''; position:absolute; width:6px; height:6px;
        border-radius:50%; background:white; left:6px; top:6px; }
    </style>
    </head><body><div id="map" aria-label="Tap the map to choose a weather location"></div>
    <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"
      integrity="sha256-20nQCchB9co0qIjJZRGuk2/Z9VM+kNiyxNV1lvTlZBo=" crossorigin=""
      onerror="location.href='weathernow-map://error'"></script>
    <script>
      if (window.L) {
        var map = L.map('map', {worldCopyJump:true}).setView(
          [${location.latitude}, ${location.longitude}], 7);
        var marker = L.marker([${location.latitude}, ${location.longitude}], {
          icon:L.divIcon({className:'weather-pointer',
            html:'<span class="weather-pin" aria-hidden="true"></span>',
            iconSize:[30,38], iconAnchor:[15,35]}),
          title:'Selected weather location', alt:'Selected weather location'
        }).addTo(map);
        var tiles = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom:19,
          attribution:'&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        });
        var hasTiles = false;
        tiles.on('tileload', function() {
          if (!hasTiles) { hasTiles = true; location.href='weathernow-map://ready'; }
        });
        tiles.addTo(map);
        map.on('click', function(event) {
          var point = event.latlng.wrap();
          marker.setLatLng(point);
          location.href='weathernow-map://pick?lat=' + point.lat + '&lon=' + point.lng;
        });
        window.resizeWeatherMap = function() {
          requestAnimationFrame(function() {
            map.invalidateSize({pan:false, animate:false});
          });
        };
        new ResizeObserver(window.resizeWeatherMap).observe(document.getElementById('map'));
        window.addEventListener('resize', window.resizeWeatherMap);
        window.updateWeatherMap = function(lat, lon, night) {
          window.resizeWeatherMap();
          document.body.classList.toggle('night', night);
          var previous = marker.getLatLng();
          if (previous.lat !== lat || previous.lng !== lon) {
            marker.setLatLng([lat, lon]);
            map.panTo([lat, lon]);
          }
        };
        window.updateWeatherMap(${location.latitude}, ${location.longitude}, $night);
      }
    </script></body></html>
""".trimIndent()
