package com.github.damontecres.wholphin.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Slides the [content] up/down as the card is [focused] or not
 *
 * This is used for the card's title, moving it down when focused as the card itself scales up
 */
@Composable
fun SlidingCardText(
    focused: Boolean,
    modifier: Modifier = Modifier,
    spaceBetweenFocused: Dp = 4.dp,
    spaceBetweenUnfocused: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val spaceBetween =
        animateDpAsState(
            if (focused) spaceBetweenFocused else spaceBetweenUnfocused,
            label = "spaceBetween",
        )
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier =
            modifier
                .layout { measurable, constraints ->
                    val topPaddingPx = (spaceBetweenUnfocused - spaceBetween.value).roundToPx()
                    val bottomPaddingPx = spaceBetween.value.roundToPx()
                    val placeable = measurable.measure(constraints)
                    layout(placeable.width, placeable.height + bottomPaddingPx + topPaddingPx) {
                        placeable.placeRelative(0, topPaddingPx)
                    }
                }.fillMaxWidth(),
        content = content,
    )
}
