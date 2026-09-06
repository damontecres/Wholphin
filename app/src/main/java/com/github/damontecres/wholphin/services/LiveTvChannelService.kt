package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetLiveTvChannelsRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches the live TV channel list.
 *
 * Both the TV guide and playback need the channel list in the same order, so that channel
 * up/down during playback moves through channels the way the guide displays them.
 */
@Singleton
class LiveTvChannelService
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) {
        /**
         * @param sortByRecentlyWatched order by the most recently watched channel first
         * @param favoriteChannelsAtBeginning list favorite channels before the rest
         */
        suspend fun getChannels(
            sortByRecentlyWatched: Boolean,
            favoriteChannelsAtBeginning: Boolean,
        ): List<BaseItemDto> =
            api.liveTvApi
                .getLiveTvChannels(
                    GetLiveTvChannelsRequest(
                        startIndex = 0,
                        userId = serverRepository.currentUser?.id,
                        enableFavoriteSorting = favoriteChannelsAtBeginning,
                        sortBy = if (sortByRecentlyWatched) listOf(ItemSortBy.DATE_PLAYED) else null,
                        sortOrder = if (sortByRecentlyWatched) SortOrder.DESCENDING else null,
                        addCurrentProgram = false,
                    ),
                ).content.items
    }
