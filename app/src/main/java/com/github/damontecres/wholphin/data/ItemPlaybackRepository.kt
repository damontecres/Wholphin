package com.github.damontecres.wholphin.data

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.TrackIndex
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemPlaybackRepository
    @Inject
    constructor(
        val serverRepository: ServerRepository,
        val itemPlaybackDao: ItemPlaybackDao,
    ) {
        fun getSelectedTracks(
            itemId: UUID,
            item: BaseItem,
        ): ChosenStreams? =
            serverRepository.currentUser?.let { user ->
                val itemPlayback = itemPlaybackDao.getItem(user = user, itemId = itemId)
                if (itemPlayback != null) {
                    Timber.v("Got itemPlayback for %s", itemId)
                    val source =
                        item.data.mediaSources?.firstOrNull { it.id?.toUUIDOrNull() == itemPlayback.sourceId }
                    if (source != null) {
                        val audioStream =
                            if (itemPlayback.audioIndexEnabled) {
                                source.mediaStreams?.firstOrNull { it.index == itemPlayback.audioIndex }
                            } else {
                                null
                            }
                        val subtitleStream =
                            if (itemPlayback.subtitleIndexEnabled) {
                                source.mediaStreams?.firstOrNull { it.index == itemPlayback.subtitleIndex }
                            } else {
                                null
                            }
                        ChosenStreams(
                            itemId,
                            audioStream,
                            subtitleStream,
                            itemPlayback.subtitleIndex == TrackIndex.DISABLED,
                        )
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
    }

data class ChosenStreams(
    val itemId: UUID,
    val audioStream: MediaStream?,
    val subtitleStream: MediaStream?,
    val subtitlesDisabled: Boolean,
)
