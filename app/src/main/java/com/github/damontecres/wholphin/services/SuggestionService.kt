package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for generating content suggestions based on user watch history.
 *
 * Uses a weighted mix of three sources:
 * - Contextual (40%): Items matching genres from recently watched content
 * - Random (30%): Random unwatched items for discovery
 * - Fresh (30%): Recently added unwatched items
 */
@Singleton
class SuggestionService
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) {
        /**
         * Get suggestions for a library.
         *
         * @param parentId The library ID to scope suggestions to
         * @param itemKind The type of items to suggest (MOVIE or SERIES)
         * @param itemsPerRow The total number of suggestions to return
         * @return List of suggested items, shuffled and deduplicated
         */
        suspend fun getSuggestions(
            parentId: UUID,
            itemKind: BaseItemKind,
            itemsPerRow: Int,
        ): List<BaseItem> =
            coroutineScope {
                val userId = serverRepository.currentUser.value?.id
                val contextualLimit = (itemsPerRow * 0.4).toInt().coerceAtLeast(1)
                val randomLimit = (itemsPerRow * 0.3).toInt().coerceAtLeast(1)
                val freshLimit = (itemsPerRow * 0.3).toInt().coerceAtLeast(1)

                // For TV shows, fetch episode history; for movies, fetch movie history
                val historyItemType =
                    if (itemKind == BaseItemKind.SERIES) BaseItemKind.EPISODE else itemKind

                // Source 1: Contextual - fetch recent history and deduplicate
                val historyRequest =
                    GetItemsRequest(
                        parentId = parentId,
                        userId = userId,
                        fields = SlimItemFields + listOf(ItemFields.GENRES),
                        includeItemTypes = listOf(historyItemType),
                        recursive = true,
                        isPlayed = true,
                        sortBy = listOf(ItemSortBy.DATE_PLAYED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        limit = 20,
                        enableTotalRecordCount = false,
                    )
                val historyItems =
                    GetItemsRequestHandler
                        .execute(api, historyRequest)
                        .content
                        .items
                        .orEmpty()

                // Deduplicate by seriesId (for episodes) or id (for movies)
                val seedItems =
                    historyItems
                        .distinctBy { it.seriesId ?: it.id }
                        .take(3)

                // Collect all genre IDs from seed items
                val allGenreIds =
                    seedItems
                        .flatMap { it.genreItems?.mapNotNull { g -> g.id } ?: emptyList() }
                        .distinct()

                // Exclude seed items from recommendations (use seriesId for episodes, id for movies)
                val excludeIds = seedItems.mapNotNull { it.seriesId ?: it.id }

                // Run all queries in parallel using async
                val contextualDeferred =
                    async(Dispatchers.IO) {
                        if (allGenreIds.isEmpty()) return@async emptyList()

                        val contextualRequest =
                            GetItemsRequest(
                                parentId = parentId,
                                userId = userId,
                                fields = SlimItemFields,
                                includeItemTypes = listOf(itemKind),
                                genreIds = allGenreIds,
                                recursive = true,
                                isPlayed = false,
                                excludeItemIds = excludeIds,
                                sortBy = listOf(ItemSortBy.RANDOM),
                                limit = contextualLimit,
                                enableTotalRecordCount = false,
                            )
                        GetItemsRequestHandler
                            .execute(api, contextualRequest)
                            .content
                            .items
                            .orEmpty()
                    }

                val randomDeferred =
                    async(Dispatchers.IO) {
                        val randomRequest =
                            GetItemsRequest(
                                parentId = parentId,
                                userId = userId,
                                fields = SlimItemFields,
                                includeItemTypes = listOf(itemKind),
                                recursive = true,
                                isPlayed = false,
                                sortBy = listOf(ItemSortBy.RANDOM),
                                limit = randomLimit,
                                enableTotalRecordCount = false,
                            )
                        GetItemsRequestHandler
                            .execute(api, randomRequest)
                            .content
                            .items
                            .orEmpty()
                    }

                val freshDeferred =
                    async(Dispatchers.IO) {
                        val freshRequest =
                            GetItemsRequest(
                                parentId = parentId,
                                userId = userId,
                                fields = SlimItemFields,
                                includeItemTypes = listOf(itemKind),
                                recursive = true,
                                isPlayed = false,
                                sortBy = listOf(ItemSortBy.DATE_CREATED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                limit = freshLimit,
                                enableTotalRecordCount = false,
                            )
                        GetItemsRequestHandler
                            .execute(api, freshRequest)
                            .content
                            .items
                            .orEmpty()
                    }

                // Await all and combine
                val contextual = contextualDeferred.await()
                val random = randomDeferred.await()
                val fresh = freshDeferred.await()

                val isSeries = itemKind == BaseItemKind.SERIES

                (contextual + random + fresh)
                    .distinctBy { it.id }
                    .shuffled()
                    .take(itemsPerRow)
                    .map { BaseItem.from(it, api, isSeries) }
            }
    }
