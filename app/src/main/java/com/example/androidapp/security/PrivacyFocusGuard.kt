package com.example.androidapp.security

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView

val LocalPrivacyExternalInteraction = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

val LocalPrivacyFocusGuard = staticCompositionLocalOf<PrivacyFocusGuard?> { null }

/** Main-thread-only focus tracking for the activity and its registered dialogs. */
class PrivacyFocusGuard(
    private val shouldExit: () -> Boolean,
    private val onExit: () -> Unit
) {
    private data class Registration(
        val observer: ViewTreeObserver,
        val listener: ViewTreeObserver.OnWindowFocusChangeListener,
        var references: Int = 1
    )

    private val handler = Handler(Looper.getMainLooper())
    private val windows = mutableMapOf<View, Registration>()
    private var observedFocus = false
    private var awaitingExpectedReturn = false
    private var disposed = false

    private val checkFocus = Runnable {
        if (!disposed && !hasFocusedWindow() && observedFocus &&
            !awaitingExpectedReturn && shouldExit()
        ) {
            onExit()
        }
    }

    private fun hasFocusedWindow(): Boolean =
        windows.keys.any { it.hasWindowFocus() }

    private fun focusChanged() {
        if (disposed) return
        handler.removeCallbacks(checkFocus)
        if (hasFocusedWindow()) {
            observedFocus = true
            awaitingExpectedReturn = false
        } else {
            // Allow activity-to-dialog focus transfer to settle.
            handler.postDelayed(checkFocus, 120L)
        }
    }

    fun awaitExpectedFocusReturn() {
        if (disposed) return
        awaitingExpectedReturn = !hasFocusedWindow()
        focusChanged()
    }

    fun registerWindow(view: View): () -> Unit {
        if (disposed) return {}
        val root = view.rootView
        val existing = windows[root]
        if (existing != null) {
            existing.references++
        } else {
            val observer = root.viewTreeObserver
            val listener = ViewTreeObserver.OnWindowFocusChangeListener {
                focusChanged()
            }
            observer.addOnWindowFocusChangeListener(listener)
            windows[root] = Registration(observer, listener)
        }
        focusChanged()
        var released = false
        return {
            if (!released) {
                released = true
                val registration = windows[root]
                if (registration != null) {
                    registration.references--
                    if (registration.references == 0) {
                        if (registration.observer.isAlive) {
                            registration.observer.removeOnWindowFocusChangeListener(
                                registration.listener
                            )
                        }
                        windows.remove(root)
                    }
                }
                focusChanged()
            }
        }
    }

    fun dispose() {
        disposed = true
        handler.removeCallbacks(checkFocus)
        windows.values.forEach { registration ->
            if (registration.observer.isAlive) {
                registration.observer.removeOnWindowFocusChangeListener(
                    registration.listener
                )
            }
        }
        windows.clear()
    }
}

/** Call inside a dialog's content so its window counts as part of the app. */
@Composable
fun PrivacyDialogFocus() {
    val guard = LocalPrivacyFocusGuard.current
    val view = LocalView.current
    DisposableEffect(guard, view) {
        val release = guard?.registerWindow(view)
        onDispose { release?.invoke() }
    }
}
