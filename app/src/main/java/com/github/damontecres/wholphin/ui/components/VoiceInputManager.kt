package com.github.damontecres.wholphin.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.findActivity
import timber.log.Timber

private const val RMS_DB_MIN = -2.0f
private const val RMS_DB_MAX = 10.0f
private const val MAX_RESULTS = 1

private val ERROR_TO_RESOURCE_MAP =
    mapOf(
        SpeechRecognizer.ERROR_AUDIO to R.string.voice_error_audio,
        SpeechRecognizer.ERROR_CLIENT to R.string.voice_error_client,
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS to R.string.voice_error_permissions,
        SpeechRecognizer.ERROR_NETWORK to R.string.voice_error_network,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT to R.string.voice_error_network_timeout,
        SpeechRecognizer.ERROR_NO_MATCH to R.string.voice_error_no_match,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY to R.string.voice_error_busy,
        SpeechRecognizer.ERROR_SERVER to R.string.voice_error_server,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT to R.string.voice_error_speech_timeout,
    )

private fun normalizeRmsDb(rmsdB: Float) = ((rmsdB - RMS_DB_MIN) / (RMS_DB_MAX - RMS_DB_MIN)).coerceIn(0f, 1f)

sealed interface VoiceInputState {
    data object Idle : VoiceInputState

    data object Listening : VoiceInputState

    data object Processing : VoiceInputState

    data class Result(
        val text: String,
    ) : VoiceInputState

    data class Error(
        val messageResId: Int,
    ) : VoiceInputState
}

@Stable
class VoiceInputManager(
    private val activity: Activity,
) {
    var state: VoiceInputState by mutableStateOf(VoiceInputState.Idle)
        private set
    var soundLevel by mutableFloatStateOf(0f)
        private set
    var partialResult by mutableStateOf("")
        private set

    val isAvailable = SpeechRecognizer.isRecognitionAvailable(activity)
    val hasPermission: Boolean
        get() =
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

    private var recognizer: SpeechRecognizer? = null

    @Volatile
    private var isTransitioning = false

    private val recognitionIntent by lazy {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
        }
    }

    fun startListening() {
        if (isTransitioning || state is VoiceInputState.Listening) return
        isTransitioning = true

        destroyRecognizer()
        partialResult = ""
        soundLevel = 0f
        state = VoiceInputState.Listening

        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        recognizer = newRecognizer
        newRecognizer.setRecognitionListener(createRecognitionListener(newRecognizer))

        try {
            newRecognizer.startListening(recognitionIntent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to start speech recognition")
            destroyRecognizer()
            state = VoiceInputState.Error(R.string.voice_error_start_failed)
        } finally {
            isTransitioning = false
        }
    }

    fun stopListening() {
        if (isTransitioning) return
        isTransitioning = true
        cleanup()
        isTransitioning = false
    }

    fun acknowledge() {
        state = VoiceInputState.Idle
    }

    fun onPermissionGranted() = startListening()

    fun onPermissionDenied() {
        Timber.w("RECORD_AUDIO permission denied")
        state = VoiceInputState.Error(R.string.voice_error_permissions)
    }

    private fun destroyRecognizer() {
        // Null out FIRST to invalidate callbacks before cancel() can trigger them
        val rec = recognizer
        recognizer = null
        rec?.let {
            try {
                it.cancel()
                it.destroy()
            } catch (e: Exception) {
                Timber.w(e, "Error destroying speech recognizer")
            }
        }
    }

    internal fun cleanup() {
        destroyRecognizer()
        soundLevel = 0f
        partialResult = ""
        state = VoiceInputState.Idle
    }

    private fun createRecognitionListener(activeRecognizer: SpeechRecognizer) =
        object : RecognitionListener {
            // Guard against callbacks from zombie recognizers
            private fun isValid() = recognizer === activeRecognizer

            override fun onReadyForSpeech(params: Bundle?) {
                if (!isValid()) return
            }

            override fun onBeginningOfSpeech() {
                if (!isValid()) return
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (!isValid()) return
                soundLevel = normalizeRmsDb(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() {
                if (!isValid()) return
                state = VoiceInputState.Processing
            }

            override fun onError(error: Int) {
                if (!isValid()) return
                state = VoiceInputState.Error(ERROR_TO_RESOURCE_MAP[error] ?: R.string.voice_error_unknown)
                soundLevel = 0f
            }

            override fun onResults(results: Bundle?) {
                if (!isValid()) return
                val spokenText =
                    results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                state =
                    if (!spokenText.isNullOrBlank()) {
                        VoiceInputState.Result(spokenText)
                    } else {
                        VoiceInputState.Error(R.string.voice_error_no_match)
                    }
                soundLevel = 0f
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isValid()) return
                partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?.let { partialResult = it }
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) = Unit
        }
}

@Composable
fun rememberVoiceInputManager(): VoiceInputManager? {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    if (activity == null) {
        Timber.w("Could not find Activity context for VoiceInputManager")
        return null
    }

    val manager = remember(activity) { VoiceInputManager(activity) }

    DisposableEffect(manager) {
        onDispose { manager.cleanup() }
    }

    return manager
}
