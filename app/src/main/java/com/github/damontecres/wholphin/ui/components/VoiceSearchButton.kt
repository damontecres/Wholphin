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

/** Constants for VoiceSearchButton and VoiceSearchOverlay */
private object VoiceSearchConstants {
    /** Delay before auto-dismissing error messages */
    const val ERROR_AUTO_DISMISS_DELAY_MS = 3000L

    /** How much sound level affects the bubble scale (0-1 sound → 0-0.15 scale) */
    const val SOUND_LEVEL_SCALE_FACTOR = 0.15f

    /** Size of the main mic bubble */
    val BUBBLE_SIZE = 160.dp

    /** Size of the mic icon inside the bubble */
    val MIC_ICON_SIZE = 80.dp

    /** Spacing between mic bubble and status text */
    val CONTENT_SPACING = 48.dp

    /** Horizontal padding for the overlay content */
    val HORIZONTAL_PADDING = 64.dp

    /** Bottom padding for the dismiss hint */
    val DISMISS_HINT_BOTTOM_PADDING = 32.dp

    /** Alpha for the dismiss hint text */
    const val HINT_TEXT_ALPHA = 0.5f

    // Animation durations

    /** Duration for sound level smoothing animation */
    const val SOUND_LEVEL_ANIM_MS = 100

    /** Duration for base pulse animation cycle */
    const val BASE_PULSE_ANIM_MS = 800

    /** Duration for ripple ring animation cycle */
    const val RIPPLE_ANIM_MS = 1500

    /** Duration for dots animation cycle */
    const val DOTS_ANIM_MS = 1200

    // Ripple ring constants

    /** Canvas size relative to bubble size */
    const val RIPPLE_CANVAS_SCALE = 1.8f

    /** Maximum expansion of ripple rings relative to bubble size */
    const val MAX_RIPPLE_EXPANSION = 0.35f

    /** Stroke width for ripple rings */
    val RIPPLE_STROKE_WIDTH = 2.dp

    /** Maximum alpha for innermost ripple ring */
    const val RIPPLE_MAX_ALPHA = 0.4f
}

