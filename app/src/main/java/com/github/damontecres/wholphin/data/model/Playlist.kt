package com.github.damontecres.wholphin.data.model

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetEpisodesRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetPlaylistItemsRequestHandler
import com.github.damontecres.wholphin.util.TransformList
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playlistsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlaylistUserPermissions
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPlaylistItemsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
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

@Singleton
class PlaylistCreator
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
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
            shuffled: Boolean,
        ): Playlist {
            val request =
                GetPlaylistItemsRequest(
                    playlistId = playlistId,
                    fields = DefaultItemFields,
                    startIndex = startIndex,
                    limit = Playlist.MAX_SIZE,
                )
            val items = GetPlaylistItemsRequestHandler.execute(api, request).content.items
            var baseItems = items.map { BaseItem.from(it, api) }
            if (shuffled) {
                baseItems = baseItems.shuffled()
            }
            return Playlist(baseItems, 0)
        }

        suspend fun getPlaylists(
            mediaType: MediaType?,
            scope: CoroutineScope,
        ): List<PlaylistInfo?> {
            val request =
                GetItemsRequest(
                    includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                    mediaTypes = mediaType?.let { listOf(mediaType) },
                    recursive = true,
                )
            val pager = ApiRequestPager(api, request, GetItemsRequestHandler, scope).init()
            return TransformList(pager) {
                it?.let {
                    PlaylistInfo(
                        id = it.id,
                        name = it.name ?: context.getString(R.string.unknown),
                        count = it.data.childCount ?: 0,
                        mediaType = it.data.mediaType,
                    )
                }
            }
        }

        suspend fun createPlaylist(
            name: String,
            initialItems: List<UUID>,
        ): UUID? =
            serverRepository.currentUser.value?.let { user ->
                api.playlistsApi
                    .createPlaylist(
                        CreatePlaylistDto(
                            name = name,
                            ids = initialItems,
                            users = listOf(PlaylistUserPermissions(user.id, true)),
                            isPublic = false,
                        ),
                    ).content.id
                    .toUUIDOrNull()
            }

        suspend fun addToPlaylist(
            playlistId: UUID,
            itemId: UUID,
        ) {
            api.playlistsApi.addItemToPlaylist(playlistId, listOf(itemId))
        }

        suspend fun removeFromPlaylist(
            playlistId: UUID,
            itemId: UUID,
        ) {
            api.playlistsApi.removeItemFromPlaylist(
                playlistId.toServerString(),
                listOf(itemId.toServerString()),
            )
        }
    }
