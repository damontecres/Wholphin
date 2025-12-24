package com.github.damontecres.wholphin.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import timber.log.Timber

/** Voice search UI state */
sealed interface VoiceSearchState {
    data object Idle : VoiceSearchState

    data object Listening : VoiceSearchState

    data class Error(
        val message: String,
    ) : VoiceSearchState
}

/** Material Design mic icon path data (avoids adding material-icons dependency) */
private val MicIcon: ImageVector by lazy {
    ImageVector
        .Builder(
            name = "Mic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 14f)
                curveToRelative(1.66f, 0f, 2.99f, -1.34f, 2.99f, -3f)
                lineTo(15f, 5f)
                curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
                reflectiveCurveTo(9f, 3.34f, 9f, 5f)
                verticalLineToRelative(6f)
                curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
                close()
                moveTo(17.3f, 11f)
                curveToRelative(0f, 3f, -2.54f, 5.1f, -5.3f, 5.1f)
                reflectiveCurveTo(6.7f, 14f, 6.7f, 11f)
                lineTo(5f, 11f)
                curveToRelative(0f, 3.41f, 2.72f, 6.23f, 6f, 6.72f)
                lineTo(11f, 21f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-3.28f)
                curveToRelative(3.28f, -0.48f, 6f, -3.3f, 6f, -6.72f)
                horizontalLineToRelative(-1.7f)
                close()
            }
        }.build()
}

// SpeechRecognizer RMS dB typically ranges from -2 to 10
private const val RMS_DB_MIN = -2.0f
private const val RMS_DB_MAX = 10.0f
private const val SOUND_LEVEL_SCALE_FACTOR = 0.15f

/** Normalizes RMS dB to 0.0-1.0 range for animation scaling */
private fun normalizeRmsDb(rmsdB: Float): Float =
    ((rmsdB - RMS_DB_MIN) / (RMS_DB_MAX - RMS_DB_MIN)).coerceIn(0f, 1f)

/**
 * Voice search button with full-screen listening overlay.
 * Handles microphone permissions and speech recognition.
 *
 * @param onSpeechResult Callback invoked with transcribed text when speech recognition completes
 * @param modifier Modifier for the button
 */
