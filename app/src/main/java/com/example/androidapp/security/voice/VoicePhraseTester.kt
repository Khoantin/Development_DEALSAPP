package com.example.androidapp.security.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.ModelDownloadListener
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/** On-device phrase matching. No emergency actions or cloud fallback. */
class VoicePhraseTester(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    var isDownloading: Boolean = false
        private set

    fun cancel() {
        generation++
        handler.removeCallbacksAndMessages(null)
        isDownloading = false
        recognizer?.let { current ->
            recognizer = null
            runCatching { current.cancel() }
            runCatching { current.destroy() }
        }
    }

    private fun recognitionIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    fun start(
        expected: String,
        onDownloadStarting: () -> Unit = {},
        onStatus: (String) -> Unit = {},
        allowModelDownload: Boolean = true,
        onMatched: () -> Unit = {},
        onFinished: (String) -> Unit
    ) {
        startMatching(
            expectedPhrases = mapOf(VoicePhraseKind.ACCESS to expected),
            onDownloadStarting = onDownloadStarting,
            onStatus = onStatus,
            allowModelDownload = allowModelDownload,
            testMode = true,
            onMatched = { onMatched() },
            onFinished = onFinished
        )
    }

    /** One explicit listening session, matching any of the three saved phrases. */
    fun startForPhrases(
        phrases: VoicePhrases,
        onStatus: (String) -> Unit = {},
        onMatched: (VoicePhraseKind) -> Unit,
        onFinished: (String) -> Unit
    ) {
        val error = phrases.validationError()
        if (error != null) {
            cancel()
            onFinished(error)
            return
        }
        startMatching(
            expectedPhrases = VoicePhraseKind.values().associateWith {
                phrases.phrase(it)
            },
            onDownloadStarting = {},
            onStatus = onStatus,
            allowModelDownload = false,
            testMode = false,
            onMatched = onMatched,
            onFinished = onFinished
        )
    }

    private fun startMatching(
        expectedPhrases: Map<VoicePhraseKind, String>,
        onDownloadStarting: () -> Unit,
        onStatus: (String) -> Unit,
        allowModelDownload: Boolean,
        testMode: Boolean,
        onMatched: (VoicePhraseKind) -> Unit,
        onFinished: (String) -> Unit
    ) {
        cancel()
        val normalizedPhrases = expectedPhrases.mapValues {
            normalizeVoicePhrase(it.value)
        }
        val session = generation
        fun finish(message: String) {
            if (session != generation) return
            cancel()
            onFinished(message)
        }
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                finish("On-device recognition is unavailable. Try a supported Android 12+ device.")
                return
            }
            val current = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            recognizer = current
            current.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    if (session == generation && !isDownloading) onStatus("Listening—say your phrase now.")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {
                    if (session == generation && !isDownloading) onStatus("Checking your phrase…")
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
                override fun onResults(results: Bundle?) {
                    if (session != generation || isDownloading) return
                    val top = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    val normalizedTop = top?.let { normalizeVoicePhrase(it) }
                    val match = normalizedPhrases.entries.singleOrNull {
                        it.value.isNotBlank() && it.value == normalizedTop
                    }?.key

                    // Release the microphone before dispatching a phrase action.
                    cancel()
                    val completedGeneration = generation
                    onFinished(
                        if (match != null) {
                            if (testMode)
                                "Phrase recognized successfully. Test only—no action triggered."
                            else "Voice input ended."
                        } else "Phrase did not match. Try again in a quiet place."
                    )
                    // A caller may have cancelled or started another session.
                    if (match != null && generation == completedGeneration) {
                        onMatched(match)
                    }
                }
                override fun onError(error: Int) {
                    if (session != generation || isDownloading) return
                    if (allowModelDownload && error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE && Build.VERSION.SDK_INT >= 34) {
                        // Recognition has ended. This phase downloads a model, not microphone audio.
                        isDownloading = true
                        handler.removeCallbacksAndMessages(null)
                        onDownloadStarting() // Mark the expected system window BEFORE opening it.
                        onStatus("English (US) model needed. Accept Android's download prompt if shown.")
                        handler.postDelayed({
                            finish("Stopped waiting for the model download. Check Android's download notification, then retry.")
                        }, 120_000L)
                        try {
                            current.triggerModelDownload(recognitionIntent(), context.mainExecutor,
                                object : ModelDownloadListener {
                                    override fun onProgress(completedPercent: Int) {
                                        if (session == generation) onStatus("Downloading English (US): $completedPercent%")
                                    }
                                    override fun onSuccess() {
                                        finish("English model downloaded. Tap Test again.")
                                    }
                                    override fun onScheduled() {
                                        finish("Download scheduled. Wait for Android to finish, then tap Test again.")
                                    }
                                    override fun onError(downloadError: Int) {
                                        finish("Model download failed with code $downloadError.")
                                    }
                                })
                        } catch (_: Exception) {
                            finish("This speech service could not start the model download.")
                        }
                        return
                    }
                    finish(when (error) {
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No matching speech heard. Tap Test to try again."
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy. Try again shortly."
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Error 12: English (US) is not supported by this service."
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Error 13: English (US) model unavailable. Install it through your device's speech settings."
                        SpeechRecognizer.ERROR_AUDIO -> "Microphone unavailable. Check device or emulator microphone settings."
                        else -> "Recognition could not finish (code $error). Tap Test to retry."
                    })
                }
            })
            handler.postDelayed({ finish("Test timed out. Tap Test to try again.") }, 30_000L)
            current.startListening(recognitionIntent())
        } catch (_: Exception) {
            finish("Could not start on-device recognition. Check microphone permission and speech-service availability.")
        }
    }
}
