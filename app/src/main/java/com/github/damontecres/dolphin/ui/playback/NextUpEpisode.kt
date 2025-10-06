package com.github.damontecres.dolphin.ui.playback

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.dolphin.ui.PreviewTvSpec
import com.github.damontecres.dolphin.ui.cards.BannerCard
import com.github.damontecres.dolphin.ui.theme.DolphinTheme
import com.github.damontecres.dolphin.ui.tryRequestFocus

@Composable
fun NextUpEpisode(
    title: String?,
    description: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 16f / 9,
) {
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
                    .padding(8.dp),
        ) {
            Text(
                text = "Up Next...",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp),
            ) {
                Box {
                    BannerCard(
                        imageUrl = imageUrl,
                        onClick = onClick,
                        onLongClick = {},
                        cardHeight = 100.dp,
                        aspectRatio = aspectRatio,
                        modifier =
                            Modifier
                                .focusRequester(focusRequester)
                                .align(Alignment.Center),
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.border,
                        modifier =
                            Modifier
                                .size(60.dp)
                                .align(Alignment.Center),
                    )
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    Text(
                        text = title ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier,
                    )
                    Text(
                        text = description ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

@PreviewTvSpec
@Composable
fun NextUpEpisodePreview() {
    DolphinTheme(true) {
        NextUpEpisode(
            title = "Episode Title",
            description = "This is the description of the episode. It might be long.",
            imageUrl = "",
            onClick = {},
            aspectRatio = 4f / 3,
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
