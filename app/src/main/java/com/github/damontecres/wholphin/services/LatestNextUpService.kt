@file:UseSerializers(
    UUIDSerializer::class,
    LocalDateTimeSerializer::class,
)

package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.util.LocalDateTimeSerializer
import com.github.damontecres.wholphin.util.supportItemKinds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds

/**
 * Get continue watching and next up items for users
 */
@Singleton
class LatestNextUpService
    @Inject
    constructor(
        private val api: ApiClient,
        private val datePlayedService: DatePlayedService,
        private val displayPreferencesService: DisplayPreferencesService,
        private val favoriteWatchManager: FavoriteWatchManager,
    ) {
        /**
         * Get resume (continue watching) items for a user
         */
        suspend fun getResume(
            userId: UUID,
            limit: Int,
            includeEpisodes: Boolean,
            useSeriesForPrimary: Boolean = true,
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
                    .map { BaseItem.from(it, api, useSeriesForPrimary) }
            return items
        }

        /**
         * Get next up items for a user
         */
        suspend fun getNextUp(
            userId: UUID,
            limit: Int,
            enableRewatching: Boolean,
            enableResumable: Boolean,
            maxDays: Int,
            useSeriesForPrimary: Boolean = true,
        ): List<BaseItem> {
            val removedSeries = getRemovedFromNextUp(userId)
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
                    .map { BaseItem.from(it, api, useSeriesForPrimary) }
                    .filter {
                        val seriesId = it.data.seriesId
                        if (seriesId != null && seriesId in removedSeries) {
                            // User has previously removed the series
                            val lastPlayedDate = it.data.userData?.lastPlayedDate
                            if (lastPlayedDate != null) {
                                // If item played it after it was removed, should include it
                                lastPlayedDate > removedSeries[seriesId]
                            } else {
                                // If unknown last played, filter out
                                false
                            }
                        } else {
                            true
                        }
                    }

            return nextUp
        }

        /**
         * Create the combined Continue Watching & Next Up items
         *
         * @see [DatePlayedService]
         */
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

        /**
         * Remove a series from next up
         */
        suspend fun removeFromNextUp(
            userId: UUID,
            episode: BaseItem,
        ) {
            favoriteWatchManager.setWatched(episode.id, false)
            episode.data.seriesId?.let { seriesId ->
                displayPreferencesService.updateDisplayPreferences(userId) {
                    val removedIds =
                        get(REMOVED_KEY)
                            ?.let {
                                Json.decodeFromString<RemovedSeriesIds>(it).value
                            }.orEmpty()
                            .toMutableMap()
                    removedIds[seriesId] = LocalDateTime.now()
                    put(
                        REMOVED_KEY,
                        Json.encodeToString(RemovedSeriesIds(removedIds)),
                    )
                }
            }
        }

        /**
         * Get when series were removed from next up
         */
        suspend fun getRemovedFromNextUp(userId: UUID): Map<UUID, LocalDateTime> =
            displayPreferencesService
                .getDisplayPreferences(userId)
                .customPrefs[REMOVED_KEY]
                ?.let {
                    Json.decodeFromString<RemovedSeriesIds>(it).value
                }.orEmpty()

        /**
         * Check if user has watched a series since removing it
         */
        suspend fun updateRemovedFromNextUp(userId: UUID) {
            val removed = getRemovedFromNextUp(userId)
            val newRemoved = removed.toMutableMap()
            var changed = false
            removed.forEach { (seriesId, timestamp) ->
                val item =
                    api.itemsApi
                        .getItems(
                            userId = userId,
                            parentId = seriesId,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            sortBy = listOf(ItemSortBy.DATE_PLAYED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = 1,
                        ).content.items
                        .firstOrNull()
                if (item != null) {
                    val lastPlayed = item.userData?.lastPlayedDate
                    if (lastPlayed != null && lastPlayed > timestamp) {
                        Timber.v("Updating removed next up for series %s", seriesId)
                        newRemoved.remove(seriesId)
                        changed = true
                    }
                } else {
                    // Series doesn't exist anymore
                    Timber.v("Updating removed next up for missing series %s", seriesId)
                    newRemoved.remove(seriesId)
                    changed = true
                }
            }
            if (changed) {
                displayPreferencesService.updateDisplayPreferences(userId) {
                    put(
                        REMOVED_KEY,
                        Json.encodeToString(RemovedSeriesIds(newRemoved)),
                    )
                }
            }
        }

        companion object {
            const val REMOVED_KEY = "removeNextUp"
        }
    }

@Serializable
data class RemovedSeriesIds(
    val value: Map<UUID, LocalDateTime>,
)
