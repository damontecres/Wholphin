package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.util.GetEpisodesRequestHandler
import com.google.common.cache.CacheBuilder
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DatePlayedService
    @Inject
    constructor(
        private val api: ApiClient,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        private val datePlayedCache =
            CacheBuilder
                .newBuilder()
                .maximumSize(AppPreference.HomePageItems.max)
                .expireAfterWrite(2, TimeUnit.HOURS)
                .build<SeriesItemId, Deferred<LocalDateTime>>()

        private fun getLastPlayed(
            seriesId: UUID,
            item: BaseItem,
        ): Deferred<LocalDateTime> {
            val request =
                GetEpisodesRequest(
                    seriesId = seriesId,
                    adjacentTo = item.id,
                    limit = 1,
                )
            return scope.async(Dispatchers.IO) {
                val premiereDate = item.data.premiereDate
                try {
                    val result =
                        GetEpisodesRequestHandler
                            .execute(
                                api,
                                request,
                            ).content.items
                            .firstOrNull()
                            ?.userData
                            ?.lastPlayedDate ?: LocalDateTime.MIN
                    if (premiereDate != null && result.isBefore(premiereDate)) {
                        premiereDate
                    } else {
                        result
                    }
                } catch (ex: InvalidStatusException) {
                    Timber.w(
                        "Error fetching series=%s, item=%s: %s",
                        seriesId,
                        item.id,
                        ex.localizedMessage,
                    )
                    LocalDateTime.MIN.coerceAtLeast(premiereDate ?: LocalDateTime.MIN)
                }
            }
        }

        suspend fun getLastPlayed(item: BaseItem): LocalDateTime? =
            withContext(Dispatchers.IO) {
                val seriesId = item.data.seriesId
                return@withContext if (seriesId != null) {
                    datePlayedCache
                        .get(SeriesItemId(seriesId, item.id)) {
                            getLastPlayed(seriesId, item)
                        }.await()
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
