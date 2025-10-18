package com.github.damontecres.wholphin.data

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.data.model.chooseSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
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
                    getChosenItemFromPlayback(item, itemPlayback)
                } else {
                    null
                }
            }

        fun getChosenItemFromPlayback(
            item: BaseItem,
            itemPlayback: ItemPlayback,
        ): ChosenStreams? {
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
                return ChosenStreams(
                    itemPlayback,
                    item.id,
                    source.id?.toUUIDOrNull(),
                    audioStream,
                    subtitleStream,
                    itemPlayback.subtitleIndex == TrackIndex.DISABLED,
                )
            } else {
                return null
            }
        }

        suspend fun savePlayVersion(
            itemId: UUID,
            sourceId: UUID,
        ): ItemPlayback? =
            withContext(Dispatchers.IO) {
                serverRepository.currentUser?.let { user ->
                    val itemPlayback =
                        ItemPlayback(
                            userId = user.rowId,
                            itemId = itemId,
                            sourceId = sourceId,
                        )
                    Timber.v("Saving play version %s", itemPlayback)
                    saveItemPlayback(itemPlayback)
                }
            }

        suspend fun saveTrackSelection(
            item: BaseItem,
            itemPlayback: ItemPlayback?,
            trackIndex: Int,
            type: MediaStreamType,
        ) = serverRepository.currentUser?.let { user ->
            var toSave =
                itemPlayback ?: ItemPlayback(
                    userId = user.rowId,
                    itemId = item.id,
                    sourceId = chooseSource(item.data, null)?.id?.toUUIDOrNull(),
                )
            toSave =
                when (type) {
                    MediaStreamType.AUDIO -> toSave.copy(audioIndex = trackIndex)
                    MediaStreamType.SUBTITLE -> toSave.copy(subtitleIndex = trackIndex)
                    else -> toSave
                }
            Timber.v("Saving track selection %s", toSave)
            saveItemPlayback(toSave)
        }

        /**
         * Saves the [ItemPlayback] into the database, returning the same object with the rowId updated if needed
         */
        suspend fun saveItemPlayback(itemPlayback: ItemPlayback): ItemPlayback {
            val toSave =
                if (itemPlayback.userId < 0) {
                    val userRowId =
                        serverRepository.currentUser?.rowId?.takeIf { it >= 0 }
                            ?: throw IllegalStateException("Trying to save an ItemPlayback without a user, but there is no current user")
                    itemPlayback.copy(userId = userRowId)
                } else {
                    itemPlayback
                }
            val id = itemPlaybackDao.saveItem(toSave)
            return toSave.copy(rowId = id)
        }
    }

data class ChosenStreams(
    val itemPlayback: ItemPlayback,
    val itemId: UUID,
    val sourceId: UUID?,
    val audioStream: MediaStream?,
    val subtitleStream: MediaStream?,
    val subtitlesDisabled: Boolean,
)