@Composable
fun VoiceSearchButton(
    onSpeechResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var voiceSearchState by remember { mutableStateOf<VoiceSearchState>(VoiceSearchState.Idle) }
    var soundLevel by remember { mutableFloatStateOf(0f) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var partialResult by remember { mutableStateOf("") }

    // Prevents race condition from rapid button taps
    var isTransitioning by remember { mutableStateOf(false) }

    fun cleanupRecognizer() {
        speechRecognizer?.let { recognizer ->
            try {
                recognizer.cancel()
                recognizer.destroy()
            } catch (e: Exception) {
                Timber.w(e, "Error cleaning up speech recognizer")
            }
        }
        speechRecognizer = null
        soundLevel = 0f
        partialResult = ""
    }

    val isAvailable =
        remember {
            SpeechRecognizer.isRecognitionAvailable(context)
        }

    // Asks for record audio permissions
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                voiceSearchState = VoiceSearchState.Listening
                partialResult = ""
                startListening(
                    context = context,
                    onStateChange = { voiceSearchState = it },
                    onSoundLevelChange = { soundLevel = it },
                    onPartialResult = { partialResult = it },
                    onResult = onSpeechResult,
                    onRecognizerCreated = { speechRecognizer = it },
                )
            } else {
                Timber.w("RECORD_AUDIO permission denied")
                voiceSearchState = VoiceSearchState.Error("Microphone permission required")
            }
        }

    // Cleanup on composable disposal
    DisposableEffect(Unit) {
        onDispose {
            cleanupRecognizer()
        }
    }

    // Cleanup when returning to Idle/Error state
    DisposableEffect(voiceSearchState) {
        onDispose {
            if (voiceSearchState !is VoiceSearchState.Listening) {
                cleanupRecognizer()
            }
        }
    }

    // Full-screen listening overlay with animated mic and partial results
    if (voiceSearchState is VoiceSearchState.Listening) {
        VoiceSearchOverlay(
            soundLevel = soundLevel,
            partialResult = partialResult,
            onDismiss = {
                cleanupRecognizer()
                voiceSearchState = VoiceSearchState.Idle
            },
        )
    }

    // Mic button: toggles listening on/off, requests permission if needed
    if (isAvailable) {
        Button(
            onClick = {
                if (isTransitioning) return@Button

                when (voiceSearchState) {
                    is VoiceSearchState.Listening -> {
                        isTransitioning = true
                        cleanupRecognizer()
                        voiceSearchState = VoiceSearchState.Idle
                        isTransitioning = false
                    }
                    else -> {
                        isTransitioning = true
                        cleanupRecognizer()

                        val hasPermission =
                            ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO,
                            ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            voiceSearchState = VoiceSearchState.Listening
                            partialResult = ""
                            startListening(
                                context = context,
                                onStateChange = { voiceSearchState = it },
                                onSoundLevelChange = { soundLevel = it },
                                onPartialResult = { partialResult = it },
                                onResult = onSpeechResult,
                                onRecognizerCreated = {
                                    speechRecognizer = it
                                    isTransitioning = false
                                },
                            )
                        } else {
                            isTransitioning = false
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            },
            modifier =
                modifier.requiredSizeIn(
                    minWidth = MinButtonSize,
                    minHeight = MinButtonSize,
                    maxWidth = MinButtonSize,
                    maxHeight = MinButtonSize,
                ),
            contentPadding = PaddingValues(0.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MicIcon,
                    contentDescription = stringResource(R.string.voice_search),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

/** Full-screen overlay with pulsing mic icon that responds to voice input level */
@Composable
private fun VoiceSearchOverlay(
    soundLevel: Float,
    partialResult: String,
    onDismiss: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    // Smooth transitions between sound level changes
    val animatedSoundLevel by animateFloatAsState(
        targetValue = soundLevel,
        animationSpec = tween(durationMillis = 100),
        label = "soundLevel",
    )

    // Continuous subtle pulse animation (1.0x to 1.05x scale)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val basePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 800),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "basePulse",
    )

    // Combine base pulse with sound-reactive scaling for the mic bubble
    val bubbleScale = basePulse + (animatedSoundLevel * SOUND_LEVEL_SCALE_FACTOR)

    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true,
                usePlatformDefaultWidth = false,
            ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 64.dp),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(160.dp)
                            .scale(bubbleScale)
                            .clip(CircleShape)
                            .background(primaryColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = MicIcon,
                        contentDescription = stringResource(R.string.voice_search),
                        modifier = Modifier.size(80.dp),
                        tint = onPrimaryColor,
                    )
                }

                Text(
                    text = if (partialResult.isNotBlank()) partialResult else stringResource(R.string.voice_search_prompt),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Initializes SpeechRecognizer and begins listening for voice input */
private fun startListening(
    context: android.content.Context,
    onStateChange: (VoiceSearchState) -> Unit,
    onSoundLevelChange: (Float) -> Unit,
    onPartialResult: (String) -> Unit,
    onResult: (String) -> Unit,
    onRecognizerCreated: (SpeechRecognizer) -> Unit,
) {
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    onRecognizerCreated(recognizer)

    val listener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Timber.d("Speech recognition ready")
            }

            override fun onBeginningOfSpeech() {
                Timber.d("Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {
                onSoundLevelChange(normalizeRmsDb(rmsdB))
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Not used
            }

            // Called when SpeechRecognizer detects silence after speech
            override fun onEndOfSpeech() {
                Timber.d("Speech ended")
            }

            override fun onError(error: Int) {
                val errorMessage =
                    when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                        else -> "Unknown error: $error"
                    }
                Timber.e("Speech recognition error: $errorMessage")
                onStateChange(VoiceSearchState.Error(errorMessage))
                onSoundLevelChange(0f)
            }

            // Called after onEndOfSpeech with final transcription, triggers the search
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val spokenText = matches?.firstOrNull()
                if (!spokenText.isNullOrBlank()) {
                    Timber.d("Speech result: $spokenText")
                    onResult(spokenText)
                }
                onStateChange(VoiceSearchState.Idle)
                onSoundLevelChange(0f)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partialText = matches?.firstOrNull()
                if (!partialText.isNullOrBlank()) {
                    Timber.d("Partial result: $partialText")
                    onPartialResult(partialText)
                }
            }

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) {
                // Not used
            }
        }

    recognizer.setRecognitionListener(listener)

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
        recognizer.startListening(intent)
    } catch (e: Exception) {
        Timber.e(e, "Failed to start speech recognition")
        onStateChange(VoiceSearchState.Error("Failed to start: ${e.message}"))
    }
}
