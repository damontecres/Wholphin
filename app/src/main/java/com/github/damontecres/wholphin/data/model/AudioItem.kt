package com.github.damontecres.wholphin.data.model

import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID
import kotlin.time.Duration

data class AudioItem(
    val id: UUID,
    val albumId: UUID?,
    val title: String?,
    val albumTitle: String?,
    val artistNames: String?,
    val runtime: Duration?,
) {
    companion object {
        fun from(item: BaseItem): AudioItem =
            AudioItem(
                id = item.id,
                albumId = item.data.albumId,
                title = item.title,
                albumTitle = item.data.album,
                artistNames = item.data.albumArtist,
                runtime = item.data.runTimeTicks?.ticks,
            )
    }
}
