package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import kotlin.coroutines.CoroutineContext

private val BaseItemDto.relevantId: UUID get() = seriesId ?: id

@HiltWorker
class SuggestionsWorker
    @AssistedInject
    constructor(
        @Assisted private val context: Context,
        @Assisted workerParams: WorkerParameters,
        private val serverRepository: ServerRepository,
        private val preferences: DataStore<AppPreferences>,
        private val api: ApiClient,
        private val cache: SuggestionsCache,
    ) : CoroutineWorker(context, workerParams) {
        override suspend fun doWork(): Result {
            val serverId = inputData.getString(PARAM_SERVER_ID)?.toUUIDOrNull() ?: return Result.failure()
            val userId = inputData.getString(PARAM_USER_ID)?.toUUIDOrNull() ?: return Result.failure()
            val requestedParentId = inputData.getString(PARAM_PARENT_ID)?.toUUIDOrNull()
            val requestedItemKind =
                inputData
                    .getString(PARAM_ITEM_KIND)
                    ?.let { runCatching { BaseItemKind.fromName(it) }.getOrNull() }
            val mode = if (requestedParentId == null && requestedItemKind == null) "periodic" else "on-demand"
            Timber.d("Starting SuggestionsWorker mode=%s", mode)
            if ((requestedParentId == null) != (requestedItemKind == null)) {
                Timber.w("Invalid on-demand suggestions input parent=%s kind=%s", requestedParentId, requestedItemKind)
                return Result.failure()
            }

            if (api.baseUrl.isNullOrBlank() || api.accessToken.isNullOrBlank()) {
                var currentUser = serverRepository.current.value
                if (currentUser == null) {
                    serverRepository.restoreSession(serverId, userId)
                    currentUser = serverRepository.current.value
                }
                if (currentUser == null) {
                    Timber.w("No user found during run")
                    return Result.failure()
                }
            }

            try {
                val prefs = preferences.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
                val itemsPerRow =
                    prefs.homePagePreferences.maxItemsPerRow
                        .takeIf { it > 0 }
                        ?: AppPreference.HomePageItems.defaultValue.toInt()

                val context = Dispatchers.IO.limitedParallelism(2, "fetchSuggestions")
                if (requestedParentId != null && requestedItemKind != null) {
                    Timber.d(
                        "Fetching on-demand suggestions for parent=%s kind=%s",
                        requestedParentId,
                        requestedItemKind,
                    )
                    fetchAndCacheSuggestions(
                        context,
                        requestedParentId,
                        userId,
                        requestedItemKind,
                        itemsPerRow,
                    )
                    Timber.d(
                        "Completed on-demand suggestions for parent=%s kind=%s",
                        requestedParentId,
                        requestedItemKind,
                    )
                    return Result.success()
                }

                val views =
                    api.userViewsApi
                        .getUserViews(userId = userId)
                        .content.items
                        .orEmpty()
                if (views.isEmpty()) {
                    Timber.d("No user views found for periodic suggestions refresh")
                    return Result.success()
                }
                val supportedViews =
                    views.mapNotNull { view ->
                        getTypeForCollection(view.collectionType)?.let {
                            SuggestionsView(view.id, it)
                        }
                    }
                val skippedCount = views.size - supportedViews.size
                Timber.d(
                    "Refreshing periodic suggestions for %d supported views; skipped=%d",
                    supportedViews.size,
                    skippedCount,
                )
                val results =
                    supervisorScope {
                        supportedViews
                            .map { view ->
                                async(Dispatchers.IO) {
                                    runCatching {
                                        Timber.v("Fetching suggestions for parent=%s kind=%s", view.id, view.itemKind)
                                        fetchAndCacheSuggestions(
                                            context,
                                            view.id,
                                            userId,
                                            view.itemKind,
                                            itemsPerRow,
                                        )
                                        ensureActive()
                                    }.onFailure { e ->
                                        Timber.e(
                                            e,
                                            "Failed to fetch suggestions for parent=%s kind=%s",
                                            view.id,
                                            view.itemKind,
                                        )
                                    }
                                }
                            }.awaitAll()
                    }
                val successCount = results.count { it.isSuccess }
                val failureCount = results.count { it.isFailure }
                if (failureCount > 0 && successCount == 0) {
                    Timber.w("All attempts failed ($failureCount views), scheduling retry")
                    return Result.retry()
                }
                Timber.d("Completed with $successCount successes and $failureCount failures")
                return Result.success()
            } catch (ex: ApiClientException) {
                Timber.w(ex, "SuggestionsWorker ApiClientException, mode=%s", mode)
                if (mode == "on-demand") {
                    return Result.failure()
                }
                return Result.retry()
            } catch (e: Exception) {
                Timber.e(e, "SuggestionsWorker failed")
                return Result.failure()
            }
        }

        private suspend fun fetchAndCacheSuggestions(
            coroutineContext: CoroutineContext,
            parentId: UUID,
            userId: UUID,
            itemKind: BaseItemKind,
            itemsPerRow: Int,
        ) {
            val suggestions =
                fetchSuggestions(
                    coroutineContext,
                    parentId,
                    userId,
                    itemKind,
                    itemsPerRow,
                )
            val newIds = suggestions.map { it.id }
            val cachedIds = cache.get(userId, parentId, itemKind)?.ids
            if (cachedIds == newIds) {
                Timber.v(
                    "Suggestions unchanged for parent=%s kind=%s count=%d; skipping cache write",
                    parentId,
                    itemKind,
                    newIds.size,
                )
                return
            }
            cache.put(
                userId,
                parentId,
                itemKind,
                newIds,
            )
            if (newIds.isEmpty()) {
                Timber.d("Cached empty suggestions for parent=%s kind=%s", parentId, itemKind)
            } else {
                Timber.d(
                    "Cached %d suggestions for parent=%s kind=%s",
                    newIds.size,
                    parentId,
                    itemKind,
                )
            }
        }

        private suspend fun fetchSuggestions(
            coroutineContext: CoroutineContext,
            parentId: UUID,
            userId: UUID,
            itemKind: BaseItemKind,
            itemsPerRow: Int,
        ): List<BaseItemDto> =
            coroutineScope {
                val isSeries = itemKind == BaseItemKind.SERIES
                val historyItemType = if (isSeries) BaseItemKind.EPISODE else itemKind

                val contextualLimit = (itemsPerRow * 0.4).toInt().coerceAtLeast(1)
                val randomLimit = (itemsPerRow * 0.3).toInt().coerceAtLeast(1)
                val freshLimit = (itemsPerRow * 0.3).toInt().coerceAtLeast(1)

                val historyDeferred =
                    async(coroutineContext) {
                        fetchItems(
                            parentId = parentId,
                            userId = userId,
                            itemKind = historyItemType,
                            sortBy = ItemSortBy.DATE_PLAYED,
                            isPlayed = true,
                            limit = 20,
                            extraFields = listOf(ItemFields.GENRES),
                        ).distinctBy { it.relevantId }.take(3)
                    }

                val seedItems = historyDeferred.await()

                val allGenreIds =
                    seedItems
                        .flatMap { it.genreItems?.mapNotNull { g -> g.id } ?: emptyList() }
                        .distinct()

                val excludeIds = seedItems.mapTo(HashSet()) { it.relevantId }

                val contextualDeferred =
                    async(coroutineContext) {
                        if (allGenreIds.isEmpty()) {
                            emptyList()
                        } else {
                            fetchItems(
                                parentId = parentId,
                                userId = userId,
                                itemKind = itemKind,
                                sortBy = ItemSortBy.RANDOM,
                                isPlayed = false,
                                limit = contextualLimit,
                                genreIds = allGenreIds,
                                excludeItemIds = excludeIds.toList(),
                            )
                        }
                    }

                val randomDeferred =
                    async(coroutineContext) {
                        fetchItems(
                            parentId = parentId,
                            userId = userId,
                            itemKind = itemKind,
                            sortBy = ItemSortBy.RANDOM,
                            isPlayed = false,
                            limit = randomLimit,
                        )
                    }

                val freshDeferred =
                    async(coroutineContext) {
                        fetchItems(
                            parentId = parentId,
                            userId = userId,
                            itemKind = itemKind,
                            sortBy = ItemSortBy.DATE_CREATED,
                            sortOrder = SortOrder.DESCENDING,
                            isPlayed = false,
                            limit = freshLimit,
                        )
                    }
                withContext(Dispatchers.Default) {
                    val contextual = contextualDeferred.await()
                    val random = randomDeferred.await()
                    val fresh = freshDeferred.await()

                    (contextual + fresh + random)
                        .asSequence()
                        .distinctBy { it.id }
                        .filterNot { excludeIds.contains(it.relevantId) }
                        .toList()
                        .shuffled()
                        .take(itemsPerRow)
                }
            }

        private suspend fun fetchItems(
            parentId: UUID,
            userId: UUID,
            itemKind: BaseItemKind,
            sortBy: ItemSortBy,
            isPlayed: Boolean,
            limit: Int,
            sortOrder: SortOrder? = null,
            genreIds: List<UUID>? = null,
            excludeItemIds: List<UUID>? = null,
            extraFields: List<ItemFields> = emptyList(),
        ): List<BaseItemDto> {
            val request =
                GetItemsRequest(
                    parentId = parentId,
                    userId = userId,
                    fields = extraFields,
                    includeItemTypes = listOf(itemKind),
                    genreIds = genreIds,
                    recursive = true,
                    isPlayed = isPlayed,
                    excludeItemIds = excludeItemIds,
                    sortBy = listOf(sortBy),
                    sortOrder = sortOrder?.let { listOf(it) },
                    limit = limit,
                    enableTotalRecordCount = false,
                    imageTypeLimit = 0,
                )
            return GetItemsRequestHandler
                .execute(api, request)
                .content.items
                .orEmpty()
        }

        companion object {
            const val WORK_NAME = "com.github.damontecres.wholphin.services.SuggestionsWorker"
            const val PARAM_USER_ID = "userId"
            const val PARAM_SERVER_ID = "serverId"
            const val PARAM_PARENT_ID = "parentId"
            const val PARAM_ITEM_KIND = "itemKind"

            fun getOnDemandWorkName(
                userId: UUID,
                parentId: UUID,
                itemKind: BaseItemKind,
            ): String = "$WORK_NAME.onDemand.$userId.$parentId.${itemKind.serialName}"

            fun getOnDemandWorkTag(
                userId: UUID,
                parentId: UUID,
                itemKind: BaseItemKind,
            ): String = "suggestions:$userId:$parentId:${itemKind.serialName}"

            fun getTypeForCollection(collectionType: CollectionType?): BaseItemKind? =
                when (collectionType) {
                    CollectionType.MOVIES -> BaseItemKind.MOVIE
                    CollectionType.TVSHOWS -> BaseItemKind.SERIES
                    else -> null
                }
        }

        private data class SuggestionsView(
            val id: UUID,
            val itemKind: BaseItemKind,
        )
    }
