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
import org.jellyfin.sdk.model.api.UserConfiguration
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
        private val userConfig: UserConfiguration? get() = serverRepository.currentUserDto.value?.configuration

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

        suspend fun chooseAudioStream(
            source: MediaSourceInfo,
            seriesId: UUID?,
            itemPlayback: ItemPlayback?,
            plc: PlaybackLanguageChoice?,
            prefs: UserPreferences,
        ): MediaStream? {
            val plc = plc ?: seriesId?.let { playbackLanguageChoiceDao.get(serverRepository.currentUser.value!!.rowId, it) }
            return source.mediaStreams?.letNotEmpty { streams ->
                val candidates = streams.filter { it.type == MediaStreamType.AUDIO }
                chooseAudioStream(candidates, itemPlayback, plc, prefs)
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
                val seriesLang =
                    playbackLanguageChoice?.audioLanguage?.takeIf { it.isNotNullOrBlank() }
                // If the user has chosen a different language for the series, prefer that
                val audioLanguage = seriesLang ?: userConfig?.audioLanguagePreference

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

        suspend fun chooseSubtitleStream(
            source: MediaSourceInfo,
            audioStream: MediaStream?,
            seriesId: UUID?,
            itemPlayback: ItemPlayback?,
            plc: PlaybackLanguageChoice?,
            prefs: UserPreferences,
        ): MediaStream? {
            val plc =
                plc ?: seriesId?.let {
                    playbackLanguageChoiceDao.get(
                        serverRepository.currentUser.value!!.rowId,
                        it,
                    )
                }
            return source.mediaStreams?.letNotEmpty { streams ->
                val candidates = streams.filter { it.type == MediaStreamType.SUBTITLE }
                chooseSubtitleStream(
                    audioStream,
                    candidates,
                    itemPlayback,
                    plc,
                    prefs,
                )
            }
        }

        fun chooseSubtitleStream(
            audioStream: MediaStream?,
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
                val seriesLang =
                    playbackLanguageChoice?.subtitleLanguage?.takeIf { it.isNotNullOrBlank() }
                val subtitleLanguage =
                    (seriesLang ?: userConfig?.subtitleLanguagePreference)
                        ?.takeIf { it.isNotNullOrBlank() }

                val subtitleMode =
                    when {
                        playbackLanguageChoice?.subtitlesDisabled == false && seriesLang != null -> {
                            // User has chosen a series level subtitle language, so override their normal
                            // subtitle mode to display that language
                            SubtitlePlaybackMode.ALWAYS
                        }

                        playbackLanguageChoice?.subtitlesDisabled == true && seriesLang == null -> {
                            // Series level settings disables subtitles
                            SubtitlePlaybackMode.NONE
                        }

                        else -> {
                            // Fallback to the user's preference
                            userConfig?.subtitleMode ?: SubtitlePlaybackMode.DEFAULT
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
                        val audioLanguage = userConfig?.audioLanguagePreference
                        val audioStreamLang = audioStream?.language
                        if (audioLanguage.isNotNullOrBlank() && audioStreamLang.isNotNullOrBlank() && audioLanguage != audioStreamLang) {
                            candidates.firstOrNull { it.language == subtitleLanguage }
                        } else {
                            null
                        }
                    }

                    SubtitlePlaybackMode.DEFAULT -> {
                        subtitleLanguage?.let { lang ->
                            // Find best track that is in the preferred language
                            (
                                candidates.firstOrNull { it.isDefault && it.isForced && it.language == lang }
                                    ?: candidates.firstOrNull { it.isDefault && it.language == lang }
                                    ?: candidates.firstOrNull { it.isForced && it.language == lang }
                            )
                        }
                            ?: (
                                // If none in preferred language, just find the best track
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
