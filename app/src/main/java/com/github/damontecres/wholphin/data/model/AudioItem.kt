package com.github.damontecres.wholphin.data.model

import androidx.compose.runtime.Stable
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID
import kotlin.time.Duration

@Stable
data class AudioItem(
    val id: UUID,
    val albumId: UUID?,
    val artistId: UUID?,
    val title: String?,
    val albumTitle: String?,
    val artistNames: String?,
    val runtime: Duration?,
    val imageUrl: String?,
    val hasLyrics: Boolean,
) {
    companion object {
        fun from(
            item: BaseItem,
            imageUrl: String?,
        ): AudioItem =
            AudioItem(
                id = item.id,
                albumId = item.data.albumId,
                artistId =
                    item.data.artistItems
                        ?.firstOrNull()
                        ?.id,
                title = item.title,
                albumTitle = item.data.album,
                artistNames = item.data.albumArtist,
                runtime = item.data.runTimeTicks?.ticks,
                imageUrl = imageUrl,
                hasLyrics = item.data.hasLyrics == true,
            )
    }
}
