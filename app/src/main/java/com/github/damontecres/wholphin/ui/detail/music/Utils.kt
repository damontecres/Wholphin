package com.github.damontecres.wholphin.ui.detail.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

suspend fun ViewModel.getPagerForAlbum(
    api: ApiClient,
    albumId: UUID,
): ApiRequestPager<GetItemsRequest> {
    val request =
        GetItemsRequest(
            parentId = albumId,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            fields = DefaultItemFields,
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
            fields = DefaultItemFields,
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

suspend fun ViewModel.getPagerForPlaylist(
    api: ApiClient,
    playlistId: UUID,
): ApiRequestPager<GetItemsRequest> {
    val request =
        GetItemsRequest(
            parentId = playlistId,
            recursive = true,
            includeItemTypes = listOf(BaseItemKind.AUDIO),
            fields = DefaultItemFields,
            sortBy =
                listOf(
                    ItemSortBy.DEFAULT,
                ),
        )
    return ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
}
