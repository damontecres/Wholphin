package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.PlaybackLanguageChoiceDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.PlaybackLanguageChoice
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.letNotEmpty
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StreamChoiceService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val playbackLanguageChoiceDao: PlaybackLanguageChoiceDao,
    ) {
        suspend fun updateAudio(
            dto: BaseItemDto,
            audioLang: String,
        ) = update(dto) {
            it.copy(
                audioLanguage = audioLang,
            )
        }

        suspend fun updateSubtitles(
            dto: BaseItemDto,
            subtitleLang: String?,
            subtitlesDisabled: Boolean,
        ) = update(dto) {
            it.copy(
                subtitleLanguage = if (subtitlesDisabled) null else subtitleLang,
                subtitlesDisabled = subtitlesDisabled,
            )
        }

        suspend fun update(
            dto: BaseItemDto,
            update: (PlaybackLanguageChoice) -> PlaybackLanguageChoice,
        ) {
            val seriesId = dto.seriesId
            if (seriesId != null) {
                val userId = serverRepository.currentUser.value!!.rowId
                val currentPlc =
                    playbackLanguageChoiceDao.get(userId, seriesId)
                        ?: PlaybackLanguageChoice(userId, seriesId, dto.id)
                val newPlc = update.invoke(currentPlc)
                Timber.v("Saving series PLC: %s", newPlc)
                playbackLanguageChoiceDao.save(newPlc)
            }
        }

        suspend fun getPlaybackLanguageChoice(dto: BaseItemDto) =
            dto.seriesId?.let {
                playbackLanguageChoiceDao.get(serverRepository.currentUser.value!!.rowId, it)
            }

        /**
         * Returns the [MediaSourceInfo] that matched the [ItemPlayback] or else the one with the highest resolution
         */
        fun chooseSource(
            dto: BaseItemDto,
            itemPlayback: ItemPlayback?,
        ): MediaSourceInfo? =
            itemPlayback?.sourceId?.let { dto.mediaSources?.firstOrNull { it.id?.toUUIDOrNull() == itemPlayback.sourceId } }
                ?: chooseSource(dto.mediaSources) // dto.mediaSources?.firstOrNull()

        /**
         * Returns the [MediaSourceInfo] with the highest video resolution
         */
        fun chooseSource(sources: List<MediaSourceInfo>?) =
            sources?.letNotEmpty { sources ->
                val result =
                    sources.maxByOrNull { s ->
                        s.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.let { video ->
                            (video.width ?: 0) * (video.height ?: 0)
                        } ?: 0
                    }
                result
            }

        suspend fun chooseStream(
            source: MediaSourceInfo,
            seriesId: UUID?,
            itemPlayback: ItemPlayback?,
            plc: PlaybackLanguageChoice?,
            type: MediaStreamType,
            prefs: UserPreferences,
        ): MediaStream? {
            val plc = plc ?: seriesId?.let { playbackLanguageChoiceDao.get(serverRepository.currentUser.value!!.rowId, it) }
            return source.mediaStreams?.letNotEmpty { streams ->
                val candidates = streams.filter { it.type == type }
                when (type) {
                    MediaStreamType.AUDIO -> chooseAudioStream(candidates, itemPlayback, plc, prefs)
                    MediaStreamType.SUBTITLE -> chooseSubtitleStream(candidates, itemPlayback, plc, prefs)
                    else -> candidates.firstOrNull()
                }
            }
        }

        fun chooseAudioStream(
            candidates: List<MediaStream>,
            itemPlayback: ItemPlayback?,
            playbackLanguageChoice: PlaybackLanguageChoice?,
            prefs: UserPreferences,
        ): MediaStream? =
            if (itemPlayback?.audioIndexEnabled == true) {
                candidates.firstOrNull { it.index == itemPlayback.audioIndex }
            } else {
                // TODO audio selection based on channel layout or preferences or default
                val audioLanguage =
                    if (prefs.userConfig.audioLanguagePreference.isNotNullOrBlank()) {
                        // If the user has chosen a preferred language, but changed tracks on the series, use that
                        // Otherwise, use their preferred language
                        playbackLanguageChoice?.audioLanguage
                            ?: prefs.userConfig.audioLanguagePreference
                    } else {
                        null
                    }
                if (audioLanguage.isNotNullOrBlank()) {
                    val sorted =
                        candidates.sortedWith(compareBy<MediaStream> { it.language }.thenByDescending { it.channels })
                    sorted.firstOrNull { it.language == audioLanguage && it.isDefault }
                        ?: sorted.firstOrNull { it.language == audioLanguage }
                        ?: sorted.firstOrNull { it.isDefault }
                        ?: sorted.firstOrNull()
                } else {
                    candidates.firstOrNull { it.isDefault }
                        ?: candidates.firstOrNull()
                }
            }

        fun chooseSubtitleStream(
            candidates: List<MediaStream>,
            itemPlayback: ItemPlayback?,
            playbackLanguageChoice: PlaybackLanguageChoice?,
            prefs: UserPreferences,
        ): MediaStream? {
            if (itemPlayback?.subtitleIndex == TrackIndex.DISABLED) {
                return null
            } else if (itemPlayback?.subtitleIndexEnabled == true) {
                return candidates.firstOrNull { it.index == itemPlayback.subtitleIndex }
            } else {
                val subtitleLanguage =
                    if (prefs.userConfig.subtitleLanguagePreference.isNotNullOrBlank()) {
                        // If the user has chosen a preferred language, but changed tracks on the series, use that
                        // Otherwise, use their preferred language
                        playbackLanguageChoice?.subtitleLanguage
                            ?: prefs.userConfig.subtitleLanguagePreference
                    } else {
                        null
                    }

                val subtitleMode =
                    when {
                        playbackLanguageChoice?.subtitlesDisabled == false &&
                            playbackLanguageChoice.subtitleLanguage != null &&
                            subtitleLanguage.isNotNullOrBlank() -> {
                            // User has a subtitle language preference, but has chosen a different language for the series
                            // So override their normal playback mode to always display subtitles
                            SubtitlePlaybackMode.ALWAYS
                        }

                        playbackLanguageChoice?.subtitlesDisabled == true -> {
                            // Series level settings disables subtitles
                            SubtitlePlaybackMode.NONE
                        }

                        else -> {
                            // Fallback to the user's preference
                            prefs.userConfig.subtitleMode
                        }
                    }
                return when (subtitleMode) {
                    SubtitlePlaybackMode.ALWAYS -> {
                        if (subtitleLanguage != null) {
                            candidates.firstOrNull { it.language == subtitleLanguage }
                        } else {
                            candidates.firstOrNull()
                        }
                    }

                    SubtitlePlaybackMode.ONLY_FORCED -> {
                        if (subtitleLanguage != null) {
                            candidates.firstOrNull { it.language == subtitleLanguage && it.isForced }
                        } else {
                            candidates.firstOrNull { it.isForced }
                        }
                    }

                    SubtitlePlaybackMode.SMART -> {
                        val audioLanguage = prefs.userConfig.audioLanguagePreference
                        if (audioLanguage != null && subtitleLanguage != null && audioLanguage != subtitleLanguage) {
                            candidates.firstOrNull { it.language == subtitleLanguage }
                        } else {
                            null
                        }
                    }

                    SubtitlePlaybackMode.DEFAULT -> {
                        // TODO check for language?
                        (
                            candidates.firstOrNull { it.isDefault && it.isForced }
                                ?: candidates.firstOrNull { it.isDefault }
                                ?: candidates.firstOrNull { it.isForced }
                        )
                    }

                    SubtitlePlaybackMode.NONE -> {
                        null
                    }
                }
            }
        }
    }
