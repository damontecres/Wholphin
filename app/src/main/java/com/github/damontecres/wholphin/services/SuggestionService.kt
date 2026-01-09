package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val BaseItemDto.relevantId: UUID get() = seriesId ?: id

@Singleton
class SuggestionService
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val cache: SuggestionsCache,
    ) {
        @Volatile
        private var genreAffinityIds: List<UUID> = emptyList()

        fun getSuggestionsFlow(
            parentId: UUID,
            itemKind: BaseItemKind,
            itemsPerRow: Int,
        ): Flow<List<BaseItem>> =
            flow {
                val userId = serverRepository.currentUser.value?.id ?: return@flow

                // Emit from cache by fetching fresh metadata for cached IDs
                cache.get(userId, parentId, itemKind)?.ids?.let { cachedIds ->
                    if (cachedIds.isNotEmpty()) {
                        emit(fetchItemsByIds(cachedIds, itemKind))
                    }
                }

                // Fetch fresh data, cache IDs, and emit
                val fresh = fetchSuggestions(parentId, itemKind, itemsPerRow)
                cache.put(userId, parentId, itemKind, fresh.map { it.id.toString() })
                emit(fresh)
            }

        private suspend fun fetchSuggestions(
            parentId: UUID,
            itemKind: BaseItemKind,
            itemsPerRow: Int,
        ): List<BaseItem> =
            coroutineScope {
                val userId = serverRepository.currentUser.value?.id
                val isSeries = itemKind == BaseItemKind.SERIES
                val historyItemType = if (isSeries) BaseItemKind.EPISODE else itemKind
                // Use previous request's genre affinity to enable parallel fetching
                val cachedGenreIds = genreAffinityIds

                val historyDeferred =
                    async(Dispatchers.IO) {
                        fetchItems(
                            parentId = parentId,
                            userId = userId,
                            itemKind = historyItemType,
                            sortBy = ItemSortBy.DATE_PLAYED,
                            isPlayed = true,
                            limit = 10,
                            extraFields = listOf(ItemFields.GENRES),
                        ).distinctBy { it.relevantId }.take(3)
                    }

                val randomDeferred =
                    async(Dispatchers.IO) {
                        fetchItems(
                            parentId = parentId,
                            userId = userId,
                            itemKind = itemKind,
                            sortBy = ItemSortBy.RANDOM,
                            isPlayed = false,
                            limit = itemsPerRow,
                        )
                    }

                val freshDeferred =
                    async(Dispatchers.IO) {
                        fetchItems(
                            parentId = parentId,
                            userId = userId,
                            itemKind = itemKind,
                            sortBy = ItemSortBy.DATE_CREATED,
                            sortOrder = SortOrder.DESCENDING,
                            isPlayed = false,
                            limit = (itemsPerRow * FRESH_CONTENT_RATIO).toInt().coerceAtLeast(1),
                        )
                    }

                val contextualDeferred =
                    async(Dispatchers.IO) {
                        if (cachedGenreIds.isEmpty()) {
                            emptyList()
                        } else {
                            fetchItems(
                                parentId = parentId,
                                userId = userId,
                                itemKind = itemKind,
                                sortBy = ItemSortBy.RANDOM,
                                isPlayed = false,
                                limit = (itemsPerRow * CONTEXTUAL_CONTENT_RATIO).toInt().coerceAtLeast(1),
                                genreIds = cachedGenreIds,
                            )
                        }
                    }

                val seedItems = historyDeferred.await()
                val random = randomDeferred.await()
                val fresh = freshDeferred.await()
                val contextual = contextualDeferred.await()

                val excludeIds = seedItems.mapTo(HashSet()) { it.relevantId }

                // Update genre affinity for next request
                genreAffinityIds = calculateGenreAffinity(seedItems)

                (contextual + fresh + random)
                    .asSequence()
                    .distinctBy { it.id }
                    .filterNot { excludeIds.contains(it.relevantId) }
                    .toList()
                    .shuffled()
                    .take(itemsPerRow)
                    .map { BaseItem.from(it, api, isSeries) }
            }

        private suspend fun fetchItemsByIds(
            ids: List<String>,
            itemKind: BaseItemKind,
        ): List<BaseItem> {
            val isSeries = itemKind == BaseItemKind.SERIES
            val uuids = ids.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
            val request =
                GetItemsRequest(
                    ids = uuids,
                    fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.OVERVIEW),
                )
            return GetItemsRequestHandler
                .execute(api, request)
                .content.items
                .orEmpty()
                .map { BaseItem.from(it, api, isSeries) }
        }

        private suspend fun fetchItems(
            parentId: UUID,
            userId: UUID?,
            itemKind: BaseItemKind,
            sortBy: ItemSortBy,
            isPlayed: Boolean,
            limit: Int,
            sortOrder: SortOrder? = null,
            genreIds: List<UUID>? = null,
            extraFields: List<ItemFields> = emptyList(),
        ): List<BaseItemDto> {
            val request =
                GetItemsRequest(
                    parentId = parentId,
                    userId = userId,
                    fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.OVERVIEW) + extraFields,
                    includeItemTypes = listOf(itemKind),
                    genreIds = genreIds,
                    recursive = true,
                    isPlayed = isPlayed,
                    sortBy = listOf(sortBy),
                    sortOrder = sortOrder?.let { listOf(it) },
                    limit = limit,
                    enableTotalRecordCount = false,
                    imageTypeLimit = 1,
                )
            return GetItemsRequestHandler
                .execute(api, request)
                .content.items
                .orEmpty()
        }

        private fun calculateGenreAffinity(items: List<BaseItemDto>): List<UUID> =
            items
                .flatMap { it.genreItems.orEmpty().mapNotNull { g -> g.id } }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .map { it.key }

        companion object {
            private const val FRESH_CONTENT_RATIO = 0.4
            private const val CONTEXTUAL_CONTENT_RATIO = 0.5
        }
    }
