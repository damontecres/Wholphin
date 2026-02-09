package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.main.LatestData
import com.github.damontecres.wholphin.ui.main.supportedLatestCollectionTypes
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.supportItemKinds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.userViewsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class LatestNextUpService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val datePlayedService: DatePlayedService,
    ) {
        suspend fun getResume(
            userId: UUID,
            limit: Int,
            includeEpisodes: Boolean,
        ): List<BaseItem> {
            val request =
                GetResumeItemsRequest(
                    userId = userId,
                    fields = SlimItemFields,
                    limit = limit,
                    includeItemTypes =
                        if (includeEpisodes) {
                            supportItemKinds
                        } else {
                            supportItemKinds
                                .toMutableSet()
                                .apply {
                                    remove(BaseItemKind.EPISODE)
                                }
                        },
                )
            val items =
                api.itemsApi
                    .getResumeItems(request)
                    .content
                    .items
                    .map { BaseItem.from(it, api, true) }
            return items
        }

        suspend fun getNextUp(
            userId: UUID,
            limit: Int,
            enableRewatching: Boolean,
            enableResumable: Boolean,
            maxDays: Int,
        ): List<BaseItem> {
            val nextUpDateCutoff =
                maxDays.takeIf { it > 0 }?.let { LocalDateTime.now().minusDays(it.toLong()) }
            val request =
                GetNextUpRequest(
                    userId = userId,
                    fields = SlimItemFields,
                    imageTypeLimit = 1,
                    parentId = null,
                    limit = limit,
                    enableResumable = enableResumable,
                    enableUserData = true,
                    enableRewatching = enableRewatching,
                    nextUpDateCutoff = nextUpDateCutoff,
                )
            val nextUp =
                api.tvShowsApi
                    .getNextUp(request)
                    .content
                    .items
                    .map { BaseItem.from(it, api, true) }
            return nextUp
        }

        suspend fun getLatest(
            user: UserDto,
            limit: Int,
            includedIds: List<UUID>,
        ): List<LatestData> {
            val excluded = user.configuration?.latestItemsExcludes.orEmpty()
            val views by api.userViewsApi.getUserViews()
            val latestData =
                views.items
                    .filter {
                        it.id in includedIds && it.id !in excluded &&
                            it.collectionType in supportedLatestCollectionTypes
                    }.map { view ->
                        val title =
                            view.name?.let { context.getString(R.string.recently_added_in, it) }
                                ?: context.getString(R.string.recently_added)
                        val request =
                            GetLatestMediaRequest(
                                fields = SlimItemFields,
                                imageTypeLimit = 1,
                                parentId = view.id,
                                groupItems = true,
                                limit = limit,
                                isPlayed = null, // Server will handle user's preference
                            )
                        LatestData(title, request)
                    }

            return latestData
        }

        suspend fun loadLatest(latestData: List<LatestData>): List<HomeRowLoadingState> {
            val rows =
                latestData.mapNotNull { (title, request) ->
                    try {
                        val latest =
                            api.userLibraryApi
                                .getLatestMedia(request)
                                .content
                                .map { BaseItem.from(it, api, true) }
                        if (latest.isNotEmpty()) {
                            HomeRowLoadingState.Success(
                                title = title,
                                items = latest,
                            )
                        } else {
                            null
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex, "Exception fetching %s", title)
                        HomeRowLoadingState.Error(
                            title = title,
                            exception = ex,
                        )
                    }
                }
            return rows
        }

        suspend fun buildCombined(
            resume: List<BaseItem>,
            nextUp: List<BaseItem>,
        ): List<BaseItem> =
            withContext(Dispatchers.IO) {
                val start = System.currentTimeMillis()
                val semaphore = Semaphore(3)
                val deferred =
                    nextUp
                        .filter { it.data.seriesId != null }
                        .map { item ->
                            async(Dispatchers.IO) {
                                try {
                                    semaphore.withPermit {
                                        datePlayedService.getLastPlayed(item)
                                    }
                                } catch (ex: Exception) {
                                    Timber.e(ex, "Error fetching %s", item.id)
                                    null
                                }
                            }
                        }

                val nextUpLastPlayed = deferred.awaitAll()
                val timestamps = mutableMapOf<UUID, LocalDateTime?>()
                nextUp.map { it.id }.zip(nextUpLastPlayed).toMap(timestamps)
                resume.forEach { timestamps[it.id] = it.data.userData?.lastPlayedDate }
                val result = (resume + nextUp).sortedByDescending { timestamps[it.id] }
                val duration = (System.currentTimeMillis() - start).milliseconds
                Timber.v("buildCombined took %s", duration)
                return@withContext result
            }
    }
