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
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SuggestionService
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val cache: SuggestionsCache,
    ) {
        // Cache preferred genres for parallel contextual requests
        private val genreAffinityCache = AtomicReference<List<UUID>>(emptyList())

        fun getSuggestionsFlow(
            parentId: UUID,
            itemKind: BaseItemKind,
            itemsPerRow: Int,
        ): Flow<List<BaseItem>> =
            flow {
                val cached = cache.get(parentId, itemKind)
                val cachedIds = cached?.items?.map { it.id }?.toSet()
                if (cached != null) {
                    emit(cached.items)
                }

                try {
                    val fresh = fetchSuggestions(parentId, itemKind, itemsPerRow)
                    val freshIds = fresh.map { it.id }.toSet()

                    // Only emit if content changed
                    if (cachedIds != freshIds) {
                        cache.put(parentId, itemKind, fresh)
                        emit(fresh)
                    } else {
                        cache.put(parentId, itemKind, fresh)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Failed to fetch suggestions")
                    if (cached == null) throw ex
                }
            }

        /**
         * Gets suggestions with cache-first strategy.
         * Returns cached data if available, otherwise fetches fresh data.
         * For stale-while-revalidate behavior, use getSuggestionsFlow() instead.
         */
        suspend fun getSuggestions(
            parentId: UUID,
            itemKind: BaseItemKind,
            itemsPerRow: Int,
        ): List<BaseItem> {
            val cached = cache.get(parentId, itemKind)
            if (cached != null) {
                return cached.items
            }

            return fetchSuggestions(parentId, itemKind, itemsPerRow).also {
                cache.put(parentId, itemKind, it)
            }
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
                val cachedGenreIds = genreAffinityCache.get()

                val historyDeferred =
                    async(Dispatchers.IO) {
                        val historyRequest =
                            GetItemsRequest(
                                parentId = parentId,
                                userId = userId,
                                fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.GENRES),
                                includeItemTypes = listOf(historyItemType),
                                recursive = true,
                                isPlayed = true,
                                sortBy = listOf(ItemSortBy.DATE_PLAYED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                limit = 20,
                                enableTotalRecordCount = false,
                                imageTypeLimit = 1,
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
                                fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                                includeItemTypes = listOf(itemKind),
                                recursive = true,
                                isPlayed = false,
                                sortBy = listOf(ItemSortBy.RANDOM),
                                limit = itemsPerRow,
                                enableTotalRecordCount = false,
                                imageTypeLimit = 1,
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
                                fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                                includeItemTypes = listOf(itemKind),
                                recursive = true,
                                isPlayed = false,
                                sortBy = listOf(ItemSortBy.DATE_CREATED),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                limit = (itemsPerRow * 0.4).toInt().coerceAtLeast(1),
                                enableTotalRecordCount = false,
                                imageTypeLimit = 1,
                            )
                        GetItemsRequestHandler
                            .execute(api, freshRequest)
                            .content
                            .items
                            .orEmpty()
                    }

                val contextualDeferred =
                    async(Dispatchers.IO) {
                        if (cachedGenreIds.isEmpty()) {
                            emptyList()
                        } else {
                            val contextualRequest =
                                GetItemsRequest(
                                    parentId = parentId,
                                    userId = userId,
                                    fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                                    includeItemTypes = listOf(itemKind),
                                    genreIds = cachedGenreIds,
                                    recursive = true,
                                    isPlayed = false,
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    limit = (itemsPerRow * 0.5).toInt().coerceAtLeast(1),
                                    enableTotalRecordCount = false,
                                    imageTypeLimit = 1,
                                )
                            GetItemsRequestHandler
                                .execute(api, contextualRequest)
                                .content
                                .items
                                .orEmpty()
                        }
                    }

                val seedItems = historyDeferred.await()
                val random = randomDeferred.await()
                val fresh = freshDeferred.await()
                val contextual = contextualDeferred.await()

                val excludeIds = seedItems.mapNotNullTo(HashSet<UUID>()) { it.seriesId ?: it.id }

                val freshGenreIds =
                    seedItems
                        .flatMap { it.genreItems?.mapNotNull { g -> g.id } ?: emptyList() }
                        .groupingBy { it }
                        .eachCount()
                        .entries
                        .sortedByDescending { it.value }
                        .take(3)
                        .map { it.key }
                genreAffinityCache.set(freshGenreIds)

                (contextual + fresh + random)
                    .distinctBy { it.id }
                    .filterNot { excludeIds.contains(it.seriesId ?: it.id) }
                    .shuffled()
                    .take(itemsPerRow)
                    .map { BaseItem.from(it, api, isSeries) }
            }
    }
