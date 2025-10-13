package com.github.damontecres.dolphin.data.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.github.damontecres.dolphin.ui.DefaultItemFields
import com.github.damontecres.dolphin.ui.indexOfFirstOrNull
import com.github.damontecres.dolphin.util.GetEpisodesRequestHandler
import com.github.damontecres.dolphin.util.GetPlaylistItemsRequestHandler
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetPlaylistItemsRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class Playlist(
    items: List<BaseItem>,
    startIndex: Int = 0,
) {
    val items = items.subList(startIndex, items.size)

    var index by mutableIntStateOf(0)

    fun hasPrevious(): Boolean = index > 0

    fun hasNext(): Boolean = index < items.size

    fun getPreviousAndReverse(): BaseItem = items[--index]

    fun getAndAdvance(): BaseItem = items[++index]

    fun peek(): BaseItem? = items.getOrNull(index + 1)

    companion object {
        const val MAX_SIZE = 100
    }
}

@Singleton
class PlaylistCreator
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        suspend fun createFromEpisode(
            seriesId: UUID,
            episodeId: UUID,
        ): Playlist {
            val request =
                GetEpisodesRequest(
                    seriesId = seriesId,
                    fields = DefaultItemFields,
                    startItemId = episodeId,
                    limit = Playlist.MAX_SIZE,
                )
            val episodes = GetEpisodesRequestHandler.execute(api, request).content.items
            val startIndex =
                episodes.indexOfFirstOrNull { it.id == episodeId }
                    ?: throw IllegalStateException("Episode $episodeId was not returned")
            return Playlist(episodes.map { BaseItem.from(it, api) }, startIndex)
        }

        suspend fun createFromPlaylistId(
            playlistId: UUID,
            startIndex: Int?,
        ): Playlist {
            val request =
                GetPlaylistItemsRequest(
                    playlistId = playlistId,
                    fields = DefaultItemFields,
                    startIndex = startIndex,
                    limit = Playlist.MAX_SIZE,
                )
            val items = GetPlaylistItemsRequestHandler.execute(api, request).content.items
            return Playlist(items.map { BaseItem.from(it, api) }, 0)
        }
    }
