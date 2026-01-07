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

@Singleton
class SuggestionService
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) {
        suspend fun getSuggestions(
            parentId: UUID,
            itemKind: BaseItemKind,
            itemsPerRow: Int,
        ): List<BaseItem> =
            coroutineScope {
                val userId = serverRepository.currentUser.value?.id
                val isSeries = itemKind == BaseItemKind.SERIES
                val historyItemType = if (isSeries) BaseItemKind.EPISODE else itemKind

                val historyDeferred =
                    async(Dispatchers.IO) {
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
                        GetItemsRequestHandler
                            .execute(api, historyRequest)
                            .content
                            .items
                            .orEmpty()
                            .distinctBy { it.seriesId ?: it.id }
                            .take(3)
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
                                limit = itemsPerRow,
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
                                limit = (itemsPerRow * 0.4).toInt().coerceAtLeast(1),
                                enableTotalRecordCount = false,
                            )
                        GetItemsRequestHandler
                            .execute(api, freshRequest)
                            .content
                            .items
                            .orEmpty()
                    }

                val seedItems = historyDeferred.await()
                val random = randomDeferred.await()
                val fresh = freshDeferred.await()

                val excludeIds = seedItems.mapNotNull { it.seriesId ?: it.id }.toSet()
                val allGenreIds =
                    seedItems
                        .flatMap { it.genreItems?.mapNotNull { g -> g.id } ?: emptyList() }
                        .distinct()

                val contextual =
                    if (allGenreIds.isEmpty()) {
                        emptyList()
                    } else {
                        val contextualRequest =
                            GetItemsRequest(
                                parentId = parentId,
                                userId = userId,
                                fields = SlimItemFields,
                                includeItemTypes = listOf(itemKind),
                                genreIds = allGenreIds,
                                recursive = true,
                                isPlayed = false,
                                excludeItemIds = excludeIds.toList(),
                                sortBy = listOf(ItemSortBy.RANDOM),
                                limit = (itemsPerRow * 0.5).toInt().coerceAtLeast(1),
                                enableTotalRecordCount = false,
                            )
                        GetItemsRequestHandler
                            .execute(api, contextualRequest)
                            .content
                            .items
                            .orEmpty()
                    }

                (contextual + fresh + random)
                    .distinctBy { it.id }
                    .filterNot { excludeIds.contains(it.seriesId ?: it.id) }
                    .shuffled()
                    .take(itemsPerRow)
                    .map { BaseItem.from(it, api, isSeries) }
            }
    }
