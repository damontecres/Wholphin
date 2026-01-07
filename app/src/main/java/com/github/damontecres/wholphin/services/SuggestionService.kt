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
        private val genreAffinityCache = AtomicReference<List<UUID>>(emptyList())

        fun getSuggestionsFlow(
            parentId: UUID,
            itemKind: BaseItemKind,
            itemsPerRow: Int,
        ): Flow<List<BaseItem>> = flow {
            val cached = cache.get(parentId, itemKind)
            val cachedIds = cached?.items?.map { it.id }?.toSet()
            cached?.let { emit(it.items) }

            try {
                val fresh = fetchSuggestions(parentId, itemKind, itemsPerRow)
                cache.put(parentId, itemKind, fresh)
                if (fresh.map { it.id }.toSet() != cachedIds) {
                    emit(fresh)
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Failed to fetch suggestions")
                if (cached == null) throw ex
            }
        }

        suspend fun getSuggestions(
            parentId: UUID,
            itemKind: BaseItemKind,
            itemsPerRow: Int,
        ): List<BaseItem> =
            cache.get(parentId, itemKind)?.items
                ?: fetchSuggestions(parentId, itemKind, itemsPerRow).also {
                    cache.put(parentId, itemKind, it)
                }

        private suspend fun fetchSuggestions(
            parentId: UUID,
            itemKind: BaseItemKind,
            itemsPerRow: Int,
        ): List<BaseItem> = coroutineScope {
            val userId = serverRepository.currentUser.value?.id
            val isSeries = itemKind == BaseItemKind.SERIES
            val historyItemType = if (isSeries) BaseItemKind.EPISODE else itemKind
            val cachedGenreIds = genreAffinityCache.get()

            val historyDeferred = async(Dispatchers.IO) {
                fetchItems(
                    parentId = parentId,
                    userId = userId,
                    itemKind = historyItemType,
                    sortBy = ItemSortBy.DATE_PLAYED,
                    isPlayed = true,
                    limit = 20,
                    extraFields = listOf(ItemFields.GENRES),
                ).distinctBy { it.seriesId ?: it.id }.take(3)
            }

            val randomDeferred = async(Dispatchers.IO) {
                fetchItems(
                    parentId = parentId,
                    userId = userId,
                    itemKind = itemKind,
                    sortBy = ItemSortBy.RANDOM,
                    isPlayed = false,
                    limit = itemsPerRow,
                )
            }

            val freshDeferred = async(Dispatchers.IO) {
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

            val contextualDeferred = async(Dispatchers.IO) {
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

            val excludeIds = seedItems.mapNotNullTo(HashSet()) { it.seriesId ?: it.id }

            genreAffinityCache.set(
                seedItems
                    .flatMap { it.genreItems?.mapNotNull { g -> g.id }.orEmpty() }
                    .groupingBy { it }
                    .eachCount()
                    .entries
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { it.key },
            )

            (contextual + fresh + random)
                .distinctBy { it.id }
                .filterNot { excludeIds.contains(it.seriesId ?: it.id) }
                .shuffled()
                .take(itemsPerRow)
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
            val request = GetItemsRequest(
                parentId = parentId,
                userId = userId,
                fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO) + extraFields,
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
            return GetItemsRequestHandler.execute(api, request).content.items.orEmpty()
        }

        companion object {
            private const val FRESH_CONTENT_RATIO = 0.4
            private const val CONTEXTUAL_CONTENT_RATIO = 0.5
        }
    }
