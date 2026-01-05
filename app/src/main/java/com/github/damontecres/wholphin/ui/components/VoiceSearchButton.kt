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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import kotlinx.coroutines.delay

private const val ERROR_AUTO_DISMISS_DELAY_MS = 3000L
private const val SOUND_LEVEL_SCALE_FACTOR = 0.15f
private val BUBBLE_SIZE = 160.dp
private val MIC_ICON_FONT_SIZE = 56.sp
private val BUTTON_ICON_FONT_SIZE = 20.sp
private val CONTENT_SPACING = 48.dp
private val HORIZONTAL_PADDING = 64.dp
private val DISMISS_HINT_BOTTOM_PADDING = 32.dp
private const val HINT_TEXT_ALPHA = 0.5f
private const val SOUND_LEVEL_ANIM_MS = 100
private const val BASE_PULSE_ANIM_MS = 800
private const val RIPPLE_ANIM_MS = 1500
private const val DOTS_ANIM_MS = 1200
private const val RIPPLE_CANVAS_SCALE = 1.8f
private const val MAX_RIPPLE_EXPANSION = 0.35f
private val RIPPLE_STROKE_WIDTH = 2.dp
private const val RIPPLE_MAX_ALPHA = 0.4f

private fun VoiceInputState.shouldShowOverlay() =
    this is VoiceInputState.Listening ||
        this is VoiceInputState.Processing ||
        this is VoiceInputState.Error

@Composable
fun VoiceSearchButton(
    onSpeechResult: (String) -> Unit,
    voiceInputManager: VoiceInputManager?,
    modifier: Modifier = Modifier,
) {
    if (voiceInputManager == null || !voiceInputManager.isAvailable) return

    val state = voiceInputManager.state

    LaunchedEffect(state) {
        when (state) {
            is VoiceInputState.Result -> {
                onSpeechResult(state.text)
                voiceInputManager.acknowledge()
            }

            is VoiceInputState.Error -> {
                delay(ERROR_AUTO_DISMISS_DELAY_MS)
                voiceInputManager.acknowledge()
            }

            else -> {}
        }
    }

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
            val voiceSearchDesc = stringResource(R.string.voice_search)
            Text(
                text = stringResource(R.string.fa_microphone),
                fontFamily = FontAwesome,
                fontSize = BUTTON_ICON_FONT_SIZE,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { contentDescription = voiceSearchDesc },
            )
        }
    }
}

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
            Stroke(width = with(density) { RIPPLE_STROKE_WIDTH.toPx() })
        }

    Canvas(modifier = modifier.size(bubbleSize * RIPPLE_CANVAS_SCALE)) {
        val canvasCenter = center
        val baseRadius = bubbleSize.toPx() / 2
        val maxExpansion = bubbleSize.toPx() * MAX_RIPPLE_EXPANSION

        for (i in 0..2) {
            val ringProgress = (rippleProgress + (i * 0.33f)) % 1f
            val ringRadius = baseRadius + (ringProgress * maxExpansion)
            val ringAlpha = (1f - ringProgress) * RIPPLE_MAX_ALPHA
            drawCircle(
                color = color.copy(alpha = ringAlpha),
                radius = ringRadius,
                center = canvasCenter,
                style = rippleStroke,
            )
        }
    }
}

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

    val animatedSoundLevel by animateFloatAsState(
        targetValue = soundLevel,
        animationSpec = tween(durationMillis = SOUND_LEVEL_ANIM_MS),
        label = "soundLevel",
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val basePulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = BASE_PULSE_ANIM_MS),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "basePulse",
    )

    val shouldAnimateRipples = !isProcessing && errorMessage == null
    val rippleProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (shouldAnimateRipples) 1f else 0f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = RIPPLE_ANIM_MS),
                repeatMode = RepeatMode.Restart,
            ),
        label = "ripple",
    )

    val dotAnimation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 4f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = DOTS_ANIM_MS),
                repeatMode = RepeatMode.Restart,
            ),
        label = "dots",
    )

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
                    .background(Color.Black.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CONTENT_SPACING),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = HORIZONTAL_PADDING),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (shouldAnimateRipples) {
                        VoiceRippleRings(
                            rippleProgress = rippleProgress,
                            bubbleSize = BUBBLE_SIZE,
                            color = primaryColor,
                        )
                    }

                    Box(
                        modifier =
                            Modifier
                                .size(BUBBLE_SIZE)
                                .graphicsLayer {
                                    scaleX = bubbleScale
                                    scaleY = bubbleScale
                                }.clip(CircleShape)
                                .background(if (errorMessage != null) errorColor else primaryColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        val voiceSearchDesc = stringResource(R.string.voice_search)
                        Text(
                            text = stringResource(R.string.fa_microphone),
                            fontFamily = FontAwesome,
                            fontSize = MIC_ICON_FONT_SIZE,
                            color = onPrimaryColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.semantics { contentDescription = voiceSearchDesc },
                        )
                    }
                }

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

            Text(
                text = stringResource(R.string.press_back_to_cancel),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = HINT_TEXT_ALPHA),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = DISMISS_HINT_BOTTOM_PADDING),
            )
        }
    }
}
