package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.util.GetEpisodesRequestHandler
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatePlayedService
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        private val datePlayedCache =
            CacheBuilder
                .newBuilder()
                .maximumSize(AppPreference.HomePageItems.max)
                .build<SeriesItemId, LocalDateTime?>(
                    object :
                        CacheLoader<SeriesItemId, LocalDateTime>() {
                        override fun load(key: SeriesItemId): LocalDateTime = getLastPlayed(key)
                    },
                )

        private fun getLastPlayed(key: SeriesItemId): LocalDateTime {
            val request =
                GetEpisodesRequest(
                    seriesId = key.seriesId,
                    adjacentTo = key.itemId,
                    limit = 1,
                )
            return try {
                val result =
                    runBlocking {
                        GetEpisodesRequestHandler
                            .execute(
                                api,
                                request,
                            ).content.items
                    }
                result.firstOrNull()?.userData?.lastPlayedDate ?: LocalDateTime.MIN
            } catch (ex: InvalidStatusException) {
                Timber.w("Error fetching %s: %s", key, ex.localizedMessage)
                LocalDateTime.MIN
            }
        }

        suspend fun getLastPlayed(item: BaseItem): LocalDateTime? {
            val seriesId = item.data.seriesId
            return if (seriesId != null) {
                datePlayedCache.get(SeriesItemId(seriesId, item.id))
            } else {
                null
            }
        }

        fun invalidate(item: BaseItem) {
            item.data.seriesId?.let { seriesId ->
                Timber.d("Invalidating %s", seriesId)
                datePlayedCache.asMap().keys.removeIf { it.seriesId == seriesId }
            }
        }

        suspend fun invalidate(itemId: UUID) {
            val seriesId =
                api.userLibraryApi.getItem(itemId = itemId).content.let {
                    if (it.type == BaseItemKind.SERIES) {
                        itemId
                    } else {
                        it.seriesId
                    }
                }
            if (seriesId != null) {
                Timber.d("Invalidating %s", seriesId)
                datePlayedCache.asMap().keys.removeIf { it.seriesId == seriesId }
            }
        }

        fun invalidateAll() {
            Timber.d("invalidateAll")
            datePlayedCache.invalidateAll()
        }
    }

@ActivityScoped
class DatePlayedInvalidationService
    @Inject
    constructor(
        @param:ActivityContext private val context: Context,
        private val serverRepository: ServerRepository,
        private val datePlayedService: DatePlayedService,
    ) {
        private val activity = (context as AppCompatActivity)

        init {
            serverRepository.current.observe(activity) {
                datePlayedService.invalidateAll()
            }
        }
    }

private data class SeriesItemId(
    val seriesId: UUID,
    val itemId: UUID,
)
