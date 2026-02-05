package com.github.damontecres.wholphin.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.handleDPadKeyEvents
import com.github.damontecres.wholphin.ui.theme.LocalTheme

/**
 * A TV capable control for choosing a value
 */
@Composable
fun SliderBar(
    value: Long,
    min: Long,
    max: Long,
    onChange: (Long) -> Unit,
    enableWrapAround: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    interval: Int = 1,
    colors: SliderColors = SliderColors.default(),
) {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val animatedIndicatorHeight by animateDpAsState(
        targetValue = 6.dp.times((if (isFocused) 2f else 1f)),
    )
    var currentValue by remember(value) { mutableLongStateOf(value) }
    val percent = (currentValue - min).toFloat() / (max - min)

    val handleSeekEventModifier =
        Modifier.handleDPadKeyEvents(
            triggerOnAction = KeyEvent.ACTION_DOWN,
            onCenter = {
                onChange(currentValue)
            },
            onLeft = {
                if (enableWrapAround && currentValue <= min) {
                    currentValue = max
                } else {
                    currentValue = (currentValue - interval).coerceAtLeast(min)
                }
                onChange(currentValue)
            },
            onRight = {
                if (enableWrapAround && currentValue >= max) {
                    currentValue = min
                } else {
                    currentValue = (currentValue + interval).coerceAtMost(max)
                }
                onChange(currentValue)
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
                    .focusable(interactionSource = interactionSource),
            onDraw = {
                val yOffset = size.height.div(2)
                drawLine(
                    color = if (isFocused) colors.inactiveFocused else colors.inactiveUnfocused,
                    start = Offset(x = 0f, y = yOffset),
                    end = Offset(x = size.width, y = yOffset),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = if (isFocused) colors.activeFocused else colors.activeUnfocused,
                    start = Offset(x = 0f, y = yOffset),
                    end =
                        Offset(
//                        x = size.width.times(if (isSelected) seekProgress else progress),
                            x = size.width.times(percent),
                            y = yOffset,
                        ),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
                drawCircle(
                    color = Color.White,
                    radius = size.height + 2,
                    center = Offset(x = size.width.times(percent), y = yOffset),
                )
            },
        )
    }
}

data class SliderColors(
    val activeFocused: Color,
    val activeUnfocused: Color,
    val inactiveFocused: Color,
    val inactiveUnfocused: Color,
) {
    companion object {
        @Composable
        fun default() =
            SliderColors(
                activeFocused = SliderActiveColor(true),
                activeUnfocused = SliderActiveColor(false),
                inactiveFocused = SliderInactiveColor(true),
                inactiveUnfocused = SliderInactiveColor(false),
            )
    }
}

@Composable
fun SliderActiveColor(focused: Boolean): Color {
    val theme = LocalTheme.current
    return when (theme) {
        AppThemeColors.UNRECOGNIZED,
        AppThemeColors.PURPLE,
        AppThemeColors.BLUE,
        AppThemeColors.GREEN,
        AppThemeColors.ORANGE,
        -> {
            MaterialTheme.colorScheme.border
        }

        AppThemeColors.BOLD_BLUE -> {
            if (focused) {
                MaterialTheme.colorScheme.border
            } else {
                MaterialTheme.colorScheme.border
            }
        }

        AppThemeColors.OLED_BLACK -> {
            if (focused) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.border
            }
        }
    }
}

@Composable
fun SliderInactiveColor(focused: Boolean): Color {
    val theme = LocalTheme.current
    return when (theme) {
        AppThemeColors.UNRECOGNIZED,
        AppThemeColors.PURPLE,
        -> {
            MaterialTheme.colorScheme.border
                .copy(alpha = .25f)
                .compositeOver(MaterialTheme.colorScheme.surfaceVariant)
                .copy(alpha = .66f)
        }

        AppThemeColors.BLUE,
        AppThemeColors.GREEN,
        AppThemeColors.ORANGE,
        AppThemeColors.BOLD_BLUE,
        -> {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .66f)
        }

        AppThemeColors.OLED_BLACK -> {
            if (focused) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.primaryContainer
            }
        }
    }
}
