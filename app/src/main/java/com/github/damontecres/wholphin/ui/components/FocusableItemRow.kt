package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * Placeholder for [com.github.damontecres.wholphin.ui.cards.ItemRow]. It is [focusable] so it can be scrolled.
 */
@Composable
@NonRestartableComposable
fun FocusableItemRow(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) = FocusableItemRow(
    titleContent = {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    },
    subtitleContent = {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )
    },
    modifier = modifier,
)

@Composable
fun FocusableItemRow(
    titleContent: @Composable () -> Unit,
    subtitleContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val padding by animateDpAsState(if (focused) 8.dp else 0.dp)
    val scale by animateFloatAsState(if (focused) 1.1f else 1f)
    val background by animateColorAsState(
        if (focused) {
            MaterialTheme.colorScheme.border.copy(alpha = .25f)
        } else {
            Color.Unspecified
        },
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .padding(start = 8.dp)
                .focusable(interactionSource = interactionSource)
                .scale(scale)
                .background(background, shape = RoundedCornerShape(8.dp))
                .padding(padding),
    ) {
        titleContent.invoke()
        subtitleContent.invoke()
    }
}
