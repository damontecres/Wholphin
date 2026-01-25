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
            Timber.d("Start")
            val serverId = inputData.getString(PARAM_SERVER_ID)?.toUUIDOrNull() ?: return Result.failure()
            val userId = inputData.getString(PARAM_USER_ID)?.toUUIDOrNull() ?: return Result.failure()

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

                val views =
                    api.userViewsApi
                        .getUserViews(userId = userId)
                        .content.items
                        .orEmpty()
                if (views.isEmpty()) {
                    return Result.success()
                }
                val results =
                    supervisorScope {
                        views
                            .mapNotNull { view ->
                                val itemKind =
                                    when (view.collectionType) {
                                        CollectionType.MOVIES -> BaseItemKind.MOVIE
                                        CollectionType.TVSHOWS -> BaseItemKind.SERIES
                                        else -> return@mapNotNull null
                                    }
                                async(Dispatchers.IO) {
                                    ensureActive()
                                    runCatching {
                                        val suggestions = fetchSuggestions(view.id, userId, itemKind, itemsPerRow)
                                        cache.put(userId, view.id, itemKind, suggestions.map { it.id.toString() })
                                    }.onFailure { e ->
                                        Timber.e(e, "Failed to fetch suggestions for view ${view.id}")
                                    }
                                }
                            }.awaitAll()
                    }
                val successCount = results.count { it.isSuccess }
                val failureCount = results.count { it.isFailure }
                cache.save()
                if (failureCount > 0 && successCount == 0) {
                    Timber.w("All attempts failed ($failureCount views), scheduling retry")
                    return Result.retry()
                }
                Timber.d("Completed with $successCount successes and $failureCount failures")
                return Result.success()
            } catch (_: ApiClientException) {
                return Result.retry()
            } catch (e: Exception) {
                Timber.e(e, "SuggestionsWorker failed")
                return Result.failure()
            }
        }

        private suspend fun fetchSuggestions(
            parentId: UUID,
            userId: UUID,
            itemKind: BaseItemKind,
            itemsPerRow: Int,
        ): List<BaseItemDto> =
            coroutineScope {
                val isSeries = itemKind == BaseItemKind.SERIES
                val historyItemType = if (isSeries) BaseItemKind.EPISODE else itemKind

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

                val seedItems = historyDeferred.await()
                val random = randomDeferred.await()
                val fresh = freshDeferred.await()

                val excludeIds = seedItems.mapTo(HashSet()) { it.relevantId }

                (fresh + random)
                    .asSequence()
                    .distinctBy { it.id }
                    .filterNot { excludeIds.contains(it.relevantId) }
                    .toList()
                    .shuffled()
                    .take(itemsPerRow)
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

        companion object {
            const val WORK_NAME = "com.github.damontecres.wholphin.services.SuggestionsWorker"
            const val PARAM_USER_ID = "userId"
            const val PARAM_SERVER_ID = "serverId"
            private const val FRESH_CONTENT_RATIO = 0.4
        }
    }
