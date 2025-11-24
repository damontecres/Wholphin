package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.data.model.PlaylistInfo
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.components.baseItemKinds
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.playback.playable
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
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CreatePlaylistDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlaylistUserPermissions
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPlaylistItemsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistCreator
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) {
        /**
         * Creates a playlist of next up episodes for the given series starting with the given episode
         */
        suspend fun createFromEpisode(
            seriesId: UUID,
            episodeId: UUID?,
            seasonId: UUID? = null,
            shuffled: Boolean = false,
        ): Playlist {
            val request =
                GetEpisodesRequest(
                    seriesId = seriesId,
                    seasonId = seasonId,
                    fields = DefaultItemFields,
                    startItemId = episodeId,
                    sortBy = if (shuffled) ItemSortBy.RANDOM else null,
                    limit = Playlist.MAX_SIZE,
                )
            val episodes = GetEpisodesRequestHandler.execute(api, request).content.items
            val startIndex =
                episodeId?.let { episodes.indexOfFirstOrNull { it.id == episodeId } } ?: 0
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

        private suspend fun createFromCollection(
            item: BaseItemDto,
            startIndex: Int = 0,
            sortAndDirection: SortAndDirection?,
            filter: GetItemsFilter,
        ): Playlist {
            val includeItemTypes =
                item.collectionType
                    ?.baseItemKinds
                    ?.filter { it.playable }
                    ?.takeIf { it.isNotEmpty() }
                    ?: BaseItemKind.entries.filter { it.playable }
            val request =
                filter.applyTo(
                    overwriteIncludeTypes = false,
                    req =
                        GetItemsRequest(
                            parentId = item.id,
                            enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                            includeItemTypes = includeItemTypes,
                            recursive = true,
                            excludeItemIds = listOf(item.id),
                            sortBy = sortAndDirection?.let { listOf(sortAndDirection.sort) },
                            sortOrder = sortAndDirection?.let { listOf(sortAndDirection.direction) },
                            fields = DefaultItemFields,
                            startIndex = startIndex,
                            limit = Playlist.MAX_SIZE,
                        ),
                )
            val items =
                GetItemsRequestHandler.execute(api, request).content.items.map {
                    BaseItem.from(it, api)
                }
            return Playlist(items, 0)
        }

        suspend fun createFrom(
            item: BaseItemDto,
            startIndex: Int = 0,
            sortAndDirection: SortAndDirection?,
            shuffled: Boolean,
            recursive: Boolean,
            filter: GetItemsFilter,
        ): PlaylistCreationResult =
            when (item.type) {
                BaseItemKind.BOX_SET,
                BaseItemKind.COLLECTION_FOLDER,
                BaseItemKind.USER_VIEW,
                ->
                    PlaylistCreationResult.Success(
                        createFromCollection(
                            item = item,
                            startIndex = startIndex,
                            sortAndDirection =
                                if (shuffled) {
                                    SortAndDirection(ItemSortBy.RANDOM, SortOrder.ASCENDING)
                                } else {
                                    sortAndDirection
                                },
                            filter = filter,
                        ),
                    )

                BaseItemKind.EPISODE -> {
                    val seriesId = item.seriesId
                    if (seriesId != null) {
                        PlaylistCreationResult.Success(
                            createFromEpisode(
                                seriesId = seriesId,
                                seasonId = null,
                                episodeId = item.id,
                                shuffled = shuffled,
                            ),
                        )
                    } else {
                        PlaylistCreationResult.Error(null, "Episode has not seriesId")
                    }
                }

                BaseItemKind.SEASON -> {
                    val seriesId = item.seriesId
                    if (seriesId != null) {
                        PlaylistCreationResult.Success(
                            createFromEpisode(
                                seriesId = seriesId,
                                seasonId = item.id,
                                episodeId = null,
                                shuffled = shuffled,
                            ),
                        )
                    } else {
                        PlaylistCreationResult.Error(null, "Episode has not seriesId")
                    }
                }

                BaseItemKind.SERIES ->
                    PlaylistCreationResult.Success(
                        createFromEpisode(
                            seriesId = item.id,
                            seasonId = null,
                            episodeId = null,
                            shuffled = shuffled,
                        ),
                    )

                BaseItemKind.PLAYLIST ->
                    PlaylistCreationResult.Success(
                        createFromPlaylistId(
                            item.id,
                            startIndex,
                            shuffled,
                        ),
                    )

                // Not support yet
//                BaseItemKind.AGGREGATE_FOLDER -> TODO()
//                BaseItemKind.FOLDER -> TODO()
//                BaseItemKind.GENRE -> TODO()
//                BaseItemKind.MANUAL_PLAYLISTS_FOLDER -> TODO()
//                BaseItemKind.MUSIC_ALBUM -> TODO()
//                BaseItemKind.MUSIC_ARTIST -> TODO()

                else -> PlaylistCreationResult.Error(null, "Unsupported type: ${item.type}")
            }

        suspend fun getServerPlaylists(
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

        suspend fun createServerPlaylist(
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

        suspend fun addToServerPlaylist(
            playlistId: UUID,
            itemId: UUID,
        ) {
            api.playlistsApi.addItemToPlaylist(playlistId, listOf(itemId))
        }

        suspend fun removeFromServerPlaylist(
            playlistId: UUID,
            itemId: UUID,
        ) {
            api.playlistsApi.removeItemFromPlaylist(
                playlistId.toServerString(),
                listOf(itemId.toServerString()),
            )
        }
    }

sealed interface PlaylistCreationResult {
    data class Success(
        val playlist: Playlist,
    ) : PlaylistCreationResult

    data class Error(
        val ex: Exception?,
        val message: String?,
    ) : PlaylistCreationResult
}
