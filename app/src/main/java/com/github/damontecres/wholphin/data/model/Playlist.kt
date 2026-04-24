package com.github.damontecres.wholphin.data.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID

/**
 * Tracks playback of multiple items. Points to the current media with function to advance or go to previous ones.
 *
 * This is not the same thing as a Jellyfin server playlist
 */
class Playlist(
    items: List<PlaylistItem>,
    startIndex: Int = 0,
) {
    val items = items.subList(startIndex, items.size)

    var index by mutableIntStateOf(0)

    fun hasPrevious(): Boolean = index > 0

    fun hasNext(): Boolean = (index + 1) < items.size

    fun getPreviousAndReverse(): PlaylistItem = items[--index]

    fun getAndAdvance(): PlaylistItem = items[++index]

    fun peek(): PlaylistItem? = items.getOrNull(index + 1)

    fun upcomingItems(): List<PlaylistItem> = items.subList(index + 1, items.size)

    fun advanceTo(id: UUID): PlaylistItem? {
        while (hasNext()) {
            val potential = getAndAdvance()
            if (potential.id == id) {
                return potential
            }
        }
        return null
    }

    companion object {
        const val MAX_SIZE = 100
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

    data class Media(
        val item: BaseItem,
    ) : PlaylistItem {
        override val id: UUID
            get() = item.id
    }

    data class Intro(
        val item: BaseItem,
    ) : PlaylistItem {
        override val id: UUID
            get() = item.id
    }
}
