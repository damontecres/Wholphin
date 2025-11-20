package com.github.damontecres.wholphin.ui.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.preferences.PlayerBackend
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import timber.log.Timber

object TrackSelectionUtils {
    @OptIn(UnstableApi::class)
    fun applyTrackSelections(
        player: Player,
        playerBackend: PlayerBackend,
        supportsDirectPlay: Boolean,
        audioIndex: Int?,
        subtitleIndex: Int?,
        source: MediaSourceInfo,
    ): TrackSelectionResult {
        val videoStreamCount = source.videoStreamCount
        val audioStreamCount = source.audioStreamCount
        val embeddedSubtitleCount = source.embeddedSubtitleCount
        val externalSubtitleCount = source.externalSubtitlesCount

        val paramsBuilder = player.trackSelectionParameters.buildUpon()
        val tracks = player.currentTracks.groups

        val subtitleSelected =
            if (subtitleIndex != null && subtitleIndex >= 0) {
                val subtitleIsExternal = source.findExternalSubtitle(subtitleIndex) != null
                if (subtitleIsExternal || supportsDirectPlay) {
                    val chosenTrack =
                        if (subtitleIsExternal && playerBackend == PlayerBackend.EXO_PLAYER) {
                            tracks.firstOrNull { group ->
                                group.type == C.TRACK_TYPE_TEXT && group.isSupported &&
                                    (0..<group.mediaTrackGroup.length)
                                        .mapNotNull {
                                            group.getTrackFormat(it).id
                                        }.any { it.endsWith("e:$subtitleIndex") }
                            }
                        } else {
                            val indexToFind =
                                calculateIndexToFind(
                                    subtitleIndex,
                                    MediaStreamType.SUBTITLE,
                                    playerBackend,
                                    videoStreamCount,
                                    audioStreamCount,
                                    embeddedSubtitleCount,
                                    externalSubtitleCount,
                                    subtitleIsExternal,
                                )
                            Timber.v("Chosen subtitle ($subtitleIndex/$indexToFind) track")
                            // subtitleIndex - externalSubtitleCount + 1
                            tracks.firstOrNull { group ->
                                group.type == C.TRACK_TYPE_TEXT && group.isSupported &&
                                    (0..<group.mediaTrackGroup.length)
                                        .map {
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
                        videoStreamCount,
                        audioStreamCount,
                        embeddedSubtitleCount,
                        externalSubtitleCount,
                        false,
                    )
                val chosenTrack =
                    tracks.firstOrNull { group ->
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
        if (audioSelected && subtitleSelected) {
            player.trackSelectionParameters = paramsBuilder.build()
        }
        return TrackSelectionResult(audioSelected, subtitleSelected)
    }

    /**
     * Maps the server provided index to the track index based on the [PlayerBackend] and other stream information
     */
    private fun calculateIndexToFind(
        serverIndex: Int,
        type: MediaStreamType,
        playerBackend: PlayerBackend,
        videoStreamCount: Int,
        audioStreamCount: Int,
        embeddedSubtitleCount: Int,
        externalSubtitleCount: Int,
        subtitleIsExternal: Boolean,
    ): Int =
        when (playerBackend) {
            PlayerBackend.EXO_PLAYER,
            PlayerBackend.UNRECOGNIZED,
            -> {
                serverIndex - externalSubtitleCount + 1
            }

            // TODO MPV could use literal indexes because they are stored in the track format ID
            PlayerBackend.MPV -> {
                when (type) {
                    MediaStreamType.VIDEO -> serverIndex - externalSubtitleCount + 1
                    MediaStreamType.AUDIO -> serverIndex - externalSubtitleCount - videoStreamCount + 1
                    MediaStreamType.SUBTITLE -> {
                        if (subtitleIsExternal) {
                            serverIndex + embeddedSubtitleCount + 1
                        } else {
                            serverIndex - externalSubtitleCount - videoStreamCount - audioStreamCount + 1
                        }
                    }
                    else -> throw UnsupportedOperationException("Cannot calculate index for $type")
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
            it.type == MediaStreamType.SUBTITLE && it.deliveryMethod == SubtitleDeliveryMethod.EXTERNAL &&
                it.index == subtitleIndex
        }
    }

data class TrackSelectionResult(
    val audioSelected: Boolean,
    val subtitleSelected: Boolean,
) {
    val bothSelected: Boolean = audioSelected && subtitleSelected
}
