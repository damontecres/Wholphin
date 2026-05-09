package com.github.damontecres.wholphin.services

import androidx.lifecycle.asFlow
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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

sealed interface SuggestionsResource {
    data object Loading : SuggestionsResource

    data class Success(
        val items: List<BaseItem>,
    ) : SuggestionsResource

    data object Empty : SuggestionsResource
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
        @OptIn(ExperimentalCoroutinesApi::class)
        fun getSuggestionsFlow(
            parentId: UUID,
            itemKind: BaseItemKind,
        ): Flow<SuggestionsResource> {
            return serverRepository.currentUser
                .asFlow()
                .flatMapLatest { user ->
                    val userId = user?.id ?: return@flatMapLatest flowOf(SuggestionsResource.Empty)
                    val cachedSuggestions = cache.get(userId, parentId, itemKind)
                    if (cachedSuggestions == null) {
                        val workName = SuggestionsWorker.getOnDemandWorkName(userId, parentId, itemKind)
                        enqueueSuggestionsWork(user, parentId, itemKind, workName)
                        workManager
                            .getWorkInfosForUniqueWorkFlow(workName)
                            .map { workInfos ->
                                cache.get(userId, parentId, itemKind)?.let {
                                    return@map it.toResource(itemKind)
                                }
                                val isActive =
                                    workInfos.any {
                                        it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                                    }
                                if (isActive) SuggestionsResource.Loading else SuggestionsResource.Empty
                            }
                    } else {
                        flow {
                            emit(cachedSuggestions.toResource(itemKind))
                        }
                    }
                }
        }

        private fun enqueueSuggestionsWork(
            user: JellyfinUser,
            parentId: UUID,
            itemKind: BaseItemKind,
            workName: String,
        ) {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            val request =
                OneTimeWorkRequestBuilder<SuggestionsWorker>()
                    .setConstraints(constraints)
                    .setInputData(
                        workDataOf(
                            SuggestionsWorker.PARAM_USER_ID to user.id.toString(),
                            SuggestionsWorker.PARAM_SERVER_ID to user.serverId.toString(),
                            SuggestionsWorker.PARAM_PARENT_ID to parentId.toString(),
                            SuggestionsWorker.PARAM_ITEM_KIND to itemKind.serialName,
                        ),
                    ).addTag(SuggestionsWorker.getOnDemandWorkTag(user.id, parentId, itemKind))
                    .build()

            workManager.enqueueUniqueWork(
                workName,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        private suspend fun CachedSuggestions.toResource(itemKind: BaseItemKind): SuggestionsResource {
            if (ids.isEmpty()) return SuggestionsResource.Empty
            return try {
                SuggestionsResource.Success(fetchItemsByIds(ids, itemKind))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch items")
                SuggestionsResource.Empty
            }
        }

        private suspend fun fetchItemsByIds(
            ids: List<UUID>,
            itemKind: BaseItemKind,
        ): List<BaseItem> {
            val isSeries = itemKind == BaseItemKind.SERIES
            val request =
                GetItemsRequest(
                    ids = ids,
                    fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.OVERVIEW),
                )
            return GetItemsRequestHandler
                .execute(api, request)
                .content.items
                .map { BaseItem.from(it, api, isSeries) }
        }
    }