/** Extension to determine if voice state should show the overlay */
private fun VoiceInputState.shouldShowOverlay(): Boolean =
    this is VoiceInputState.Listening ||
        this is VoiceInputState.Processing ||
        this is VoiceInputState.Error

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

    // Handle state transitions for results and errors
    LaunchedEffect(state) {
        when (state) {
            is VoiceInputState.Result -> {
                onSpeechResult(state.text)
                voiceInputManager.acknowledge()
            }

            is VoiceInputState.Error -> {
                delay(VoiceSearchConstants.ERROR_AUTO_DISMISS_DELAY_MS)
                voiceInputManager.acknowledge()
            }

            else -> { /* Idle, Listening, Processing - no action needed */ }
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
    if (state.shouldShowOverlay()) {
        val errorMessage = (state as? VoiceInputState.Error)?.messageResId?.let { stringResource(it) }

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

/**
 * Expanding ripple rings that pulse outward from the center.
 * Used to provide visual feedback during active listening.
 */
@Composable
private fun VoiceRippleRings(
    rippleProgress: Float,
    bubbleSize: androidx.compose.ui.unit.Dp,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val rippleStroke =
        remember(density) {
            Stroke(width = with(density) { VoiceSearchConstants.RIPPLE_STROKE_WIDTH.toPx() })
        }

    Canvas(modifier = modifier.size(bubbleSize * VoiceSearchConstants.RIPPLE_CANVAS_SCALE)) {
        val canvasCenter = center
        val baseRadius = bubbleSize.toPx() / 2
        val maxExpansion = bubbleSize.toPx() * VoiceSearchConstants.MAX_RIPPLE_EXPANSION

        for (i in 0..2) {
            // Stagger each ring's progress (offset by 0.33 each)
            val ringProgress = (rippleProgress + (i * 0.33f)) % 1f
            val ringRadius = baseRadius + (ringProgress * maxExpansion)
            val ringAlpha = (1f - ringProgress) * VoiceSearchConstants.RIPPLE_MAX_ALPHA

            drawCircle(
                color = color.copy(alpha = ringAlpha),
                radius = ringRadius,
                center = canvasCenter,
                style = rippleStroke,
            )
        }
    }
}

/**
 * Determines the status text to display based on current voice input state.
 * Returns a pair of (displayText, accessibilityText) where accessibilityText excludes animated dots.
 */
private fun getStatusText(
    errorMessage: String?,
    partialResult: String,
    isProcessing: Boolean,
    processingText: String,
    listeningText: String,
    dotCount: Int,
): Pair<String, String> {
    val dots = ".".repeat(dotCount)
    return when {
        errorMessage != null -> errorMessage to errorMessage
        partialResult.isNotBlank() -> partialResult to partialResult
        isProcessing -> (processingText + dots) to processingText
        else -> (listeningText + dots) to listeningText
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

    // Smooth transitions between sound level changes
    val animatedSoundLevel by animateFloatAsState(
        targetValue = soundLevel,
        animationSpec = tween(durationMillis = VoiceSearchConstants.SOUND_LEVEL_ANIM_MS),
        label = "soundLevel",
    )

    // Continuous subtle pulse animation (1.0x to 1.05x scale)
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val basePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = VoiceSearchConstants.BASE_PULSE_ANIM_MS),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "basePulse",
    )

    // Ripple rings animation (0.0 to 1.0, restarts)
    // Target 0f when processing/error to "pause" the effect without fast-looping
    val shouldAnimateRipples = !isProcessing && errorMessage == null
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (shouldAnimateRipples) 1f else 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = VoiceSearchConstants.RIPPLE_ANIM_MS),
                repeatMode = RepeatMode.Restart,
            ),
        label = "ripple",
    )

    // Animated dots for status text (cycles 0, 1, 2, 3)
    val dotAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = VoiceSearchConstants.DOTS_ANIM_MS),
                repeatMode = RepeatMode.Restart,
            ),
        label = "dots",
    )

    // Combine base pulse with sound-reactive scaling for the mic bubble
    val bubbleScale = basePulse + (animatedSoundLevel * VoiceSearchConstants.SOUND_LEVEL_SCALE_FACTOR)

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
                horizontalArrangement = Arrangement.spacedBy(VoiceSearchConstants.CONTENT_SPACING),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = VoiceSearchConstants.HORIZONTAL_PADDING),
            ) {
                // Mic bubble with ripple rings
                Box(contentAlignment = Alignment.Center) {
                    // Ripple rings (only when actively listening)
                    if (shouldAnimateRipples) {
                        VoiceRippleRings(
                            rippleProgress = rippleProgress,
                            bubbleSize = VoiceSearchConstants.BUBBLE_SIZE,
                            color = primaryColor,
                        )
                    }

                    // Main mic bubble
                    Box(
                        modifier =
                            Modifier
                                .size(VoiceSearchConstants.BUBBLE_SIZE)
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
                            modifier = Modifier.size(VoiceSearchConstants.MIC_ICON_SIZE),
                            tint = onPrimaryColor,
                        )
                    }
                }

                // Status text
                val processingText = stringResource(R.string.processing)
                val listeningText = stringResource(R.string.voice_search_prompt)
                val (statusText, accessibilityDescription) =
                    getStatusText(
                        errorMessage = errorMessage,
                        partialResult = partialResult,
                        isProcessing = isProcessing,
                        processingText = processingText,
                        listeningText = listeningText,
                        dotCount = dotAnimation.toInt(),
                    )

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
                color = Color.White.copy(alpha = VoiceSearchConstants.HINT_TEXT_ALPHA),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = VoiceSearchConstants.DISMISS_HINT_BOTTOM_PADDING),
            )
        }
    }
}
