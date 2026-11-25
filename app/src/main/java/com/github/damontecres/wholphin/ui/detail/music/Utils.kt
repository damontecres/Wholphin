package com.github.damontecres.wholphin.ui.detail.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.ui.DetailItemFields
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetPlaylistItemsRequestHandler
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetPlaylistItemsRequest
import java.util.UUID

suspend fun ViewModel.getPagerForAlbum(
    api: ApiClient,
    albumId: UUID,
): ApiRequestPager<GetItemsRequest> {
    val request =
        GetItemsRequest(
            parentId = albumId,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            fields = DetailItemFields,
            sortBy =
                listOf(
                    ItemSortBy.PARENT_INDEX_NUMBER,
                    ItemSortBy.INDEX_NUMBER,
                    ItemSortBy.SORT_NAME,
                ),
        )
    return ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
}

suspend fun ViewModel.getPagerForArtist(
    api: ApiClient,
    artistId: UUID,
): ApiRequestPager<GetItemsRequest> {
    val request =
        GetItemsRequest(
            artistIds = listOf(artistId),
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            fields = DetailItemFields,
            // TODO better sort
            sortBy =
                listOf(
                    ItemSortBy.PARENT_INDEX_NUMBER,
                    ItemSortBy.INDEX_NUMBER,
                    ItemSortBy.SORT_NAME,
                ),
        )
    return ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
}

/**
 * Items of a playlist in the playlist's own order.
 *
 * A recursive items query with [ItemSortBy.DEFAULT] does not keep that order: the server sorts it
 * by sort name, which for songs starts with the track number. The playlist endpoint returns the
 * entries as stored; non-audio entries are dropped when the queue is built.
 */
internal fun playlistItemsRequest(playlistId: UUID): GetPlaylistItemsRequest =
    GetPlaylistItemsRequest(
        playlistId = playlistId,
        fields = DetailItemFields,
    )

suspend fun ViewModel.getPagerForPlaylist(
    api: ApiClient,
    playlistId: UUID,
): ApiRequestPager<GetPlaylistItemsRequest> =
    ApiRequestPager(
        api,
        playlistItemsRequest(playlistId),
        GetPlaylistItemsRequestHandler,
        viewModelScope,
    ).init()
