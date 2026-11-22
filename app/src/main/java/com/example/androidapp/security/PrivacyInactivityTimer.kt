package com.example.androidapp.security

import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Foreground inactivity only. External interactions and backgrounding pause it. */
class PrivacyInactivityTimer(
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) {
    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 120_000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var monitoring = false
    private var disposed = false
    private var lastInteractionMillis = 0L

    var onTimeout: (() -> Unit)? = null

    init {
        require(timeoutMillis > 0L)
    }

    private val timeoutCheck = object : Runnable {
        override fun run() {
            if (!monitoring || disposed) return
            val remaining = timeoutMillis -
                    (SystemClock.elapsedRealtime() - lastInteractionMillis)
            if (remaining > 0L) {
                handler.postDelayed(this, remaining)
            } else {
                monitoring = false
                onTimeout?.invoke()
            }
        }
    }

    fun setMonitoring(enabled: Boolean) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (disposed || monitoring == enabled) return
        monitoring = enabled
        handler.removeCallbacks(timeoutCheck)
        if (enabled) {
            lastInteractionMillis = SystemClock.elapsedRealtime()
            handler.postDelayed(timeoutCheck, timeoutMillis)
        }
    }

    fun recordInteraction() {
        // Input connections may deliver work from another thread.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            handler.post { recordInteraction() }
            return
        }
        if (!monitoring || disposed) return
        lastInteractionMillis = SystemClock.elapsedRealtime()
        handler.removeCallbacks(timeoutCheck)
        handler.postDelayed(timeoutCheck, timeoutMillis)
    }

    fun dispose() {
        check(Looper.myLooper() == Looper.getMainLooper())
        disposed = true
        monitoring = false
        onTimeout = null
        handler.removeCallbacksAndMessages(null)
    }
}
