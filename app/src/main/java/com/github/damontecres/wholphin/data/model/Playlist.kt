package com.github.damontecres.wholphin.data.model

import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID

/**
 * Tracks playback of multiple items. Points to the current media with function to advance or go to previous ones.
 *
 * This is not the same thing as a Jellyfin server playlist
 */
data class Playlist(
    val items: List<PlaylistItem>,
) {
    companion object {
        const val MAX_SIZE = 100

        fun fromMedia(list: List<BaseItem>) = Playlist(list.map { PlaylistItem.Media(it) })
    }
}

data class PlaylistInfo(
    val id: UUID,
    val name: String,
    val count: Int,
    val mediaType: MediaType,
)

sealed interface PlaylistItem {
    val id: UUID
    val item: BaseItem

    data class Media(
        override val item: BaseItem,
    ) : PlaylistItem {
        override val id: UUID
            get() = item.id
    }

    data class Intro(
        override val item: BaseItem,
    ) : PlaylistItem {
        override val id: UUID
            get() = item.id
    }
}
