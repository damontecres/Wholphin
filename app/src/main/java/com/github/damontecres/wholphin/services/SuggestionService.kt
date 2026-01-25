package com.github.damontecres.wholphin.services

import androidx.lifecycle.asFlow
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

sealed class SuggestionsResource {
    data object Loading : SuggestionsResource()

    data class Success(
        val items: List<BaseItem>,
    ) : SuggestionsResource()

    data object Empty : SuggestionsResource()
}

@Singleton
class SuggestionService
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val cache: SuggestionsCache,
        private val workManager: WorkManager,
    ) {
        fun getSuggestionsFlow(
            parentId: UUID,
            itemKind: BaseItemKind,
        ): Flow<SuggestionsResource> {
            return serverRepository.currentUser
                .asFlow()
                .flatMapLatest { user ->
                    val userId = user?.id ?: return@flatMapLatest flowOf(SuggestionsResource.Empty)

                    cache.cacheVersion
                        .map { cache.get(userId, parentId, itemKind)?.ids.orEmpty() }
                        .distinctUntilChanged()
                        .flatMapLatest { cachedIds ->
                            if (cachedIds.isNotEmpty()) {
                                flow {
                                    try {
                                        emit(SuggestionsResource.Success(fetchItemsByIds(cachedIds, itemKind)))
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to fetch items")
                                        emit(SuggestionsResource.Empty)
                                    }
                                }
                            } else {
                                workManager
                                    .getWorkInfosForUniqueWorkFlow(SuggestionsWorker.WORK_NAME)
                                    .map { workInfos ->
                                        val isActive =
                                            workInfos.any {
                                                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                                            }
                                        if (isActive) SuggestionsResource.Loading else SuggestionsResource.Empty
                                    }
                            }
                        }
                }
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
    }
