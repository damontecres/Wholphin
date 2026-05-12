package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.InfiniteRepeatableSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.skipStringRes
import com.github.damontecres.wholphin.ui.theme.PreviewInteractionSource
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import org.jellyfin.sdk.model.api.MediaSegmentType

@Composable
fun SkipSegmentButton(
    type: MediaSegmentType,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource? = null,
) {
    val infiniteTransition = rememberInfiniteTransition("SkipSegmentButton")
    val color by infiniteTransition.animateColor(
        initialValue = MaterialTheme.colorScheme.onSurface,
        targetValue = Color.Transparent,
        animationSpec =
            InfiniteRepeatableSpec(
                animation = tween(durationMillis = 1500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "SkipSegmentButton_color",
    )
    Button(
        onClick = onClick,
        modifier =
            modifier.drawBehind {
                val px = 6.dp.toPx()
                drawRoundRect(
                    color = color,
                    cornerRadius = CornerRadius(this.size.width * .5f),
                    topLeft = Offset(-px, -px),
                    size = Size(this.size.width + px * 2, this.size.height + px * 2),
                )
            },
        onLongClick = onLongClick,
        enabled = true,
        contentPadding =
            PaddingValues(
                start = 8.dp,
                top = 4.dp,
                end = 8.dp,
                bottom = 4.dp,
            ),
        contentHeight = 32.dp,
        interactionSource = interactionSource,
        content = {
            Text(text = stringResource(type.skipStringRes))
        },
    )
}

@PreviewTvSpec
@Composable
fun SkipSegmentButtonPreview() {
    WholphinTheme {
        val source = remember { PreviewInteractionSource() }
        SkipSegmentButton(
            type = MediaSegmentType.INTRO,
            onClick = {},
            modifier = Modifier.padding(16.dp),
            interactionSource = source,
        )
    }
}
