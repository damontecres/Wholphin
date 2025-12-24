package com.github.damontecres.wholphin.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import kotlinx.coroutines.delay

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

private const val SOUND_LEVEL_SCALE_FACTOR = 0.15f

/**
 * Voice search button with full-screen listening overlay.
 * Handles microphone permissions and speech recognition.
 *
 * @param onSpeechResult Callback invoked with transcribed text when speech recognition completes
 * @param voiceInputManager The voice input manager instance (from [rememberVoiceInputManager])
 * @param modifier Modifier for the button
 */
@Composable
fun VoiceSearchButton(
    onSpeechResult: (String) -> Unit,
    voiceInputManager: VoiceInputManager?,
    modifier: Modifier = Modifier,
) {
    if (voiceInputManager == null || !voiceInputManager.isAvailable) return

    val state = voiceInputManager.state

    // Handle result state - invoke callback and acknowledge
    LaunchedEffect(state) {
        if (state is VoiceInputState.Result) {
            onSpeechResult(state.text)
            voiceInputManager.acknowledge()
        }
    }

    // Auto-dismiss error state after a short delay
    LaunchedEffect(state) {
        if (state is VoiceInputState.Error) {
            delay(3000)
            voiceInputManager.acknowledge()
        }
    }

    // Permission launcher - only triggered when needed
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            if (isGranted) {
                voiceInputManager.onPermissionGranted()
            } else {
                voiceInputManager.onPermissionDenied()
            }
        }

    // Show overlay when listening, processing, or showing error
    if (state is VoiceInputState.Listening || state is VoiceInputState.Processing || state is VoiceInputState.Error) {
        val errorResId = (state as? VoiceInputState.Error)?.messageResId
        val errorMessage = errorResId?.let { stringResource(it) }

        VoiceSearchOverlay(
            soundLevel = voiceInputManager.soundLevel,
            partialResult = voiceInputManager.partialResult,
            isProcessing = state is VoiceInputState.Processing,
            errorMessage = errorMessage,
            onDismiss = { voiceInputManager.stopListening() },
        )
    }

    // Mic button
    Button(
        onClick = {
            when (state) {
                is VoiceInputState.Listening -> {
                    voiceInputManager.stopListening()
                }

                else -> {
                    if (voiceInputManager.hasPermission) {
                        voiceInputManager.startListening()
                    } else {
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

/** Full-screen overlay with pulsing mic icon that responds to voice input level */
@Composable
private fun VoiceSearchOverlay(
    soundLevel: Float,
    partialResult: String,
    isProcessing: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val errorColor = MaterialTheme.colorScheme.error

    // Cache Stroke object to avoid allocation on every frame
    val density = LocalDensity.current
    val rippleStroke =
        remember(density) {
            Stroke(width = with(density) { 2.dp.toPx() })
        }

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

    // Ripple rings animation (0.0 to 1.0, restarts)
    // We use a constant duration but switch the target value to 0f when processing
    // to "pause" the effect without causing the fast-looping issue.
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (isProcessing || errorMessage != null) 0f else 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1500),
                repeatMode = RepeatMode.Restart,
            ),
        label = "ripple",
    )

    // Animated dots for "Listening..." text (cycles 0, 1, 2, 3)
    val dotAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = 1200),
                repeatMode = RepeatMode.Restart,
            ),
        label = "dots",
    )

    // Combine base pulse with sound-reactive scaling for the mic bubble
    val bubbleScale = basePulse + (animatedSoundLevel * SOUND_LEVEL_SCALE_FACTOR)
    val bubbleSizeDp = 160.dp

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
                    .background(Color.Black.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 64.dp),
            ) {
                // Mic bubble with ripple rings
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                ) {
                    // Ripple rings expanding outward from the bubble
                    // Only show ripple rings when actively listening (not processing or error)
                    if (!isProcessing && errorMessage == null) {
                        Canvas(modifier = Modifier.size(bubbleSizeDp * 1.8f)) {
                            val canvasCenter = center
                            val baseRadius = bubbleSizeDp.toPx() / 2
                            val maxExpansion = bubbleSizeDp.toPx() * 0.35f

                            for (i in 0..2) {
                                // Stagger each ring's progress
                                val ringProgress = (rippleProgress + (i * 0.33f)) % 1f
                                val ringRadius = baseRadius + (ringProgress * maxExpansion)
                                val ringAlpha = (1f - ringProgress) * 0.4f

                                drawCircle(
                                    color = primaryColor.copy(alpha = ringAlpha),
                                    radius = ringRadius,
                                    center = canvasCenter,
                                    style = rippleStroke,
                                )
                            }
                        }
                    }

                    // Main mic bubble
                    Box(
                        modifier =
                            Modifier
                                .size(bubbleSizeDp)
                                .graphicsLayer {
                                    scaleX = bubbleScale
                                    scaleY = bubbleScale
                                }.clip(CircleShape)
                                .background(if (errorMessage != null) errorColor else primaryColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = MicIcon,
                            contentDescription = stringResource(R.string.voice_search),
                            modifier = Modifier.size(80.dp),
                            tint = onPrimaryColor,
                        )
                    }
                }

                // Determine status text and accessibility description
                val processingText = stringResource(R.string.processing)
                val listeningText = stringResource(R.string.voice_search_prompt)

                val statusText =
                    when {
                        errorMessage != null -> errorMessage
                        partialResult.isNotBlank() -> partialResult
                        isProcessing -> processingText + ".".repeat(dotAnimation.toInt())
                        else -> listeningText + ".".repeat(dotAnimation.toInt())
                    }
                // Accessibility description without animated dots
                val accessibilityDescription =
                    when {
                        errorMessage != null -> errorMessage
                        partialResult.isNotBlank() -> partialResult
                        isProcessing -> processingText
                        else -> listeningText
                    }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (errorMessage != null) errorColor else Color.White,
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics { contentDescription = accessibilityDescription },
                )
            }

            // Dismissal hint at bottom
            Text(
                text = stringResource(R.string.press_back_to_cancel),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
            )
        }
    }
}
