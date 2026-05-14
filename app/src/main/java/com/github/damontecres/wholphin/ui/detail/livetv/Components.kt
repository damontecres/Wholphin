package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import java.time.LocalDateTime

@Composable
fun Program(
    guideStart: LocalDateTime,
    program: TvProgram,
    colorCode: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val startedBeforeGuide = program.start.isBefore(guideStart)
    val shape =
        remember(startedBeforeGuide) {
            val cornerSize = 4.dp
            if (startedBeforeGuide) {
                RoundedCornerShape(
                    topEnd = cornerSize,
                    bottomEnd = cornerSize,
                    topStart = 0.dp,
                    bottomStart = 0.dp,
                )
            } else {
                RoundedCornerShape(cornerSize)
            }
        }
    val title = program.name ?: program.id.toString()
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(1f, 1f, .95f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor =
                    if (colorCode) {
                        program.category?.color
                            ?: MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    } else {
                        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    },
                contentColor = MaterialTheme.colorScheme.onSurface,
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ),
        modifier =
            modifier
                .padding(2.dp)
                .fillMaxSize(),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
        ) {
            if (startedBeforeGuide) {
                Text(
                    text = stringResource(R.string.fa_caret_left),
                    fontFamily = FontAwesome,
                    color = LocalContentColor.current,
                    fontSize = 16.sp,
                    modifier =
                        Modifier
                            .align(Alignment.CenterVertically)
                            .padding(start = 2.dp),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = 2.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            ) {
                Text(
                    text = title,
                    color = LocalContentColor.current,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier,
                )
                val subtitle =
                    remember(program) {
                        listOfNotNull(
                            program.seasonEpisode?.let { "S${it.season} E${it.episode}" },
                            program.subtitle,
                        ).joinToString(" - ").ifBlank { null }
                    }
                subtitle?.let {
                    Text(
                        text = it,
                        color = LocalContentColor.current,
                        fontSize = 14.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier,
                    )
                }
            }
        }
        RecordingMarker(
            isRecording = program.isRecording,
            isSeriesRecording = program.isSeriesRecording,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

@Composable
fun Channel(
    channel: TvChannel,
    channelIndex: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(1f, 1f, .95f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
            ),
        modifier =
            modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(2.dp)
                .fillMaxSize(),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(4.dp)
                    .fillMaxSize(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    text = channel.number ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier,
                )
                Column(
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier,
                ) {
                    var showImage by remember { mutableStateOf(true) }
                    if (showImage) {
                        AsyncImage(
                            model = channel.imageUrl,
                            contentDescription = channel.name,
                            modifier = Modifier.weight(1f),
                            onError = {
                                showImage = false
                            },
                        )
                    }
                    channel.name?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = if (showImage) 10.sp else 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier,
                        )
                    }
                }
            }
            if (channel.favorite) {
                Text(
                    color = colorResource(android.R.color.holo_red_light),
                    text = stringResource(R.string.fa_heart),
                    fontSize = 16.sp,
                    fontFamily = FontAwesome,
                    modifier =
                        Modifier
                            .padding(2.dp)
                            .align(Alignment.TopEnd),
                )
            }
        }
    }
}
