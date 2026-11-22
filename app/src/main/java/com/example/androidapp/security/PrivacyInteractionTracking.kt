package com.example.androidapp.security

import android.os.Build
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.TextAttribute
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

val LocalPrivacyInactivityTimer =
    staticCompositionLocalOf<PrivacyInactivityTimer?> { null }

/** Observe input without consuming gestures or keyboard events. */
@Composable
fun Modifier.trackPrivacyInteraction(): Modifier {
    val timer = LocalPrivacyInactivityTimer.current
    return this
        .pointerInput(timer) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Scroll ||
                        event.changes.any { it.pressed || it.previousPressed }
                    ) {
                        timer?.recordInteraction()
                    }
                }
            }
        }
        .onPreviewKeyEvent {
            timer?.recordInteraction()
            false
        }
}

/** Covers Compose text fields, including the supplied dialog text fields. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PrivacyInteractionTracking(
    timer: PrivacyInactivityTimer,
    content: @Composable () -> Unit
) {
    val interceptor = remember(timer) {
        PlatformTextInputInterceptor { request, nextHandler ->
            val trackedRequest = object : PlatformTextInputMethodRequest {
                override fun createInputConnection(
                    outAttributes: EditorInfo
                ): InputConnection {
                    val connection = request.createInputConnection(outAttributes)
                    return when {
                        Build.VERSION.SDK_INT >= 34 ->
                            Api34ActivityInputConnection(connection, timer)
                        Build.VERSION.SDK_INT >= 33 ->
                            Api33ActivityInputConnection(connection, timer)
                        else -> ActivityInputConnection(connection, timer)
                    }
                }
            }
            nextHandler.startInputMethod(trackedRequest)
        }
    }

    CompositionLocalProvider(LocalPrivacyInactivityTimer provides timer) {
        InterceptPlatformTextInput(interceptor, content)
    }
}

// Text is forwarded directly to the original connection; it is never stored or logged.
private open class ActivityInputConnection(
    connection: InputConnection,
    protected val timer: PrivacyInactivityTimer
) : InputConnectionWrapper(connection, false) {
    override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
        timer.recordInteraction()
        return super.commitText(text, newCursorPosition)
    }

    override fun setComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        timer.recordInteraction()
        return super.setComposingText(text, newCursorPosition)
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        timer.recordInteraction()
        return super.deleteSurroundingText(beforeLength, afterLength)
    }

    override fun deleteSurroundingTextInCodePoints(
        beforeLength: Int,
        afterLength: Int
    ): Boolean {
        timer.recordInteraction()
        return super.deleteSurroundingTextInCodePoints(beforeLength, afterLength)
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        timer.recordInteraction()
        return super.sendKeyEvent(event)
    }

    override fun performEditorAction(editorAction: Int): Boolean {
        timer.recordInteraction()
        return super.performEditorAction(editorAction)
    }

    override fun performContextMenuAction(id: Int): Boolean {
        timer.recordInteraction()
        return super.performContextMenuAction(id)
    }
}

@RequiresApi(33)
private open class Api33ActivityInputConnection(
    connection: InputConnection,
    timer: PrivacyInactivityTimer
) : ActivityInputConnection(connection, timer) {
    override fun commitText(
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?
    ): Boolean {
        timer.recordInteraction()
        return super.commitText(text, newCursorPosition, textAttribute)
    }

    override fun setComposingText(
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?
    ): Boolean {
        timer.recordInteraction()
        return super.setComposingText(text, newCursorPosition, textAttribute)
    }
}

@RequiresApi(34)
private class Api34ActivityInputConnection(
    connection: InputConnection,
    timer: PrivacyInactivityTimer
) : Api33ActivityInputConnection(connection, timer) {
    override fun replaceText(
        start: Int,
        end: Int,
        text: CharSequence,
        newCursorPosition: Int,
        textAttribute: TextAttribute?
    ): Boolean {
        timer.recordInteraction()
        return super.replaceText(start, end, text, newCursorPosition, textAttribute)
    }
}
