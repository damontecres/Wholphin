package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.letNotEmpty

@Composable
fun PlaybackDebugOverlay(
    currentPlayback: CurrentPlayback?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 8.dp, top = 8.dp),
        ) {
            Text(
                text = "Backend: ${currentPlayback?.backend}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier,
            )
            Text(
                text = "Play method: ${currentPlayback?.playMethod?.serialName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier,
            )
            Text(
                text = "Video decoder: ${currentPlayback?.videoDecoder}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier,
            )
            Text(
                text = "Audio decoder: ${currentPlayback?.audioDecoder}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier,
            )
        }
        currentPlayback?.tracks?.letNotEmpty {
            PlaybackTrackInfo(
                trackSupport = it,
            )
        }
    }
}
