package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.UserItemDataDto
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles toggling media as favorited or watched
 */
@Singleton
class FavoriteWatchManager
    @Inject
    constructor(
        private val api: ApiClient,
        private val datePlayedService: DatePlayedService,
    ) {
        suspend fun setWatched(
            itemId: UUID,
            played: Boolean,
        ): UserItemDataDto {
            datePlayedService.invalidate(itemId)
            return if (played) {
                api.playStateApi.markPlayedItem(itemId).content
            } else {
                api.playStateApi.markUnplayedItem(itemId).content
            }
        }

        suspend fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ): UserItemDataDto =
            if (favorite) {
                api.userLibraryApi.markFavoriteItem(itemId).content
            } else {
                api.userLibraryApi.unmarkFavoriteItem(itemId).content
            }

        suspend fun removeContinueWatching(item: BaseItem) {
        }
    }
