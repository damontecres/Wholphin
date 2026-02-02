package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.playOnClickSound
import com.github.damontecres.wholphin.ui.playSoundOnFocus

@Composable
fun OverviewText(
    overview: String,
    maxLines: Int,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    textBoxHeight: Dp = maxLines * 20.dp,
    enabled: Boolean = true,
    isHidden: Boolean = false,
) {
    val context = LocalContext.current
    val isFocused = interactionSource.collectIsFocusedAsState().value
    val bgColor =
        if (isFocused) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = .4f)
        } else {
            Color.Unspecified
        }
    Box(
        modifier =
            modifier
                .background(bgColor, shape = RoundedCornerShape(8.dp))
                .playSoundOnFocus(true)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                ) {
                    playOnClickSound(context)
                    onClick.invoke()
                },
    ) {
        Text(
            text =
                if (overview.isBlank()) {
                    stringResource(R.string.no_overview)
                } else if (isHidden) {
                    stringResource(R.string.unwatched_spoiler_hidden)
                } else {
                    overview
                },
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = if (isHidden || overview.isBlank()) FontStyle.Italic else FontStyle.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .padding(8.dp)
                    .height(textBoxHeight),
        )
    }
}
