package com.github.damontecres.dolphin.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.util.TrackSupport

@Composable
fun PlaybackTrackInfo(
    trackSupport: List<TrackSupport>,
    modifier: Modifier = Modifier,
) {
    val selectedWeight = .5f
    val weights = listOf(.25f, .4f, .5f, 1f, 1f)
    val textStyle =
        MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onBackground)

    LazyColumn(
        modifier = modifier,
    ) {
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier,
            ) {
                val texts =
                    listOf(
                        "ID",
                        "Type",
                        "Codec",
                        "Supported",
                        "Labels",
                    )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(selectedWeight),
                ) {
                    Text(
                        text = "Selected",
                        style = textStyle,
                    )
                }
                texts.forEachIndexed { index, text ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.weight(weights[index]),
                    ) {
                        Text(
                            text = text,
                            style = textStyle,
                        )
                    }
                }
            }
        }
        items(trackSupport) { track ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier,
            ) {
                val texts =
                    listOf(
                        track.id ?: "",
                        track.type.name,
                        track.codecs ?: "",
                        track.supported.name,
                        track.labels.joinToString(", "),
                    )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(selectedWeight),
                ) {
                    if (track.selected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(12.dp),
                        )
                    } else {
                        Text(
                            text = "-",
                            style = textStyle,
                        )
                    }
                }
                texts.forEachIndexed { index, text ->
                    Box(
                        contentAlignment = Alignment.CenterStart,
                        modifier = Modifier.weight(weights[index]),
                    ) {
                        Text(
                            text = text,
                            style = textStyle,
                        )
                    }
                }
            }
        }
    }
}
