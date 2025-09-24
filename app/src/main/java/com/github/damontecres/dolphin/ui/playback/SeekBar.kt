package com.github.damontecres.stashapp.ui.components.playback

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.dolphin.ui.handleDPadKeyEvents
import com.github.damontecres.dolphin.ui.playback.ControllerViewState

@Composable
fun SeekBarImpl(
    progress: Float,
    bufferedProgress: Float,
    onSeek: (seekProgress: Float) -> Unit,
    controllerViewState: ControllerViewState,
    modifier: Modifier = Modifier,
    intervals: Int = 10,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val color = MaterialTheme.colorScheme.border
    val animatedIndicatorHeight by animateDpAsState(
        targetValue = 6.dp.times((if (isFocused) 2f else 1f)),
    )
    var hasSeeked by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableFloatStateOf(progress) }
    val progressToUse = if (isFocused && hasSeeked) seekProgress else progress
    LaunchedEffect(isFocused) {
        if (!isFocused) hasSeeked = false
    }

    val offset = 1f / intervals

    val handleSeekEventModifier =
        Modifier.handleDPadKeyEvents(
            onCenter = {
                controllerViewState.pulseControls()
                onSeek(seekProgress)
            },
            onLeft = {
                controllerViewState.pulseControls()
                seekProgress = (progressToUse - offset).coerceAtLeast(0f)
                hasSeeked = true
                onSeek(seekProgress)
            },
            onRight = {
                controllerViewState.pulseControls()
                seekProgress = (progressToUse + offset).coerceAtMost(1f)
                hasSeeked = true
                onSeek(seekProgress)
            },
        )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(animatedIndicatorHeight)
                    .padding(horizontal = 4.dp)
                    .then(handleSeekEventModifier)
                    .focusable(enabled = enabled, interactionSource = interactionSource),
            onDraw = {
                val yOffset = size.height.div(2)
                drawLine(
                    color = color.copy(alpha = 0.15f),
                    start = Offset(x = 0f, y = yOffset),
                    end = Offset(x = size.width, y = yOffset),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color.copy(alpha = 0.50f),
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
                            x = size.width.times(progressToUse),
                            y = yOffset,
                        ),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = Color.White,
                    radius = size.height + 2,
                    center = Offset(x = size.width.times(progressToUse), y = yOffset),
                )
            },
        )
    }
}
