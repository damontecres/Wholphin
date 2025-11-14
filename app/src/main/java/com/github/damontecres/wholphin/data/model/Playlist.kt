package com.github.damontecres.wholphin.data.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID

class Playlist(
    items: List<BaseItem>,
    startIndex: Int = 0,
) {
    val items = items.subList(startIndex, items.size)

    var index by mutableIntStateOf(0)

    fun hasPrevious(): Boolean = index > 0

    fun hasNext(): Boolean = (index + 1) < items.size

    fun getPreviousAndReverse(): BaseItem = items[--index]

    fun getAndAdvance(): BaseItem = items[++index]

    fun peek(): BaseItem? = items.getOrNull(index + 1)

    fun upcomingItems(): List<BaseItem> = items.subList(index + 1, items.size)

    fun advanceTo(id: UUID): BaseItem? {
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
