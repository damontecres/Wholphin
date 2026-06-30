package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.jellyfin.sdk.model.api.TrickplayInfo
import kotlin.time.Duration.Companion.seconds

@Composable
fun TrickplayPreview(
    seekProgressMs: Long,
    trickplayInfo: TrickplayInfo?,
    trickplayUrlFor: (Int) -> String?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        if (trickplayInfo != null) {
            val tilesPerImage = trickplayInfo.tileWidth * trickplayInfo.tileHeight
            val index =
                (seekProgressMs / trickplayInfo.interval).toInt() / tilesPerImage
            val imageUrl = remember(index) { trickplayUrlFor(index) }

            if (imageUrl != null) {
                SeekPreviewImage(
                    modifier = Modifier,
                    previewImageUrl = imageUrl,
                    seekProgressMs = seekProgressMs,
                    trickPlayInfo = trickplayInfo,
                )
            }
        }
        Text(
            text = (seekProgressMs / 1000L).seconds.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            modifier =
                Modifier
                    .background(
                        Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(4.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
