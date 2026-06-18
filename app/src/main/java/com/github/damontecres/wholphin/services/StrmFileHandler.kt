package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import kotlinx.coroutines.delay
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.TimeoutException
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Resolves .strm files to populate the tracks & metadata
 */
@Singleton
class StrmFileHandler
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        /**
         * Attempts to resolve if the strm file is not yet resolved.
         *
         * If the item is not a strm, file, nothing happens
         *
         * @return the resolved item or null if could not be resolved
         */
        suspend fun resolveStrm(
            item: BaseItem,
            sourceId: String? = null,
        ): BaseItem? {
            if (shouldResolveStrm(item, sourceId)) {
                Timber.d("Resolving strm %s", item.id)
                resolve(item.id, null)
                var count = 5
                while (count > 0) {
                    Timber.v("Checking for streams in %s", item.id)
                    val fetchedItem =
                        api.userLibraryApi
                            .getItem(item.id)
                            .content
                            .let { BaseItem(it) }
                    if (!shouldResolveStrm(fetchedItem, sourceId)) {
                        Timber.d("Got updated streams for %s", item.id)
                        return fetchedItem
                    }
                    delay(2.seconds)
                    count--
                }
                Timber.w("Resolve timed out for %s", item.id)
                throw TimeoutException("Resolve timed out for " + item.id)
            }
            return null
        }

        private suspend fun resolve(
            itemId: UUID,
            mediaSourceId: String?,
        ) {
            val response by
                api.mediaInfoApi
                    .getPostedPlaybackInfo(
                        itemId,
                        PlaybackInfoDto(
                            deviceProfile = null,
                            mediaSourceId = mediaSourceId,
                            autoOpenLiveStream = false,
                        ),
                    )
            if (response.errorCode != null) {
                throw IllegalStateException("Error: " + response.errorCode)
            }
        }

        companion object {
            fun isStrmFile(item: BaseItem): Boolean = item.data.path?.endsWith(".strm") == true

            fun shouldResolveStrm(
                item: BaseItem,
                sourceId: String? = null,
            ): Boolean {
                if (!isStrmFile(item)) {
                    return false
                }
                val source =
                    sourceId?.let { item.data.mediaSources?.firstOrNull { it.id == sourceId } }
                        ?: item.data.mediaSources?.firstOrNull()
                val streams = source?.mediaStreams
                return streams.isNullOrEmpty()
            }
        }
    }
