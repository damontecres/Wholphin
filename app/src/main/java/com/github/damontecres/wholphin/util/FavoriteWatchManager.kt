package com.github.damontecres.wholphin.util

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import java.util.UUID

interface FavoriteWatchManager {
    suspend fun setWatched(
        itemId: UUID,
        played: Boolean,
    )

    suspend fun setFavorite(
        itemId: UUID,
        favorite: Boolean,
    )
}

class FavoriteWatchManagerImpl(
    private val api: ApiClient,
) : FavoriteWatchManager {
    override suspend fun setWatched(
        itemId: UUID,
        played: Boolean,
    ) {
        if (played) {
            api.playStateApi.markPlayedItem(itemId)
        } else {
            api.playStateApi.markUnplayedItem(itemId)
        }
    }

    override suspend fun setFavorite(
        itemId: UUID,
        favorite: Boolean,
    ) {
        if (favorite) {
            api.userLibraryApi.markFavoriteItem(itemId)
        } else {
            api.userLibraryApi.unmarkFavoriteItem(itemId)
        }
    }
}
