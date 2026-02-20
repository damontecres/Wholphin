package com.github.damontecres.wholphin.ui.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.preferences.PlayerBackend
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import timber.log.Timber
import kotlin.math.max

object TrackSelectionUtils {
    @OptIn(UnstableApi::class)
    fun createTrackSelections(
        trackSelectionParams: TrackSelectionParameters,
        tracks: Tracks,
        playerBackend: PlayerBackend,
        supportsDirectPlay: Boolean,
        audioIndex: Int?,
        subtitleIndex: Int?,
        source: MediaSourceInfo,
    ): TrackSelectionResult {
        val embeddedSubtitleCount = source.embeddedSubtitleCount
        val externalSubtitleCount = source.externalSubtitlesCount

        val paramsBuilder = trackSelectionParams.buildUpon()
        val groups = tracks.groups

        val subtitleSelected =
            if (subtitleIndex != null && subtitleIndex >= 0) {
                val subtitleIsExternal = source.findExternalSubtitle(subtitleIndex) != null
                if (subtitleIsExternal || supportsDirectPlay) {
                    val chosenTrack =
                        if (subtitleIsExternal && playerBackend == PlayerBackend.EXO_PLAYER) {
                            groups.firstOrNull { group ->
                                group.type == C.TRACK_TYPE_TEXT && group.isSupported &&
                                    (0..<group.mediaTrackGroup.length)
                                        .mapNotNull {
                                            group.getTrackFormat(it).id
                                        }.any { it.endsWith("e:$subtitleIndex") }
                            }
                        } else {
                            val actualEmbeddedCount =
                                groups
                                    .filter { group ->
                                        group.type == C.TRACK_TYPE_TEXT &&
                                            (0..<group.mediaTrackGroup.length)
                                                .mapNotNull {
                                                    group.getTrackFormat(it).id
                                                }.none { it.contains("e:") }
                                    }.size
                            val indexToFind =
                                calculateIndexToFind(
                                    subtitleIndex,
                                    MediaStreamType.SUBTITLE,
                                    playerBackend,
                                    embeddedSubtitleCount,
                                    externalSubtitleCount,
                                    subtitleIsExternal,
                                    actualEmbeddedCount,
                                    source,
                                )
                            Timber.v("Chosen subtitle ($subtitleIndex/$indexToFind) track")
                            // subtitleIndex - externalSubtitleCount + 1
                            groups.firstOrNull { group ->
                                group.type == C.TRACK_TYPE_TEXT && group.isSupported &&
                                    (0..<group.mediaTrackGroup.length)
                                        .filter {
                                            if (subtitleIsExternal) {
                                                group.getTrackFormat(0).id?.contains("e:") == true
                                            } else {
                                                group.getTrackFormat(0).id?.contains("e:") == false
                                            }
                                        }.map {
                                            group.getTrackFormat(it).idAsInt
                                        }.contains(indexToFind)
                            }
                        }

                    Timber.v("Chosen subtitle ($subtitleIndex) track: $chosenTrack")
                    chosenTrack?.let {
                        paramsBuilder
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                TrackSelectionOverride(
                                    chosenTrack.mediaTrackGroup,
                                    0,
                                ),
                            )
                    }
                    chosenTrack != null
                } else {
                    false
                }
            } else {
                paramsBuilder
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)

