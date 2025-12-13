package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.contentColorFor
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage

@Composable
fun Program(
    program: TvProgram,
    focused: Boolean,
    colorCode: Boolean,
    modifier: Modifier = Modifier,
) {
    val background =
        if (focused) {
            MaterialTheme.colorScheme.inverseSurface
        } else if (colorCode) {
            program.category?.color
                ?: MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        } else {
            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
        }
    val textColor = MaterialTheme.colorScheme.contentColorFor(background)
    Box(
        modifier =
            modifier
                .padding(2.dp)
                .fillMaxSize()
                .background(
                    background,
                    shape = RoundedCornerShape(4.dp),
                ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(4.dp),
        ) {
            Text(
                text = program.name ?: program.id.toString(),
                color = textColor,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier,
            )
            listOfNotNull(
                program.seasonEpisode?.let { "S${it.season} E${it.episode}" },
                program.subtitle,
            ).joinToString(" - ")
                .ifBlank { null }
                ?.let {
                    Text(
                        text = it,
                        color = textColor,
                        fontSize = 14.sp,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier,
                    )
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
    focused: Boolean,
    modifier: Modifier = Modifier,
) {
    val background =
        if (focused) {
            MaterialTheme.colorScheme.inverseSurface
        } else {
            MaterialTheme.colorScheme.surface
        }
    val textColor = MaterialTheme.colorScheme.contentColorFor(background)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    background,
                    shape = RoundedCornerShape(4.dp),
                ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .padding(4.dp)
                    .fillMaxSize(),
        ) {
            Text(
                text = channel.number ?: channel.name ?: channelIndex.toString(),
                color = textColor,
                modifier = Modifier,
            )
            AsyncImage(
                model = channel.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxHeight(.66f),
            )
        }
    }
}
