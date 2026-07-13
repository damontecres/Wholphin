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
         * Attempts to resolve a strm file is not yet resolved. This will attempt to resolve every media source.
         *
         * If the item is not a strm file, nothing happens
         *
         * @return the resolved item or null if could not be resolved
         */
        suspend fun resolveStrm(item: BaseItem): BaseItem? {
            if (shouldResolveStrm(item)) {
                Timber.d("Resolving strm %s", item.id)
                // Resolve the file as a while
                resolve(item.id, null)
                val resolvedItem = waitForUpdate(item.id) { shouldResolveStrm(it) }
                // Resolve each media source that is unresolved
                resolvedItem.data.mediaSources
                    .orEmpty()
                    .filter { it.mediaStreams.isNullOrEmpty() }
                    .forEach {
                        resolve(item.id, it.id)
                    }
                return waitForUpdate(item.id) { shouldResolveStrm(it) }
            }
            return null
        }

        /**
         * Attempts to resolve a specific media source in the strm file if is not yet resolved.
         *
         * If the item is not a strm file, nothing happens
         *
         * @return the resolved item or null if could not be resolved
         */
        suspend fun resolveStrm(
            item: BaseItem,
            sourceId: String,
        ): BaseItem? {
            if (shouldResolveStrm(item, sourceId)) {
                Timber.d("Resolving strm %s", item.id)
                resolve(item.id, sourceId)
                return waitForUpdate(item.id) { shouldResolveStrm(it, sourceId) }
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

        private suspend fun waitForUpdate(
            itemId: UUID,
            valid: (BaseItem) -> Boolean,
        ): BaseItem {
            var count = 5
            while (count > 0) {
                Timber.v("Checking for streams in %s", itemId)
                val fetchedItem =
                    api.userLibraryApi
                        .getItem(itemId)
                        .content
                        .let { BaseItem(it) }
                if (!valid(fetchedItem)) {
                    Timber.d("Got updated streams for %s", itemId)
                    return fetchedItem
                }
                delay(2.seconds)
                count--
            }
            Timber.w("Resolve timed out for %s", itemId)
            throw TimeoutException("Resolve timed out for $itemId")
        }

        companion object {
            fun isStrmFile(item: BaseItem): Boolean = item.data.path?.endsWith(".strm") == true

            fun shouldResolveStrm(item: BaseItem): Boolean {
                if (!isStrmFile(item)) {
                    return false
                }
                return item.data.mediaSources
                    .orEmpty()
                    .any { it.mediaStreams.isNullOrEmpty() }
            }

            fun shouldResolveStrm(
                item: BaseItem,
                sourceId: String,
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
