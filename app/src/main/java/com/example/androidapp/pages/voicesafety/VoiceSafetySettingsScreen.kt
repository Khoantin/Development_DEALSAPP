package com.example.androidapp.pages.voicesafety

import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.androidapp.security.LocalPrivacyExternalInteraction
import com.example.androidapp.security.trackPrivacyInteraction
import com.example.androidapp.security.voice.VoicePhraseKind
import com.example.androidapp.security.voice.VoicePhraseStore
import com.example.androidapp.security.voice.VoicePhraseTester
import com.example.androidapp.security.voice.VoicePhrases

@Composable
fun VoiceSafetySettingsScreen(
    sessionStatus: String = "No voice-triggered session in this app session.",
    sessionEventId: String? = null,
    sessionBusy: Boolean = false,
    duressEnabled: Boolean = false,
    onDuressEnabledChange: (Boolean) -> Unit = {},
    onCancelSession: () -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val externalInteraction = LocalPrivacyExternalInteraction.current
    val store = remember(context) { VoicePhraseStore(context) }
    val loaded = remember(store) { runCatching { store.load() } }
    var phrases by remember(store) { mutableStateOf(loaded.getOrDefault(VoicePhrases())) }
    var status by remember {
        mutableStateOf(if (loaded.isFailure) "Saved phrases could not be read. Clear them and configure them again."
        else "Test mode only. No alerts or location sharing will be triggered.")
    }
    var testing by remember { mutableStateOf(false) }
    var requestingPermission by remember { mutableStateOf(false) }
    val tester = remember(context) { VoicePhraseTester(context) }
    val rootView = LocalView.current.rootView
    val latestExternalInteraction by rememberUpdatedState(externalInteraction)
    var waitingForDownloadPrompt by remember { mutableStateOf(false) }
    val downloadGuard = remember(rootView, lifecycleOwner, tester) {
        DownloadPromptGuard(
            rootView,
            lifecycleOwner.lifecycle,
            onActiveChanged = { active ->
                waitingForDownloadPrompt = active
                latestExternalInteraction(active)
            },
            onExpired = {
                tester.cancel()
                testing = false
                status = "Download prompt wait ended. Return to the app and retry when ready."
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        requestingPermission = false
        externalInteraction(false)
        status = if (granted) "Microphone allowed. Tap your Test button again."
        else "Microphone permission denied. You can enable it in Android app settings."
    }

    DisposableEffect(lifecycleOwner, tester, downloadGuard) {
        downloadGuard.attach()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (downloadGuard.active) {
                        // The recognition session has already ended before the model prompt.
                        downloadGuard.noteWindowLeft()
                    } else {
                        tester.cancel()
                        if (testing) status = "Test stopped because the screen is no longer active."
                        testing = false
                    }
                }
                Lifecycle.Event.ON_RESUME -> downloadGuard.checkReturn()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            tester.cancel()
            downloadGuard.dispose()
        }
    }

    fun test(kind: VoicePhraseKind) {
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val error = phrases.validationError()
        if (error != null) { status = error; return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            requestingPermission = true
            externalInteraction(true)
            try {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } catch (_: Exception) {
                requestingPermission = false
                externalInteraction(false)
                status = "Could not request microphone permission."
            }
            return
        }
        testing = true
        status = "Starting microphone for the ${kind.label.lowercase()} phrase…"
        tester.start(
            expected = phrases.phrase(kind),
            onDownloadStarting = { downloadGuard.begin() },
            onStatus = { status = it },
            onFinished = { result ->
                testing = false
                status = result
                downloadGuard.requestFinished()
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().trackPrivacyInteraction()
                .safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = {
                tester.cancel()
                downloadGuard.release()
                onBack()
            }) { Text("Back") }
            Text("Voice Safety", style = MaterialTheme.typography.headlineSmall)
            Text("Choose three distinct English phrases. Testing uses on-device recognition and does not activate Voice Safety Mode.")
            Text("Android may show its microphone indicator or play recognition sounds. A phrase is not proof of who is speaking; access will still require your passcode.")

            Text(status, style = MaterialTheme.typography.bodyLarge)

            VoicePhraseKind.values().forEach { kind ->
                OutlinedTextField(
                    value = phrases.phrase(kind),
                    onValueChange = { value ->
                        if (value.length <= 120) {
                            phrases = when (kind) {
                                VoicePhraseKind.ACCESS -> phrases.copy(access = value)
                                VoicePhraseKind.DURESS -> phrases.copy(duress = value)
                                VoicePhraseKind.CANCEL -> phrases.copy(cancel = value)
                            }
                            status = "Unsaved changes. Save phrases when ready."
                        }
                    },
                    enabled = !testing && !requestingPermission && !waitingForDownloadPrompt,
                    label = { Text("${kind.label} phrase") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                Button(onClick = { test(kind) }, enabled = !testing && !requestingPermission && !waitingForDownloadPrompt) {
                    Text("Test ${kind.label} Phrase")
                }
            }

            if (testing) {
                Button(onClick = {
                    tester.cancel()
                    downloadGuard.release()
                    testing = false
                    status = "Test stopped."
                }) { Text("Stop Test") }
            }
            Button(enabled = !testing && !requestingPermission && !waitingForDownloadPrompt, onClick = {
                val error = phrases.validationError()
                status = if (error != null) error else {
                    runCatching { store.save(phrases) }.fold(
                        onSuccess = { "Phrases saved. Tap Voice in WeatherNow to use them. Duress requires the switch below." },
                        onFailure = { "Could not save phrases. Please try again." }
                    )
                }
            }) { Text("Save Phrases") }
            Text("Duress sharing", style = MaterialTheme.typography.titleMedium)
            Text("Before enabling: save linked recipients in Location Sharing and grant location permission there. Then saying your duress phrase through WeatherNow's Voice button creates an emergency event and shares your location with those recipients.")
            Text(
                "Location sharing can continue while the app is in the background " +
                        "using a foreground service. If Android stops the service, reopen " +
                        "the app to attempt to resume an active saved session. " +
                        "Use your cancel phrase or Cancel Session to stop sharing. " +
                        "This version does not send push notifications or SMS."
            )
            Text("Enable duress phrase for this app session")
            Switch(
                checked = duressEnabled,
                onCheckedChange = onDuressEnabledChange,
                enabled = !testing && !requestingPermission && !waitingForDownloadPrompt && !sessionBusy
            )
            Text("Turning this switch off prevents new voice-triggered shares. Use Cancel Session to stop an existing one.")
            Text(sessionStatus)
            sessionEventId?.let { Text("Event ID: $it") }
            if (sessionEventId != null || sessionBusy) {
                Button(
                    onClick = onCancelSession,
                    enabled = !testing && !requestingPermission && !waitingForDownloadPrompt
                ) { Text("Cancel Session") }
            }
            TextButton(enabled = !testing && !requestingPermission && !waitingForDownloadPrompt && sessionEventId == null && !sessionBusy, onClick = {
                runCatching { store.clear() }.fold(
                    onSuccess = {
                        phrases = VoicePhrases()
                        status = "Saved phrases cleared."
                    },
                    onFailure = { status = "Could not clear saved phrases." }
                )
            }) { Text("Clear Saved Phrases") }
        }
    }
}

/** A bounded exception for the model-download system window, never ordinary listening. */
private class DownloadPromptGuard(
    private val view: View,
    private val lifecycle: Lifecycle,
    private val onActiveChanged: (Boolean) -> Unit,
    private val onExpired: () -> Unit
) {
    private val handler = Handler(Looper.getMainLooper())
    private var observer: ViewTreeObserver? = null
    private var windowLeft = false
    var active = false
        private set

    private val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
        if (active) {
            if (!focused) noteWindowLeft() else checkReturn()
        }
    }

    fun attach() {
        observer = view.viewTreeObserver.also { it.addOnWindowFocusChangeListener(listener) }
    }

    fun begin() {
        handler.removeCallbacksAndMessages(null)
        windowLeft = false
        active = true
        onActiveChanged(true)
        // A service may never return a result; do not leave privacy exceptions enabled forever.
        handler.postDelayed({
            if (active) {
                release()
                onExpired()
            }
        }, 120_000L)
    }

    fun noteWindowLeft() { if (active) windowLeft = true }

    fun checkReturn() {
        handler.post {
            if (active && windowLeft && view.hasWindowFocus() &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                release()
            }
        }
    }

    fun requestFinished() {
        if (!active) return
        // Downloads can finish without showing a window. If one is open, retain the
        // exception until focus returns, rather than causing an exit behind the prompt.
        handler.postDelayed({
            if (active && view.hasWindowFocus() &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                release()
            }
        }, 500L)
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        if (active) {
            active = false
            windowLeft = false
            onActiveChanged(false)
        }
    }

    fun dispose() {
        observer?.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(listener)
        observer = null
        release()
    }
}
