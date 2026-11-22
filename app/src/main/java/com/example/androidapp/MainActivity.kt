package com.example.androidapp

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import com.example.androidapp.ui.theme.AndroidAppTheme
import com.example.androidapp.navigation.AppNavigation
import com.example.androidapp.security.PrivacyInactivityTimer
import com.example.androidapp.security.PrivacyInteractionTracking
import com.example.androidapp.ui.theme.AndroidAppTheme

class MainActivity : ComponentActivity() {

    private var screenshotMonitor: ScreenshotMonitor? = null
    private val inactivityTimer = PrivacyInactivityTimer()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            AndroidAppTheme {
                PrivacyInteractionTracking(inactivityTimer) {
                    AppNavigation(inactivityTimer = inactivityTimer)
                }
            }
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        inactivityTimer.recordInteraction()
    }

    override fun onStart() {
        super.onStart()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val monitor = screenshotMonitor
                ?: ScreenshotMonitor(this).also {
                    screenshotMonitor = it
                }

            monitor.start()
        }
    }

    override fun onStop() {
        inactivityTimer.setMonitoring(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            screenshotMonitor?.stop()
        }

        super.onStop()
    }

    override fun onDestroy() {
        inactivityTimer.dispose()
        super.onDestroy()
    }

    // Created only on Android 14 and newer.
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private class ScreenshotMonitor(
        private val activity: Activity
    ) {

        private var registered = false

        private val callback = Activity.ScreenCaptureCallback {
            Toast.makeText(
                activity,
                "Screenshot detected.",
                Toast.LENGTH_SHORT
            ).show()
        }

        fun start() {
            if (registered) return

            activity.registerScreenCaptureCallback(
                activity.mainExecutor,
                callback
            )

            registered = true
        }

        fun stop() {
            if (!registered) return

            activity.unregisterScreenCaptureCallback(callback)

            registered = false
        }
    }
}
