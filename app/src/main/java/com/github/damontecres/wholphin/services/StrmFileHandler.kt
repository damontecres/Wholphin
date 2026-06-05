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
        suspend fun resolveStrm(item: BaseItem): BaseItem? {
            if (shouldResolveStrm(item)) {
                Timber.d("Resolved strm %s", item.id)
                resolve(item.id, null)
                var count = 5
                while (count > 0) {
                    Timber.v("Checking for streams in %s", item.id)
                    val fetchedItem =
                        api.userLibraryApi
                            .getItem(item.id)
                            .content
                            .let { BaseItem(it) }
                    if (!shouldResolveStrm(fetchedItem)) {
                        Timber.d("Got updated streams for %s", item.id)
                        return fetchedItem
                    }
                    delay(2000)
                    count--
                }
                Timber.w("Resole timed out for %s", item.id)
                throw TimeoutException("Resole timed out for " + item.id)
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
            fun isStrmFile(item: BaseItem): Boolean =
                item.data.path?.endsWith(".strm") == true &&
                    item.data.mediaSources
                        ?.firstOrNull()
                        ?.isRemote == true

            fun shouldResolveStrm(item: BaseItem): Boolean {
                if (!isStrmFile(item)) {
                    return false
                }
                val streams =
                    item.data.mediaSources
                        ?.firstOrNull()
                        ?.mediaStreams
                return streams.isNullOrEmpty()
            }
        }
    }
