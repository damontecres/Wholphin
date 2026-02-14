package com.github.damontecres.wholphin.ui.playback

/*
 * Modified from https://github.com/android/tv-samples
 *
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.view.KeyEvent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import kotlinx.coroutines.FlowPreview
import kotlin.time.Duration

@Composable
fun SteppedSeekBarImpl(
    progress: Float,
    durationMs: Long,
    bufferedProgress: Float,
    onSeek: (Long) -> Unit,
    controllerViewState: ControllerViewState,
    modifier: Modifier = Modifier,
    intervals: Int = 10,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    var hasSeeked by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(progress) }
    val progressToUse = if (isFocused && hasSeeked) seekProgress else progress
    LaunchedEffect(isFocused) {
        if (!isFocused) hasSeeked = false
    }

    val offset = 1f / intervals

    val seek = { percent: Float ->
        onSeek((percent * durationMs).toLong())
    }

    SeekBarDisplay(
        enabled = enabled,
        progress = progressToUse,
        bufferedProgress = bufferedProgress,
        durationMs = durationMs,
        onLeft = { multiplier ->
            controllerViewState.pulseControls()
            seekProgress = (seekProgress - offset * multiplier).coerceAtLeast(0f)
            hasSeeked = true
            seek(seekProgress)
        },
        onRight = { multiplier ->
            controllerViewState.pulseControls()
            seekProgress = (seekProgress + offset * multiplier).coerceAtMost(1f)
            hasSeeked = true
            seek(seekProgress)
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

@OptIn(FlowPreview::class)
@Composable
fun IntervalSeekBarImpl(
    progress: Float,
    durationMs: Long,
    bufferedProgress: Float,
    onSeek: (Long) -> Unit,
    controllerViewState: ControllerViewState,
    seekBack: Duration,
    seekForward: Duration,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    var hasSeeked by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableLongStateOf((progress * durationMs).toLong()) }
//    val progressToUse by remember { derivedStateOf { if (isFocused && hasSeeked) seekPositionMs else (progress * durationMs).toLong() } }
    val progressToUse =
        if (isFocused && hasSeeked) seekPositionMs else (progress * durationMs).toLong()

    LaunchedEffect(isFocused) {
        if (!isFocused) hasSeeked = false
    }

    SeekBarDisplay(
        enabled = enabled,
        progress = (progressToUse.toDouble() / durationMs).toFloat(),
        bufferedProgress = bufferedProgress,
        durationMs = durationMs,
        onLeft = { multiplier ->
            controllerViewState.pulseControls()
            seekPositionMs = (seekPositionMs - seekBack.inWholeMilliseconds * multiplier).coerceAtLeast(0L)
            hasSeeked = true
            onSeek(seekPositionMs)
        },
        onRight = { multiplier ->
            controllerViewState.pulseControls()
            seekPositionMs =
                (seekPositionMs + seekForward.inWholeMilliseconds * multiplier).coerceAtMost(durationMs)
            hasSeeked = true
            onSeek(seekPositionMs)
        },
        interactionSource = interactionSource,
        modifier = modifier,
    )
}

@Composable
fun SeekBarDisplay(
    progress: Float,
    bufferedProgress: Float,
    durationMs: Long,
    onLeft: (Int) -> Unit,
    onRight: (Int) -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val color = MaterialTheme.colorScheme.border
    val onSurface = MaterialTheme.colorScheme.onSurface

    val isFocused by interactionSource.collectIsFocusedAsState()
    val animatedIndicatorHeight by animateDpAsState(
        targetValue = 6.dp.times((if (isFocused) 2f else 1f)),
    )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(animatedIndicatorHeight)
                    .padding(horizontal = 4.dp)
                    .onPreviewKeyEvent { event ->
                        when (event.nativeKeyEvent.keyCode) {
                            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_LEFT -> {
                                when (event.type) {
                                    KeyEventType.KeyDown -> {
                                        onLeft.invoke(
                                            calculateSeekAccelerationMultiplier(
                                                repeatCount = event.nativeKeyEvent.repeatCount,
                                                durationMs = durationMs,
                                            ),
                                        )
                                    }

                                    KeyEventType.KeyUp -> {
                                        // Consume so key-up does not trigger an additional seek.
                                    }

                                    else -> {
                                        return@onPreviewKeyEvent false
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }

                            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_SYSTEM_NAVIGATION_RIGHT -> {
                                when (event.type) {
                                    KeyEventType.KeyDown -> {
                                        onRight.invoke(
                                            calculateSeekAccelerationMultiplier(
                                                repeatCount = event.nativeKeyEvent.repeatCount,
                                                durationMs = durationMs,
                                            ),
                                        )
                                    }

                                    KeyEventType.KeyUp -> {
                                        // Consume so key-up does not trigger an additional seek.
                                    }

                                    else -> {
                                        return@onPreviewKeyEvent false
                                    }
                                }
                                return@onPreviewKeyEvent true
                            }
                        }
                        false
                    }.focusable(enabled = enabled, interactionSource = interactionSource),
            onDraw = {
                val yOffset = size.height.div(2)
                drawLine(
                    color = onSurface.copy(alpha = 0.25f),
                    start = Offset(x = 0f, y = yOffset),
                    end = Offset(x = size.width, y = yOffset),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = onSurface.copy(alpha = .65f),
                    start = Offset(x = 0f, y = yOffset),
                    end =
                        Offset(
                            x = size.width.times(bufferedProgress),
                            y = yOffset,
                        ),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(x = 0f, y = yOffset),
                    end =
                        Offset(
//                        x = size.width.times(if (isSelected) seekProgress else progress),
                            x = size.width.times(progress),
                            y = yOffset,
                        ),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = Color.White,
                    radius = size.height + 2,
                    center = Offset(x = size.width.times(progress), y = yOffset),
                )
            },
        )
    }
}