                true
            }
        val audioSelected =
            if (audioIndex != null && supportsDirectPlay) {
                val indexToFind =
                    calculateIndexToFind(
                        audioIndex,
                        MediaStreamType.AUDIO,
                        playerBackend,
                        embeddedSubtitleCount,
                        externalSubtitleCount,
                        false,
                        null,
                        source,
                    )
                val chosenTrack =
                    groups.firstOrNull { group ->
                        group.type == C.TRACK_TYPE_AUDIO && group.isSupported &&
                            (0..<group.mediaTrackGroup.length)
                                .map {
                                    group.getTrackFormat(it).idAsInt
                                }.contains(indexToFind)
                    }
                Timber.v("Chosen audio ($audioIndex/$indexToFind) track: $chosenTrack")
                chosenTrack?.let {
                    paramsBuilder
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(
                            TrackSelectionOverride(
                                chosenTrack.mediaTrackGroup,
                                0,
                            ),
                        )
                }
                chosenTrack != null
            } else {
                audioIndex == null
            }
        return TrackSelectionResult(paramsBuilder.build(), audioSelected, subtitleSelected)
    }

    /**
     * Maps the server provided index to the track index based on the [PlayerBackend] and other stream information
     */
    private fun calculateIndexToFind(
        serverIndex: Int,
        type: MediaStreamType,
        playerBackend: PlayerBackend,
        embeddedSubtitleCount: Int,
        externalSubtitleCount: Int,
        subtitleIsExternal: Boolean,
        actualEmbeddedCount: Int?,
        source: MediaSourceInfo,
    ): Int =
        when (playerBackend) {
            PlayerBackend.EXO_PLAYER,
            PlayerBackend.UNRECOGNIZED,
            -> {
                serverIndex - externalSubtitleCount + 1
            }

            // TODO MPV could use literal indexes because they are stored in the track format ID
            PlayerBackend.PREFER_MPV,
            PlayerBackend.MPV,
            -> {
                when (type) {
                    MediaStreamType.VIDEO -> {
                        serverIndex - externalSubtitleCount + 1
                    }

                    MediaStreamType.AUDIO -> {
                        val videoStreamsBeforeAudioCount =
                            source.mediaStreams
                                .orEmpty()
                                .indexOfFirst { it.type == MediaStreamType.AUDIO } - externalSubtitleCount
                        serverIndex - externalSubtitleCount - videoStreamsBeforeAudioCount + 1
                    }

                    MediaStreamType.SUBTITLE -> {
                        if (subtitleIsExternal) {
                            // Need to account for the actual embedded count because if the library
                            // disables embedded subtitles, they still exist in the direct played file,
                            // but not included in the MediaStreams list
                            serverIndex + max(actualEmbeddedCount ?: 0, embeddedSubtitleCount) + 1
                        } else {
                            val videoStreamCount = source.videoStreamCount
                            val audioStreamCount = source.audioStreamCount
                            serverIndex - externalSubtitleCount - videoStreamCount - audioStreamCount + 1
                        }
                    }

                    else -> {
                        throw UnsupportedOperationException("Cannot calculate index for $type")
                    }
                }
            }
        }
}

val Format.idAsInt: Int?
    @OptIn(UnstableApi::class)
    get() =
        id?.let {
            if (it.contains(":")) {
                it.split(":").last().toIntOrNull()
            } else {
                it.toIntOrNull()
            }
        }

/**
 * Returns the number of external subtitle streams there are
 */
val MediaSourceInfo.externalSubtitlesCount: Int
    get() =
        mediaStreams
            ?.count { it.type == MediaStreamType.SUBTITLE && it.isExternal } ?: 0

/**
 * Returns the number of embedded subtitle streams there are
 */
val MediaSourceInfo.embeddedSubtitleCount: Int
    get() =
        mediaStreams
            ?.count { it.type == MediaStreamType.SUBTITLE && !it.isExternal } ?: 0

/**
 * Returns the number of video streams there are
 */
val MediaSourceInfo.videoStreamCount: Int
    get() =
        mediaStreams
            ?.count { it.type == MediaStreamType.VIDEO } ?: 0

/**
 * Returns the number of audio streams there are
 */
val MediaSourceInfo.audioStreamCount: Int
    get() =
        mediaStreams
            ?.count { it.type == MediaStreamType.AUDIO } ?: 0

/**
 * Returns the [MediaStream] for the given subtitle index iff it is delivered external
 */
fun MediaSourceInfo.findExternalSubtitle(subtitleIndex: Int?): MediaStream? = mediaStreams?.findExternalSubtitle(subtitleIndex)

fun List<MediaStream>.findExternalSubtitle(subtitleIndex: Int?): MediaStream? =
    subtitleIndex?.let {
        firstOrNull {
            it.type == MediaStreamType.SUBTITLE &&
                (it.deliveryMethod == SubtitleDeliveryMethod.EXTERNAL || it.isExternal) &&
                it.index == subtitleIndex
        }
    }

data class TrackSelectionResult(
    val trackSelectionParameters: TrackSelectionParameters,
    val audioSelected: Boolean,
    val subtitleSelected: Boolean,
) {
    val bothSelected: Boolean = audioSelected && subtitleSelected
}
