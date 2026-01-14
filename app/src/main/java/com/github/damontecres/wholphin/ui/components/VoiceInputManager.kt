package com.github.damontecres.wholphin.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.github.damontecres.wholphin.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.io.Closeable
import javax.inject.Inject
import javax.inject.Singleton

private const val RMS_DB_MIN = -2.0f
private const val RMS_DB_MAX = 10.0f
private const val MAX_RESULTS = 1
private const val LISTENING_TIMEOUT_MS = 5000L

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

private val RETRYABLE_ERRORS =
    setOf(
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_NO_MATCH,
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
        val isRetryable: Boolean,
    ) : VoiceInputState
}

@Singleton
class VoiceInputManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : Closeable {
        private val handler = Handler(Looper.getMainLooper())
        private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        private val mutex = Mutex()

        private val _state = MutableStateFlow<VoiceInputState>(VoiceInputState.Idle)
        val state: StateFlow<VoiceInputState> = _state.asStateFlow()

        private val _soundLevel = MutableStateFlow(0f)
        val soundLevel: StateFlow<Float> = _soundLevel.asStateFlow()

        private val _partialResult = MutableStateFlow("")
        val partialResult: StateFlow<String> = _partialResult.asStateFlow()

        val isAvailable = SpeechRecognizer.isRecognitionAvailable(context)
        val hasPermission: Boolean
            get() =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED

        private var recognizer: SpeechRecognizer? = null
        private var busyRetryCount = 0

        private val timeoutHandler = Handler(Looper.getMainLooper())
        private val timeoutRunnable =
            Runnable {
                scope.launch {
                    mutex.withLock {
                        if (_state.value is VoiceInputState.Listening) {
                            val partial = _partialResult.value
                            destroyRecognizer()
                            handler.post {
                                _soundLevel.value = 0f
                                if (partial.isNotBlank()) {
                                    _state.value = VoiceInputState.Result(partial)
                                } else {
                                    _partialResult.value = ""
                                    _state.value =
                                        VoiceInputState.Error(
                                            messageResId = R.string.voice_error_timeout,
                                            isRetryable = true,
                                        )
                                }
                            }
                        }
                    }
                }
            }

        private val recognitionIntent by lazy {
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_RESULTS)
            }
        }

        fun startListening() {
            scope.launch {
                mutex.withLock {
                    if (_state.value is VoiceInputState.Listening) return@withLock

                    busyRetryCount = 0
                    destroyRecognizer()
                    cancelTimeout()
                    handler.post {
                        _partialResult.value = ""
                        _soundLevel.value = 0f
                        _state.value = VoiceInputState.Listening
                    }

                    val newRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                    recognizer = newRecognizer
                    newRecognizer.setRecognitionListener(createRecognitionListener(newRecognizer))

                    try {
                        newRecognizer.startListening(recognitionIntent)
                        timeoutHandler.postDelayed(timeoutRunnable, LISTENING_TIMEOUT_MS)
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to start speech recognition")
                        destroyRecognizer()
                        cancelTimeout()
                        handler.post {
                            _state.value =
                                VoiceInputState.Error(
                                    messageResId = R.string.voice_error_start_failed,
                                    isRetryable = true,
                                )
                        }
                    }
                }
            }
        }

        fun stopListening() {
            scope.launch {
                mutex.withLock {
                    cancelTimeout()
                    close()
                }
            }
        }

        private fun cancelTimeout() {
            timeoutHandler.removeCallbacks(timeoutRunnable)
        }

        fun acknowledge() {
            handler.post { _state.value = VoiceInputState.Idle }
        }

        fun onPermissionGranted() = startListening()

        fun onPermissionDenied() {
            Timber.w("RECORD_AUDIO permission denied")
            handler.post {
                _state.value =
                    VoiceInputState.Error(
                        messageResId = R.string.voice_error_permissions,
                        isRetryable = false,
                    )
            }
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

        override fun close() {
            destroyRecognizer()
            handler.post {
                _soundLevel.value = 0f
                _partialResult.value = ""
                _state.value = VoiceInputState.Idle
            }
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
                    handler.post { _soundLevel.value = normalizeRmsDb(rmsdB) }
                }

                override fun onBufferReceived(buffer: ByteArray?) = Unit

                override fun onEndOfSpeech() {
                    if (!isValid()) return
                    cancelTimeout()
                    handler.post { _state.value = VoiceInputState.Processing }
                }

                override fun onError(error: Int) {
                    if (!isValid()) return
                    Timber.e("Voice recognition error code: $error")
                    cancelTimeout()
                    if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY && busyRetryCount < 1) {
                        busyRetryCount++
                        handler.postDelayed({ startListening() }, 300)
                        return
                    }
                    handler.post {
                        _state.value =
                            VoiceInputState.Error(
                                messageResId = ERROR_TO_RESOURCE_MAP[error] ?: R.string.voice_error_unknown,
                                isRetryable = error in RETRYABLE_ERRORS,
                            )
                        _soundLevel.value = 0f
                    }
                }

                override fun onResults(results: Bundle?) {
                    if (!isValid()) return
                    cancelTimeout()
                    val spokenText =
                        results
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                    handler.post {
                        _state.value =
                            if (!spokenText.isNullOrBlank()) {
                                VoiceInputState.Result(spokenText)
                            } else {
                                VoiceInputState.Error(
                                    messageResId = R.string.voice_error_no_match,
                                    isRetryable = true,
                                )
                            }
                        _soundLevel.value = 0f
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    if (!isValid()) return
                    partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?.let { handler.post { _partialResult.value = it } }
                }

                override fun onEvent(
                    eventType: Int,
                    params: Bundle?,
                ) = Unit
            }
    }
