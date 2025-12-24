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

/**
 * Voice input state representing the current status of speech recognition.
 * Observers can react to state transitions for focus management, UI updates, etc.
 */
sealed interface VoiceInputState {
    /** No voice recognition active */
    data object Idle : VoiceInputState

    /** Actively listening for speech */
    data object Listening : VoiceInputState

    /** Speech ended, waiting for recognition results */
    data object Processing : VoiceInputState

    /** Speech recognition completed with a result */
    data class Result(
        val text: String,
    ) : VoiceInputState

    /** An error occurred during speech recognition */
    data class Error(
        val messageResId: Int,
    ) : VoiceInputState
}

// SpeechRecognizer RMS dB typically ranges from -2 to 10
private const val RMS_DB_MIN = -2.0f
private const val RMS_DB_MAX = 10.0f

/** Normalizes RMS dB to 0.0-1.0 range for animation scaling */
private fun normalizeRmsDb(rmsdB: Float): Float = ((rmsdB - RMS_DB_MIN) / (RMS_DB_MAX - RMS_DB_MIN)).coerceIn(0f, 1f)

/**
 * Manages speech recognition lifecycle with proper cleanup and state management.
 * Use [rememberVoiceInputManager] to create an instance in Compose.
 *
 * @param activity The Activity context required for SpeechRecognizer
 */
@Stable
class VoiceInputManager(
    private val activity: Activity,
) {
    /** Current state of voice input - observe this for UI updates and focus management */
    var state: VoiceInputState by mutableStateOf(VoiceInputState.Idle)
        private set

    /** Current sound level (0.0 to 1.0) for animation */
    var soundLevel: Float by mutableFloatStateOf(0f)
        private set

    /** Partial transcription result shown during listening */
    var partialResult: String by mutableStateOf("")
        private set

    /** Whether speech recognition is available on this device */
    val isAvailable: Boolean = SpeechRecognizer.isRecognitionAvailable(activity)

    /** Whether microphone permission is granted */
    val hasPermission: Boolean
        get() =
            ContextCompat.checkSelfPermission(
                activity,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

    private var recognizer: SpeechRecognizer? = null
    private var isTransitioning = false

    /**
     * Starts listening for voice input.
     * Call this after ensuring permission is granted.
     */
    fun startListening() {
        if (isTransitioning || state is VoiceInputState.Listening) return
        isTransitioning = true

        cleanup()
        state = VoiceInputState.Listening
        partialResult = ""

        val newRecognizer = SpeechRecognizer.createSpeechRecognizer(activity)
        recognizer = newRecognizer

        newRecognizer.setRecognitionListener(createRecognitionListener())

        val intent =
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }

        try {
            newRecognizer.startListening(intent)
            isTransitioning = false
        } catch (e: Exception) {
            Timber.e(e, "Failed to start speech recognition")
            state = VoiceInputState.Error(R.string.voice_error_start_failed)
            isTransitioning = false
        }
    }

    /** Stops listening and returns to idle state */
    fun stopListening() {
        if (isTransitioning) return
        isTransitioning = true
        cleanup()
        isTransitioning = false
    }

    /** Acknowledges a result or error, returning to idle state */
    fun acknowledge() {
        state = VoiceInputState.Idle
    }

    /** Called when permission is granted - starts listening */
    fun onPermissionGranted() {
        startListening()
    }

    /** Called when permission is denied */
    fun onPermissionDenied() {
        Timber.w("RECORD_AUDIO permission denied")
        state = VoiceInputState.Error(R.string.voice_error_permissions)
    }

    /** Cleans up the recognizer. Called automatically on disposal. */
    internal fun cleanup() {
        recognizer?.let { rec ->
            try {
                rec.cancel()
                rec.destroy()
            } catch (e: Exception) {
                Timber.w(e, "Error cleaning up speech recognizer")
            }
        }
        recognizer = null
        soundLevel = 0f
        partialResult = ""
        // Reset state to Idle to prevent persisting Error/Listening states on reuse
        state = VoiceInputState.Idle
    }

    private fun createRecognitionListener() =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Timber.d("Speech recognition ready")
            }

            override fun onBeginningOfSpeech() {
                Timber.d("Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {
                soundLevel = normalizeRmsDb(rmsdB)
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Not used
            }

            override fun onEndOfSpeech() {
                Timber.d("Speech ended")
                state = VoiceInputState.Processing
            }

            override fun onError(error: Int) {
                val errorResId =
                    when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> R.string.voice_error_audio
                        SpeechRecognizer.ERROR_CLIENT -> R.string.voice_error_client
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> R.string.voice_error_permissions
                        SpeechRecognizer.ERROR_NETWORK -> R.string.voice_error_network
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> R.string.voice_error_network_timeout
                        SpeechRecognizer.ERROR_NO_MATCH -> R.string.voice_error_no_match
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> R.string.voice_error_busy
                        SpeechRecognizer.ERROR_SERVER -> R.string.voice_error_server
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> R.string.voice_error_speech_timeout
                        else -> R.string.voice_error_unknown
                    }
                Timber.e("Speech recognition error: $error")
                state = VoiceInputState.Error(errorResId)
                soundLevel = 0f
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    Timber.d("Speech result: $spokenText")
                    state = VoiceInputState.Result(spokenText)
                } else {
                    state = VoiceInputState.Idle
                }
                soundLevel = 0f
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    Timber.d("Partial result: $text")
                    partialResult = text
                }
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) {
                // Not used
            }
        }
}

/**
 * Creates and remembers a [VoiceInputManager] with proper lifecycle management.
 * The manager is automatically cleaned up when the composable leaves the composition.
 *
 * @return The VoiceInputManager instance, or null if Activity context is unavailable
 */
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
        onDispose {
            manager.cleanup()
        }
    }

    return manager
}
