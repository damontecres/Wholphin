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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.R
import timber.log.Timber

/**
 * State for the voice search functionality
 */
sealed interface VoiceSearchState {
    data object Idle : VoiceSearchState
    data object Listening : VoiceSearchState
    data class Error(val message: String) : VoiceSearchState
}

/**
 * Microphone icon vector - fill color will be overridden by Icon's tint (LocalContentColor)
 */
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

/**
 * Normalizes RMS dB value from SpeechRecognizer to a 0.0-1.0 range.
 * SpeechRecognizer typically returns values in the -2.0 to 10.0 range.
 */
private fun normalizeRmsDb(rmsdB: Float): Float {
    val minRms = -2.0f
    val maxRms = 10.0f
    return ((rmsdB - minRms) / (maxRms - minRms)).coerceIn(0f, 1f)
}

@Composable
fun VoiceSearchButton(
    onSpeechResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var voiceSearchState by remember { mutableStateOf<VoiceSearchState>(VoiceSearchState.Idle) }
    var soundLevel by remember { mutableFloatStateOf(0f) }
    var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Smooth animation for sound level
    val animatedSoundLevel by animateFloatAsState(
        targetValue = soundLevel,
        animationSpec = tween(durationMillis = 100),
        label = "soundLevel",
    )

    // Check if speech recognition is available
    val isAvailable = remember {
        SpeechRecognizer.isRecognitionAvailable(context)
    }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            startListening(
                context = context,
                onStateChange = { voiceSearchState = it },
                onSoundLevelChange = { soundLevel = it },
                onResult = onSpeechResult,
                onRecognizerCreated = { speechRecognizer = it },
            )
        } else {
            Timber.w("RECORD_AUDIO permission denied")
            voiceSearchState = VoiceSearchState.Error("Microphone permission required")
        }
    }

    // Clean up SpeechRecognizer when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    // Also clean up when state changes to Idle or Error
    DisposableEffect(voiceSearchState) {
        onDispose {
            if (voiceSearchState !is VoiceSearchState.Listening) {
                speechRecognizer?.destroy()
                speechRecognizer = null
                soundLevel = 0f
            }
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    if (isAvailable) {
        Button(
            onClick = {
                when (voiceSearchState) {
                    is VoiceSearchState.Listening -> {
                        // Stop listening
                        speechRecognizer?.stopListening()
                        speechRecognizer?.destroy()
                        speechRecognizer = null
                        voiceSearchState = VoiceSearchState.Idle
                        soundLevel = 0f
                    }
                    else -> {
                        // Check permission and start listening
                        val hasPermission = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            startListening(
                                context = context,
                                onStateChange = { voiceSearchState = it },
                                onSoundLevelChange = { soundLevel = it },
                                onResult = onSpeechResult,
                                onRecognizerCreated = { speechRecognizer = it },
                            )
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            },
            modifier = modifier.requiredSizeIn(
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
                // Layer 1: Sound wave animation (behind the icon)
                if (voiceSearchState is VoiceSearchState.Listening) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2

                        drawSoundWaves(
                            color = primaryColor,
                            centerX = centerX,
                            centerY = centerY,
                            soundLevel = animatedSoundLevel,
                        )
                    }
                }

                // Layer 2: Standard Icon composable - automatically adapts to LocalContentColor
                // This ensures the icon is visible regardless of button focus/press state
                Icon(
                    imageVector = MicIcon,
                    contentDescription = stringResource(R.string.voice_search),
                    modifier = Modifier.size(28.dp),
                    // Don't set tint - let it use LocalContentColor from Button
                )
            }
        }
    }
}

private fun startListening(
    context: android.content.Context,
    onStateChange: (VoiceSearchState) -> Unit,
    onSoundLevelChange: (Float) -> Unit,
    onResult: (String) -> Unit,
    onRecognizerCreated: (SpeechRecognizer) -> Unit,
) {
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    onRecognizerCreated(recognizer)

    val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Timber.d("Speech recognition ready")
            onStateChange(VoiceSearchState.Listening)
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

        override fun onEndOfSpeech() {
            Timber.d("Speech ended")
        }

        override fun onError(error: Int) {
            val errorMessage = when (error) {
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
            // Could show partial results if desired
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
            // Not used
        }
    }

    recognizer.setRecognitionListener(listener)

    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }

    try {
        recognizer.startListening(intent)
        onStateChange(VoiceSearchState.Listening)
    } catch (e: Exception) {
        Timber.e(e, "Failed to start speech recognition")
        onStateChange(VoiceSearchState.Error("Failed to start: ${e.message}"))
    }
}

/**
 * Draws animated sound wave arcs emanating from the right side of the icon
 */
private fun DrawScope.drawSoundWaves(
    color: Color,
    centerX: Float,
    centerY: Float,
    soundLevel: Float,
) {
    val baseStroke = 2.5f
    val arcSpacing = 6f

    // Arc 1: Always visible when listening
    val arc1Alpha = 0.3f + (soundLevel * 0.4f)
    val arc1Stroke = baseStroke + (soundLevel * 1f)
    drawArc(
        color = color.copy(alpha = arc1Alpha),
        startAngle = -45f,
        sweepAngle = 90f,
        useCenter = false,
        topLeft = Offset(centerX - 8f, centerY - 8f),
        size = Size(16f, 16f),
        style = Stroke(width = arc1Stroke, cap = StrokeCap.Round),
    )

    // Arc 2: Visible when soundLevel > 0.3
    if (soundLevel > 0.3f) {
        val arc2Progress = ((soundLevel - 0.3f) / 0.3f).coerceIn(0f, 1f)
        val arc2Alpha = 0.2f + (arc2Progress * 0.4f)
        val arc2Stroke = baseStroke + (arc2Progress * 0.8f)
        drawArc(
            color = color.copy(alpha = arc2Alpha),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(centerX - 8f - arcSpacing, centerY - 8f - arcSpacing),
            size = Size(16f + arcSpacing * 2, 16f + arcSpacing * 2),
            style = Stroke(width = arc2Stroke, cap = StrokeCap.Round),
        )
    }

    // Arc 3: Visible when soundLevel > 0.6
    if (soundLevel > 0.6f) {
        val arc3Progress = ((soundLevel - 0.6f) / 0.4f).coerceIn(0f, 1f)
        val arc3Alpha = 0.15f + (arc3Progress * 0.35f)
        val arc3Stroke = baseStroke + (arc3Progress * 0.6f)
        drawArc(
            color = color.copy(alpha = arc3Alpha),
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(centerX - 8f - arcSpacing * 2, centerY - 8f - arcSpacing * 2),
            size = Size(16f + arcSpacing * 4, 16f + arcSpacing * 4),
            style = Stroke(width = arc3Stroke, cap = StrokeCap.Round),
        )
    }
}
