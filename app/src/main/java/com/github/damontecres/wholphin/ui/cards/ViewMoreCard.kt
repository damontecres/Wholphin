package com.github.damontecres.wholphin.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import kotlinx.coroutines.delay

@Composable
fun ViewMoreCard(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    aspectRatio: AspectRatio = AspectRatio.TALL,
    size: DpSize = DpSize(width = Cards.height2x3 * aspectRatio.ratio, height = Cards.height2x3),
    showTitle: Boolean = true,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    val spaceBetween by animateDpAsState(if (focused) 12.dp else 4.dp)
    val spaceBelow by animateDpAsState(if (focused) 4.dp else 12.dp)
    var focusedAfterDelay by remember { mutableStateOf(false) }

    val hideOverlayDelay = 500L
    if (focused) {
        LaunchedEffect(Unit) {
            delay(hideOverlayDelay)
            if (focused) {
                focusedAfterDelay = true
            } else {
                focusedAfterDelay = false
            }
        }
    } else {
        focusedAfterDelay = false
    }
    val width =
        remember(size, aspectRatio) {
            size.width.takeIf { it.isSpecified } ?: (size.height * aspectRatio.ratio)
        }
    val height =
        remember(size, aspectRatio) {
            size.height.takeIf { it.isSpecified } ?: (size.height * (1f / aspectRatio.ratio))
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(spaceBetween),
        modifier = modifier,
    ) {
        Card(
            modifier =
                Modifier
                    .size(width, height)
                    .aspectRatio(aspectRatio.ratio),
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            colors =
                CardDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                ),
        ) {
            Box {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    tint = MaterialTheme.colorScheme.onSurface,
                    contentDescription = "View more",
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (showTitle) {
            Column(
                verticalArrangement = Arrangement.spacedBy(0.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .width(width)
                        .padding(bottom = spaceBelow),
            ) {
                Text(
                    text = stringResource(R.string.view_more),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    modifier =
                        Modifier
                            .width(width)
                            .padding(horizontal = 4.dp)
                            .enableMarquee(focusedAfterDelay),
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun Preview() {
    WholphinTheme {
        Column {
            ViewMoreCard(
                onClick = {},
                onLongClick = {},
                modifier = Modifier.padding(16.dp),
                aspectRatio = AspectRatio.TALL,
                size = DpSize(width = Dp.Unspecified, height = Cards.heightEpisode),
                showTitle = false,
            )
        }
    }
}
