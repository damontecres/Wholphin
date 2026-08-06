package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.SeriesTrackChoiceDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.ActivationFlag
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.SeriesTrackChoice
import com.github.damontecres.wholphin.data.model.SeriesTrackChoiceType
import com.github.damontecres.wholphin.data.model.TrackChoiceParentType
import com.github.damontecres.wholphin.data.model.TrackFlag
import com.github.damontecres.wholphin.data.model.TrackFlag.Companion.has
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.SubtitleModePreference
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.UserProfileSettings
import com.github.damontecres.wholphin.ui.gt
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.ui.util.StringProvider
import com.github.damontecres.wholphin.ui.util.StringStringProvider
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

/**
 * Manage the track choices for media
 */
@Singleton
class StreamChoiceService
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val seriesTrackChoiceDao: SeriesTrackChoiceDao,
    ) {
        private val userConfig: UserConfiguration? get() = serverRepository.currentUserDto?.configuration

        suspend fun saveSeriesTrackChoice(
            dto: BaseItemDto,
            stream: MediaStream,
        ) {
            val type =
                when (stream.type) {
                    MediaStreamType.AUDIO -> SeriesTrackChoiceType.AUDIO
                    MediaStreamType.SUBTITLE -> SeriesTrackChoiceType.SUBTITLE
                    else -> return
                }
            val userId = serverRepository.currentUser!!.rowId
            val newStc =
                buildList {
                    dto.parentId
                        ?.let { seasonId ->
                            SeriesTrackChoice(
                                userId = userId,
                                parentId = seasonId,
                                parentType = TrackChoiceParentType.SEASON,
                                type = type,
                                itemId = dto.id,
                                language = stream.language,
                                activation = ActivationFlag.ACTIVATED,
                                trackFlags = calculateTrackFlags(stream),
                                codec = stream.codec,
                                trackIndex = stream.index,
                                title = stream.title,
                                audioChannels = stream.channels,
                            )
                        }?.let(::add)
                    dto.seriesId
                        ?.let { seriesId ->
                            SeriesTrackChoice(
                                userId = userId,
                                parentId = seriesId,
                                parentType = TrackChoiceParentType.SERIES,
                                type = type,
                                itemId = dto.id,
                                language = stream.language,
                                activation = ActivationFlag.ACTIVATED,
                                trackFlags = calculateTrackFlags(stream),
                                codec = stream.codec,
                                trackIndex = stream.index,
                                title = stream.title,
                                audioChannels = stream.channels,
                            )
                        }?.let(::add)
                }
            Timber.d(
                "Saving series track choices for itemId=%s, seriesId=%s, parentId=%s: %s",
                dto.id,
                dto.seriesId,
                dto.parentId,
                newStc,
            )
            seriesTrackChoiceDao.save(newStc)
        }

        suspend fun saveDisabledSeriesTrackChoice(
            dto: BaseItemDto,
            type: MediaStreamType,
        ) {
            val type =
                when (type) {
                    MediaStreamType.AUDIO -> SeriesTrackChoiceType.AUDIO
                    MediaStreamType.SUBTITLE -> SeriesTrackChoiceType.SUBTITLE
                    else -> return
                }
            val userId = serverRepository.currentUser!!.rowId
            val newStc =
                buildList {
                    dto.parentId
                        ?.let { seasonId ->
                            SeriesTrackChoice(
                                userId = userId,
                                parentId = seasonId,
                                parentType = TrackChoiceParentType.SEASON,
                                type = type,
                                itemId = dto.id,
                                language = null,
                                activation = ActivationFlag.DISABLED,
                                trackFlags = 0,
                                codec = null,
                                trackIndex = null,
                                title = null,
                                audioChannels = null,
                            )
                        }?.let(::add)
                    dto.seriesId
                        ?.let { seriesId ->
                            SeriesTrackChoice(
                                userId = userId,
                                parentId = seriesId,
                                parentType = TrackChoiceParentType.SERIES,
                                type = type,
                                itemId = dto.id,
                                language = null,
                                activation = ActivationFlag.DISABLED,
                                trackFlags = 0,
                                codec = null,
                                trackIndex = null,
                                title = null,
                                audioChannels = null,
                            )
                        }?.let(::add)
                }
            Timber.d(
                "Saving disabled series track choices for itemId=%s, seriesId=%s, parentId=%s",
                dto.id,
                dto.seriesId,
                dto.parentId,
            )
            seriesTrackChoiceDao.save(newStc)
        }

        suspend fun saveOnlyForcedSeriesTrackChoice(
            dto: BaseItemDto,
            type: MediaStreamType,
        ) {
            val type =
                when (type) {
                    MediaStreamType.AUDIO -> SeriesTrackChoiceType.AUDIO
                    MediaStreamType.SUBTITLE -> SeriesTrackChoiceType.SUBTITLE
                    else -> return
                }
            val userId = serverRepository.currentUser!!.rowId
            val newStc =
                buildList {
                    dto.parentId
                        ?.let { seasonId ->
                            SeriesTrackChoice(
                                userId = userId,
                                parentId = seasonId,
                                parentType = TrackChoiceParentType.SEASON,
                                type = type,
                                itemId = dto.id,
                                language = null,
                                activation = ActivationFlag.ONLY_FORCED,
                                trackFlags = 0,
                                codec = null,
                                trackIndex = null,
                                title = null,
                                audioChannels = null,
                            )
                        }?.let(::add)
                    dto.seriesId
                        ?.let { seriesId ->
                            SeriesTrackChoice(
                                userId = userId,
                                parentId = seriesId,
                                parentType = TrackChoiceParentType.SERIES,
                                type = type,
                                itemId = dto.id,
                                language = null,
                                activation = ActivationFlag.ONLY_FORCED,
                                trackFlags = 0,
                                codec = null,
                                trackIndex = null,
                                title = null,
                                audioChannels = null,
                            )
                        }?.let(::add)
                }
            Timber.d(
                "Saving only forced series track choices for itemId=%s, seriesId=%s, parentId=%s",
                dto.id,
                dto.seriesId,
                dto.parentId,
            )
            seriesTrackChoiceDao.save(newStc)
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

        /**
         * Returns the audio stream that should play
         */
        suspend fun chooseAudioStream(
            source: MediaSourceInfo,
            item: BaseItemDto,
            itemPlayback: ItemPlayback?,
            stc: List<SeriesTrackChoice>?,
            prefs: UserPreferences,
        ): StreamChoiceResult? {
            val stc = stc ?: getSeriesTrackChoices(item, SeriesTrackChoiceType.SUBTITLE)
            return source.mediaStreams?.letNotEmpty { streams ->
                val candidates = streams.filter { it.type == MediaStreamType.AUDIO }
                chooseAudioStream(candidates, itemPlayback, stc, prefs)
            }
        }

        /**
         * Returns the audio stream that should play
         */
        fun chooseAudioStream(
            candidates: List<MediaStream>,
            itemPlayback: ItemPlayback?,
            stc: List<SeriesTrackChoice>,
            prefs: UserPreferences,
        ): StreamChoiceResult =
            if (itemPlayback?.audioIndexEnabled == true) {
                candidates.firstOrNull { it.index == itemPlayback.audioIndex } with
                    StreamChoiceReason.Item(
                        itemPlayback,
                    )
            } else if (stc.isNotEmpty()) {
                val first = stc.first()
                val result = scoreStreams(candidates, first)
                if (result.isEmpty() && stc.size > 1) {
                    // SeriesTrackChoice did not apply to any streams, but there are more options
                    chooseAudioStream(candidates, itemPlayback, stc.subList(1, stc.size), prefs)
                } else if (result.isEmpty()) {
                    // SeriesTrackChoice did not apply to any streams, so use regular selection logic
                    Timber.v("No audio STC applied")
                    chooseAudioStream(candidates, itemPlayback, emptyList(), prefs)
                } else {
                    // Otherwise, use the best scored stream
                    Timber.v("Using audio STC from %s", first.parentId)
                    result.first().second with StreamChoiceReason.from(first)
                }
            } else {
                val audioLanguage = getPreferredLanguage(MediaStreamType.AUDIO, prefs, userConfig)
                val stream =
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
                stream with StreamChoiceReason.UserPreferences
            }

        /**
         * Returns the subtitle stream that should play
         */
        suspend fun chooseSubtitleStream(
            item: BaseItemDto,
            source: MediaSourceInfo,
            audioStream: MediaStream?,
            itemPlayback: ItemPlayback?,
            stc: List<SeriesTrackChoice>?,
            prefs: UserPreferences,
        ): StreamChoiceResult? =
            source.mediaStreams?.letNotEmpty { streams ->
                val candidates = streams.filter { it.type == MediaStreamType.SUBTITLE }
                val stc = stc ?: getSeriesTrackChoices(item, SeriesTrackChoiceType.SUBTITLE)
                chooseSubtitleStream(
                    audioStream?.language,
                    candidates,
                    itemPlayback,
                    stc,
                    prefs,
                )
            }

        /**
         * Resolves ONLY_FORCED to an actual subtitle track index.
         * Returns the original index if not ONLY_FORCED or DISABLED.
         */
        suspend fun resolveSubtitleIndex(
            source: MediaSourceInfo,
            audioStreamIndex: Int?,
            item: BaseItemDto,
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
                        userId = serverRepository.currentUser!!.rowId,
                        itemId = item.id,
                        subtitleIndex = TrackIndex.ONLY_FORCED,
                    )
                chooseSubtitleStream(
                    item = item,
                    source = source,
                    audioStream = audioStream,
                    itemPlayback = itemPlayback,
                    stc = null,
                    prefs = prefs,
                )?.stream?.index
            }

        /**
         * Returns the subtitle stream that should play
         */
        fun chooseSubtitleStream(
            audioStreamLang: String?,
            candidates: List<MediaStream>,
            itemPlayback: ItemPlayback?,
            stc: List<SeriesTrackChoice>,
            prefs: UserPreferences,
        ): StreamChoiceResult {
            if (itemPlayback?.subtitleIndex == TrackIndex.DISABLED) {
                return null with StreamChoiceReason.Item(itemPlayback)
            } else if (stc.isNotEmpty()) {
                val first = stc.first()
                return when (first.activation) {
                    ActivationFlag.DISABLED -> {
                        null with StreamChoiceReason.from(first)
                    }

                    ActivationFlag.ONLY_FORCED -> {
                        val subtitleLanguage =
                            getPreferredLanguage(MediaStreamType.SUBTITLE, prefs, userConfig)
                        findForcedTrack(
                            candidates,
                            subtitleLanguage,
                            audioStreamLang,
                        ) with StreamChoiceReason.from(first)
                    }

                    ActivationFlag.ACTIVATED -> {
                        val result = scoreStreams(candidates, first)
                        if (result.isEmpty() && stc.size > 1) {
                            // SeriesTrackChoice did not apply to any streams, but there are more options
                            chooseSubtitleStream(
                                audioStreamLang,
                                candidates,
                                itemPlayback,
                                stc.subList(1, stc.size),
                                prefs,
                            )
                        } else if (result.isEmpty()) {
                            // SeriesTrackChoice did not apply to any streams, so use regular selection logic
                            Timber.v("No subtitle STC applied")
                            chooseSubtitleStream(
                                audioStreamLang,
                                candidates,
                                itemPlayback,
                                emptyList(),
                                prefs,
                            )
                        } else {
                            // Otherwise, use the best scored stream
                            Timber.v("Using subtitle STC from %s", first.parentId)
                            result.first().second with StreamChoiceReason.from(first)
                        }
                    }
                }
            }
            val subtitleLanguage = getPreferredLanguage(MediaStreamType.SUBTITLE, prefs, userConfig)
            if (itemPlayback?.subtitleIndex == TrackIndex.ONLY_FORCED) {
                // Client-side manual override: User selected "Only Forced" in player menu
                return findForcedTrack(
                    candidates,
                    subtitleLanguage,
                    audioStreamLang,
                ) with StreamChoiceReason.Item(itemPlayback)
            } else if (itemPlayback?.subtitleIndexEnabled == true) {
                return candidates.firstOrNull { it.index == itemPlayback.subtitleIndex } with
                    StreamChoiceReason.Item(itemPlayback)
            } else {
                val subtitleMode =
                    when (prefs.userPreferences?.subtitleMode) {
                        SubtitleModePreference.USE_USER_PROFILE -> userConfig?.subtitleMode
                        SubtitleModePreference.DEFAULT -> SubtitlePlaybackMode.DEFAULT
                        SubtitleModePreference.SMART -> SubtitlePlaybackMode.SMART
                        SubtitleModePreference.ONLY_FORCED -> SubtitlePlaybackMode.ONLY_FORCED
                        SubtitleModePreference.ALWAYS -> SubtitlePlaybackMode.ALWAYS
                        SubtitleModePreference.NONE -> SubtitlePlaybackMode.NONE
                        null -> SubtitlePlaybackMode.DEFAULT
                    } ?: SubtitlePlaybackMode.DEFAULT
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
                val stream =
                    when (subtitleMode) {
                        SubtitlePlaybackMode.ALWAYS -> {
                            if (subtitleLanguage.isNotNullOrBlank()) {
                                candidates.firstOrNull {
                                    // Prefer non-forced first
                                    !it.isForced && it.language.equalsLangOrUnknown(subtitleLanguage)
                                } ?: candidates.firstOrNull {
                                    it.language.equalsLangOrUnknown(subtitleLanguage)
                                }
                            } else {
                                candidates.firstOrNull { !it.isForced } ?: candidates.firstOrNull()
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
                return stream with StreamChoiceReason.UserPreferences
            }
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

        suspend fun getSeriesTrackChoices(
            seriesId: UUID?,
            seasonId: UUID?,
            type: SeriesTrackChoiceType,
        ): List<SeriesTrackChoice> {
            val userId = serverRepository.currentUser?.rowId ?: return emptyList()
            return when {
                seriesId != null && seasonId != null -> {
                    seriesTrackChoiceDao.get(
                        userId = userId,
                        seasonId = seasonId,
                        seriesId = seriesId,
                        type = type,
                    )
                }

                seriesId != null -> {
                    seriesTrackChoiceDao.getBySeriesId(
                        userId = userId,
                        seriesId = seriesId,
                        type = type,
                    )
                }

                seasonId != null -> {
                    seriesTrackChoiceDao.getBySeasonId(
                        userId = userId,
                        seasonId = seasonId,
                        type = type,
                    )
                }

                else -> {
                    emptyList()
                }
            }
        }

        suspend fun getSeriesTrackChoices(
            item: BaseItemDto,
            type: SeriesTrackChoiceType,
        ): List<SeriesTrackChoice> = getSeriesTrackChoices(item.seriesId, item.parentId, type)

        suspend fun getSeriesTrackChoices(
            item: BaseItemDto,
            type: MediaStreamType,
        ): List<SeriesTrackChoice> =
            getSeriesTrackChoices(
                seriesId = item.seriesId,
                seasonId = item.parentId,
                type =
                    when (type) {
                        MediaStreamType.AUDIO -> SeriesTrackChoiceType.AUDIO
                        MediaStreamType.SUBTITLE -> SeriesTrackChoiceType.SUBTITLE
                        else -> return emptyList()
                    },
            )
    }

private val String?.isUnknown: Boolean
    get() =
        this.isNullOrBlank() ||
            this.equals("unknown", true) ||
            this.equals("und", true) ||
            this.equals("undetermined", true) ||
            this.equals("mul", true) ||
            this.equals("zxx", true)

private val String?.isNotUnknown: Boolean
    get() = !this.isUnknown

private fun String?.equalsLangOrUnknown(lang: String): Boolean = equals(lang, ignoreCase = true) || this.isUnknown

private fun String?.equalsLangExact(lang: String?): Boolean = this.isNotUnknown && lang.isNotUnknown && equals(lang, ignoreCase = true)

/** Returns true if the track is forced (via metadata flag or title patterns). */
fun isForcedOrSigns(track: MediaStream): Boolean = track.isForced || isSigns(track)

fun isSigns(track: MediaStream): Boolean {
    val title = track.title ?: track.displayTitle ?: return false
    return title.contains("forced", ignoreCase = true) ||
        title.contains("signs", ignoreCase = true) ||
        title.contains("songs", ignoreCase = true)
}

/**
 * Based on the user's preferences, get their preferred language for audio or subtitles
 *
 * @param prefs the [UserPreferences]
 */
fun getPreferredLanguage(
    type: MediaStreamType,
    prefs: UserPreferences,
    userConfig: UserConfiguration?,
): String? {
    val (pref, profileLang) =
        when (type) {
            MediaStreamType.AUDIO -> {
                prefs.userPreferences?.preferredAudioLanguage to
                    userConfig?.audioLanguagePreference
            }

            MediaStreamType.SUBTITLE -> {
                prefs.userPreferences?.preferredSubtitleLanguage to
                    userConfig?.subtitleLanguagePreference
            }

            else -> {
                throw IllegalArgumentException("Only audio or subtitle supported, not $type")
            }
        }
    return when (pref) {
        UserProfileSettings.USE_USER_PROFILE -> profileLang
        UserProfileSettings.PREFER_ANY_LANGUAGE -> null
        else -> pref
    }?.takeIf { it.isNotNullOrBlank() }
}

/**
 * Scores streams based the user's explicit [SeriesTrackChoice]
 */
fun scoreStreams(
    streams: List<MediaStream>,
    choice: SeriesTrackChoice,
): List<Pair<Int, MediaStream>> {
    if (choice.activation == ActivationFlag.DISABLED) {
        return emptyList()
    }
    val streams =
        streams.filter {
            if (
                (choice.activation == ActivationFlag.ONLY_FORCED || choice.has(TrackFlag.FORCED)) &&
                !isForcedOrSigns(it)
            ) {
                // Filter out non-forced tracks if the user only wants forced
                return@filter false
            }
            if (choice.language.isNotUnknown &&
                !choice.language.equalsLangExact(it.language)
            ) {
                // Filter out languages that do not match
                return@filter false
            }
            // Filter by type
            when (choice.type) {
                SeriesTrackChoiceType.AUDIO -> it.type == MediaStreamType.AUDIO
                SeriesTrackChoiceType.SUBTITLE -> it.type == MediaStreamType.SUBTITLE
            }
        }
    val scored =
        streams
            .map { s ->
                var score = 0

                // Prefer exact language match over unknown
                // TODO what if choice.language.isUnknown and the user has a preferred subtitle language?
                // choice.language is never unknown, it is only saved if specified
                if (choice.language.equalsLangExact(s.language)) score += 10_000

//                        if (s.language.isUnknown) score += 10

                if (s.isForced && choice.has(TrackFlag.FORCED)) {
                    score += 1_000
                }
                if (choice.has(TrackFlag.SIGNS) && isSigns(s)) {
                    score += 500
                }
                if (s.isDefault && choice.has(TrackFlag.DEFAULT)) {
                    score += 500
                }
                if (s.isHearingImpaired && choice.has(TrackFlag.SDH)) {
                    score += 500
                }
                if (s.isExternal && choice.has(TrackFlag.EXTERNAL)) {
                    score += 100
                }
                if (s.index == choice.trackIndex) {
                    score += 100
                }
                if (s.title == choice.title) {
                    score += 10
                }

                if (choice.audioChannels != null && s.channels == choice.audioChannels) {
                    score += 100
                } else if (choice.audioChannels != null && s.channels.gt(choice.audioChannels)) {
                    score += 50
                }

                score to s
            }.sortedByDescending { it.first }
    return scored
}

private fun calculateTrackFlags(track: MediaStream): Int {
    var flag = 0
    TrackFlag.entries.forEach {
        if (it.hasFlag.invoke(track)) flag = flag or it.flag
    }
    return flag
}

data class StreamChoiceResult(
    val stream: MediaStream?,
    val reason: StreamChoiceReason,
)

private infix fun MediaStream?.with(reason: StreamChoiceReason) = StreamChoiceResult(this, reason)

sealed interface StreamChoiceReason {
    val description: StringProvider

    data object Unknown : StreamChoiceReason {
        override val description: StringProvider = ResStringProvider(R.string.unknown)
    }

    data object UserPreferences : StreamChoiceReason {
        override val description: StringProvider =
            ResStringProvider(R.string.profile_specific_settings)
    }

    data class Series(
        val stc: SeriesTrackChoice,
    ) : StreamChoiceReason {
        override val description: StringProvider =
            when (stc.activation) {
                ActivationFlag.ACTIVATED -> StringStringProvider("Series choice")
                ActivationFlag.DISABLED -> StringStringProvider("Disabled by Series")
                ActivationFlag.ONLY_FORCED -> StringStringProvider("Only forced by Series")
            }
    }

    data class Season(
        val stc: SeriesTrackChoice,
    ) : StreamChoiceReason {
        override val description: StringProvider =
            when (stc.activation) {
                ActivationFlag.ACTIVATED -> StringStringProvider("Season choice")
                ActivationFlag.DISABLED -> StringStringProvider("Disabled by Season")
                ActivationFlag.ONLY_FORCED -> StringStringProvider("Only forced by Season")
            }
    }

    data class Item(
        val itemPlayback: ItemPlayback,
    ) : StreamChoiceReason {
        override val description: StringProvider = StringStringProvider("Item specific")
    }

    companion object {
        fun from(stc: SeriesTrackChoice) =
            when (stc.parentType) {
                TrackChoiceParentType.SERIES -> Series(stc)
                TrackChoiceParentType.SEASON -> Season(stc)
                TrackChoiceParentType.DEPRECATED -> Unknown
                TrackChoiceParentType.UNKNOWN -> Unknown
            }
    }
}
