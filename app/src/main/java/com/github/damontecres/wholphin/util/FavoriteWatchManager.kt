package com.github.damontecres.wholphin.util

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.util.UUID

interface FavoriteWatchManager {
    suspend fun setWatched(
        itemId: UUID,
        played: Boolean,
    ): UserItemDataDto

    suspend fun setFavorite(
        itemId: UUID,
        favorite: Boolean,
    ): UserItemDataDto
}

class FavoriteWatchManagerImpl(
    private val api: ApiClient,
) : FavoriteWatchManager {
    override suspend fun setWatched(
        itemId: UUID,
        played: Boolean,
    ): UserItemDataDto =
        if (played) {
            api.playStateApi.markPlayedItem(itemId).content
        } else {
            api.playStateApi.markUnplayedItem(itemId).content
        }

    override suspend fun setFavorite(
        itemId: UUID,
        favorite: Boolean,
    ): UserItemDataDto =
        if (favorite) {
            api.userLibraryApi.markFavoriteItem(itemId).content
        } else {
            api.userLibraryApi.unmarkFavoriteItem(itemId).content
        }
}
