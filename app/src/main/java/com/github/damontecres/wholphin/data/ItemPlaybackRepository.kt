package com.github.damontecres.wholphin.data

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.PlaybackLanguageChoice
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.StreamChoiceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.MediaSourceInfo
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
        private val streamChoiceService: StreamChoiceService,
    ) {
        suspend fun getSelectedTracks(
            itemId: UUID,
            item: BaseItem,
            prefs: UserPreferences,
        ): ChosenStreams? =
            serverRepository.currentUser.value?.let { user ->
                val itemPlayback = itemPlaybackDao.getItem(user = user, itemId = itemId)
                val plc = streamChoiceService.getPlaybackLanguageChoice(item.data)
                Timber.v("For ${item.id}:  itemPlayback=${itemPlayback != null}, plc=${plc != null}")
                return getChosenItemFromPlayback(item, itemPlayback, plc, prefs)
            }

        fun getChosenItemFromPlayback(
            item: BaseItem,
            itemPlayback: ItemPlayback?,
            plc: PlaybackLanguageChoice?,
            prefs: UserPreferences,
        ): ChosenStreams? {
            val source =
                item.data.mediaSources?.firstOrNull { it.id?.toUUIDOrNull() == itemPlayback?.sourceId }
                    ?: streamChoiceService.chooseSource(item.data.mediaSources.orEmpty())
            if (source != null) {
                val audioStream =
                    streamChoiceService.chooseAudioStream(
                        candidates =
                            source.mediaStreams
                                ?.filter { it.type == MediaStreamType.AUDIO }
                                .orEmpty(),
                        itemPlayback = itemPlayback,
                        playbackLanguageChoice = plc,
                        prefs = prefs,
                    )
                val subtitleStream =
                    streamChoiceService.chooseSubtitleStream(
                        audioStream = audioStream,
                        candidates =
                            source.mediaStreams
                                ?.filter { it.type == MediaStreamType.SUBTITLE }
                                .orEmpty(),
                        itemPlayback = itemPlayback,
                        playbackLanguageChoice = plc,
                        prefs = prefs,
                    )
                return ChosenStreams(
                    itemPlayback = itemPlayback,
                    plc = plc,
                    itemId = item.id,
                    source = source,
                    videoStream = source.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO },
                    audioStream = audioStream,
                    subtitleStream = subtitleStream,
                    subtitlesDisabled = itemPlayback?.subtitleIndex == TrackIndex.DISABLED,
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
                serverRepository.currentUser.value?.let { user ->
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
        ): ItemPlayback =
            serverRepository.current.value!!.let { current ->
                val source =
                    itemPlayback?.sourceId?.let { sourceId ->
                        item.data.mediaSources?.firstOrNull { it.id?.toUUIDOrNull() == sourceId }
                    } ?: streamChoiceService.chooseSource(item.data, null)
                if (source == null) {
                    Timber.w("Could not find media source for ${item.id}")
                    throw IllegalArgumentException("Could not find media source for ${item.id}")
                }
                var toSave =
                    itemPlayback ?: ItemPlayback(
                        userId = current.user.rowId,
                        itemId = item.id,
                        sourceId = source.id?.toUUIDOrNull(),
                    )
                toSave =
                    when (type) {
                        MediaStreamType.AUDIO -> toSave.copy(audioIndex = trackIndex)
                        MediaStreamType.SUBTITLE -> toSave.copy(subtitleIndex = trackIndex)
                        else -> toSave
                    }
                Timber.v("Saving track selection %s", toSave)
                toSave = saveItemPlayback(toSave)
                val seriesId = item.data.seriesId
                if (seriesId != null && trackIndex != TrackIndex.UNSPECIFIED) {
                    if (type == MediaStreamType.AUDIO) {
                        val audioLang = current.userDto.configuration?.audioLanguagePreference
                        val stream = source.mediaStreams?.first { it.index == trackIndex }
                        if (stream?.language != null && stream.language != audioLang) {
                            streamChoiceService.updateAudio(item.data, stream.language!!)
                        }
                    } else if (type == MediaStreamType.SUBTITLE) {
                        if (trackIndex == TrackIndex.DISABLED) {
                            streamChoiceService.updateSubtitles(
                                item.data,
                                subtitleLang = null,
                                subtitlesDisabled = true,
                            )
                        } else {
                            val subtitleLang =
                                current.userDto.configuration?.subtitleLanguagePreference
                            val stream = source.mediaStreams?.first { it.index == trackIndex }
                            if (stream?.language != null && stream.language != subtitleLang) {
                                streamChoiceService.updateSubtitles(
                                    item.data,
                                    stream.language!!,
                                    subtitlesDisabled = false,
                                )
                            }
                        }
                    }
                }
                toSave
            }

        /**
         * Saves the [ItemPlayback] into the database, returning the same object with the rowId updated if needed
         */
        suspend fun saveItemPlayback(itemPlayback: ItemPlayback): ItemPlayback {
            val toSave =
                if (itemPlayback.userId < 0) {
                    val userRowId =
                        serverRepository.currentUser.value
                            ?.rowId
                            ?.takeIf { it >= 0 }
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
    val itemPlayback: ItemPlayback?,
    val plc: PlaybackLanguageChoice?,
    val itemId: UUID,
    val source: MediaSourceInfo,
    val videoStream: MediaStream?,
    val audioStream: MediaStream?,
    val subtitleStream: MediaStream?,
    val subtitlesDisabled: Boolean,
)
