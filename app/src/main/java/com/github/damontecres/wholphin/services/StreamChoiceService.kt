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
                    audioStream?.language,
                    candidates,
                    itemPlayback,
                    plc,
                    prefs,
                )
            }
        }

        /**
         * Resolves ONLY_FORCED to an actual subtitle track index.
         * Returns the original index if not ONLY_FORCED or DISABLED.
         */
        suspend fun resolveSubtitleIndex(
            source: MediaSourceInfo,
            audioStreamIndex: Int?,
            seriesId: UUID?,
            subtitleIndex: Int,
            prefs: UserPreferences,
        ): Int? =
            if (subtitleIndex != TrackIndex.ONLY_FORCED) {
                subtitleIndex
            } else {
                val audioStream =
                    source.mediaStreams?.firstOrNull {
                        it.type == MediaStreamType.AUDIO && it.index == audioStreamIndex
                    }
                val itemPlayback =
                    ItemPlayback(
                        userId = serverRepository.currentUser.value!!.rowId,
                        itemId = UUID.randomUUID(), // Not used for ONLY_FORCED resolution
                        subtitleIndex = TrackIndex.ONLY_FORCED,
                    )
                chooseSubtitleStream(
                    source = source,
                    audioStream = audioStream,
                    seriesId = seriesId,
                    itemPlayback = itemPlayback,
                    plc = null,
                    prefs = prefs,
                )?.index
            }

        fun chooseSubtitleStream(
            audioStreamLang: String?,
            candidates: List<MediaStream>,
            itemPlayback: ItemPlayback?,
            playbackLanguageChoice: PlaybackLanguageChoice?,
            prefs: UserPreferences,
        ): MediaStream? {
            if (itemPlayback?.subtitleIndex == TrackIndex.DISABLED) {
                return null
            } else if (itemPlayback?.subtitleIndex == TrackIndex.ONLY_FORCED) {
                // Client-side manual override: User selected "Only Forced" in player menu
                val seriesLang =
                    playbackLanguageChoice?.subtitleLanguage?.takeIf { it.isNotNullOrBlank() }
                val subtitleLanguage =
                    (seriesLang ?: userConfig?.subtitleLanguagePreference)
                        ?.takeIf { it.isNotNullOrBlank() }
                return findForcedTrack(candidates, subtitleLanguage, audioStreamLang)
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
                val candidates =
                    candidates
                        .sortedWith(
                            compareByDescending<MediaStream> { it.isExternal }
                                .thenByDescending { it.isDefault }
                                .thenByDescending {
                                    !it.isForced && it.language.equals(subtitleLanguage, true)
                                }.thenByDescending {
                                    it.isForced && it.language.equals(subtitleLanguage, true)
                                }.thenByDescending { it.isForced && it.language.isUnknown }
                                .thenByDescending { it.isForced },
                        )
                return when (subtitleMode) {
                    SubtitlePlaybackMode.ALWAYS -> {
                        if (subtitleLanguage.isNotNullOrBlank()) {
                            candidates.firstOrNull {
                                it.language.equals(subtitleLanguage, true) ||
                                    it.language.isUnknown
                            }
                        } else {
                            candidates.firstOrNull()
                        }
                    }

                    SubtitlePlaybackMode.ONLY_FORCED -> {
                        if (subtitleLanguage.isNotNullOrBlank()) {
                            candidates.firstOrNull { it.language == subtitleLanguage && it.isForced }
                                ?: candidates.firstOrNull { it.language.isUnknown && it.isForced }
                        } else {
                            candidates.firstOrNull { it.isForced }
                        }
                    }

                    SubtitlePlaybackMode.SMART -> {
                        if (subtitleLanguage.isNotNullOrBlank()) {
                            val audioLanguage = userConfig?.audioLanguagePreference
                            if (
                                // Has preferred subtitle lang & preferred audio, so only show subtitles if actual audio is different
                                (audioLanguage.isNotNullOrBlank() && audioLanguage != audioStreamLang) ||
                                // Has preferred subtitle lang, but no preferred audio lang, so show subtitle if subtitle lang is different from actual audio
                                (audioLanguage.isNullOrBlank() && subtitleLanguage != audioStreamLang)
                            ) {
                                candidates.firstOrNull { it.language == subtitleLanguage }
                                    ?: candidates.firstOrNull { it.language.isUnknown }
                            } else {
                                // Otherwise, show forced subtitles in preferred lang
                                candidates.firstOrNull { it.isForced && it.language == subtitleLanguage }
                                    ?: candidates.firstOrNull { it.isForced && it.language.isUnknown }
                            }
                        } else {
                            candidates.firstOrNull { it.isDefault }
                        }
                    }

                    SubtitlePlaybackMode.DEFAULT -> {
                        if (subtitleLanguage.isNotNullOrBlank()) {
                            candidates.firstOrNull { it.language == subtitleLanguage && (it.isDefault || it.isForced) }
                                ?: candidates.firstOrNull { it.isDefault || it.isForced }
                        } else {
                            candidates.firstOrNull { it.isDefault || it.isForced }
                        }
                    }

                    SubtitlePlaybackMode.NONE -> {
                        null
                    }
                }
            }
        }

        /** Returns true if the track is forced (via metadata flag or title patterns). */
        private fun isForcedOrSigns(track: MediaStream): Boolean {
            if (track.isForced) return true
            val title = track.title ?: track.displayTitle ?: return false
            return title.contains("forced", ignoreCase = true) ||
                title.contains("signs", ignoreCase = true) ||
                title.contains("songs", ignoreCase = true)
        }

        /** Finds a forced/signs track: subtitle pref -> audio -> unknown -> null. */
        private fun findForcedTrack(
            candidates: List<MediaStream>,
            subtitleLanguage: String?,
            audioLanguage: String?,
        ): MediaStream? {
            // 1. User's preferred subtitle language
            if (subtitleLanguage != null) {
                candidates
                    .firstOrNull { it.language.equals(subtitleLanguage, true) && isForcedOrSigns(it) }
                    ?.let { return it }
            }
            // 2. Audio language (for sign-subtitles if no preference match)
            if (audioLanguage != null) {
                candidates
                    .firstOrNull { it.language.equals(audioLanguage, true) && isForcedOrSigns(it) }
                    ?.let { return it }
            }
            // 3. Unknown language forced track
            return candidates.firstOrNull { it.language.isUnknown && isForcedOrSigns(it) }
        }
    }

private val String?.isUnknown: Boolean
    get() =
        this == null ||
            this.equals("unknown", true) ||
            this.equals("und", true) ||
            this.equals("undetermined", true) ||
            this.equals("mul", true) ||
            this.equals("zxx", true)
