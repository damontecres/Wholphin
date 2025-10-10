package com.github.damontecres.dolphin.ui.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import com.github.damontecres.dolphin.ui.AppColors
import com.github.damontecres.dolphin.ui.PreviewTvSpec
import com.github.damontecres.dolphin.ui.theme.DolphinTheme
import com.github.damontecres.dolphin.ui.tryRequestFocus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Layout for showing the next up episode during playback
 */
@Composable
fun NextUpEpisode(
    title: String?,
    description: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    timeLeft: Duration?,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Box(
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Up Next...",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp),
            ) {
                NextUpCard(
                    imageUrl = imageUrl,
                    onClick = onClick,
                    timeLeft = timeLeft,
                    interactionSource = interactionSource,
                    modifier =
                        Modifier
//                            .fillMaxWidth(.4f)
//                            .fillMaxHeight()
                            .focusRequester(focusRequester),
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .weight(1f),
                ) {
                    Text(
                        text = title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier,
                    )
                    Text(
                        text = description ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

@Composable
fun NextUpCard(
    imageUrl: String?,
    onClick: () -> Unit,
    timeLeft: Duration?,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        onClick = onClick,
        interactionSource = interactionSource,
    ) {
        Box(modifier = Modifier) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier,
            )
            if (timeLeft != null && timeLeft > Duration.ZERO) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .background(
                                AppColors.TransparentBlack50,
                                shape = CircleShape,
                            ),
                ) {
                    Text(
                        text = timeLeft.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .size(60.dp)
                            .align(Alignment.Center),
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun NextUpEpisodePreview() {
    DolphinTheme(true) {
        NextUpEpisode(
            title = "Episode Title",
            description = "This is the description of the episode. It might be long.",
            imageUrl = "",
            onClick = {},
            aspectRatio = 4f / 3,
            timeLeft = 30.seconds,
            modifier =
                Modifier
                    .padding(16.dp)
                    .height(200.dp)
                    .width(400.dp)
//                    .fillMaxWidth(.4f)
//                .align(Alignment.BottomCenter)
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                        shape = RoundedCornerShape(8.dp),
                    ),
        )
    }
}
