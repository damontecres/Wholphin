package com.github.damontecres.wholphin.ui.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod

/**
 * Functions for selecting which audio & subtitle tracks to activate in the [androidx.media3.common.Player]
 */
object TrackSelectionUtils {
    @OptIn(UnstableApi::class)
    fun createTrackSelections(
        trackSelectionParams: TrackSelectionParameters,
        tracks: Tracks,
        audioIndex: Int?,
        subtitleIndex: Int?,
        source: MediaSourceInfo,
    ): TrackSelectionResult {
        val paramsBuilder = trackSelectionParams.buildUpon()
        val subtitleSelected =
            if (subtitleIndex != null && subtitleIndex >= 0) {
                val subtitleIsExternal = source.findExternalSubtitle(subtitleIndex) != null
                val chosenTrack =
                    if (subtitleIsExternal) {
                        tracks.groups.firstOrNull { group ->
                            group.type == C.TRACK_TYPE_TEXT && group.isExternal
                        }
                    } else {
                        val playerIndex =
                            getPlayerIndex(subtitleIndex, source, MediaStreamType.SUBTITLE)
                        if (playerIndex != null) {
                            tracks.groups
                                .filter { group ->
                                    group.type == C.TRACK_TYPE_TEXT &&
                                        group.length >= 1 &&
                                        !group.isExternal
                                }
                                // TODO why are exoplayer tracks out of order sometimes?
                                .sortedById()
                                .getOrNull(playerIndex)
                        } else {
                            null
                        }
                    }
                when {
                    chosenTrack != null && chosenTrack.isSupported -> {
                        paramsBuilder
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                TrackSelectionOverride(chosenTrack.mediaTrackGroup, 0),
                            )
                        TrackSelected.SELECTED
                    }

                    chosenTrack != null && !chosenTrack.isSupported -> {
                        TrackSelected.UNSUPPORTED
                    }

                    else -> {
                        TrackSelected.NOT_FOUND
                    }
                }
            } else {
                paramsBuilder
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                TrackSelected.SELECTED
            }

        val audioSelected =
            if (audioIndex != null && audioIndex >= 0) {
                val playerIndex = getPlayerIndex(audioIndex, source, MediaStreamType.AUDIO)
                val chosenTrack =
                    if (playerIndex != null) {
                        tracks.groups
                            .filter { group ->
                                group.type == C.TRACK_TYPE_AUDIO && group.length >= 1
                            }
                            // TODO why are exoplayer tracks out of order sometimes?
                            .sortedById()
                            .getOrNull(playerIndex)
                    } else {
                        null
                    }
                when {
                    chosenTrack != null && chosenTrack.isSupported -> {
                        paramsBuilder
                            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                            .setOverrideForType(
                                TrackSelectionOverride(chosenTrack.mediaTrackGroup, 0),
                            )
                        TrackSelected.SELECTED
                    }

                    chosenTrack != null && !chosenTrack.isSupported -> {
                        TrackSelected.UNSUPPORTED
                    }

                    else -> {
                        TrackSelected.NOT_FOUND
                    }
                }
            } else {
                TrackSelected.SELECTED
            }
        return TrackSelectionResult(paramsBuilder.build(), audioSelected, subtitleSelected)
    }

    private fun getPlayerIndex(
        serverIndex: Int,
        source: MediaSourceInfo,
        type: MediaStreamType,
    ): Int? {
        val playerIndex =
            source.mediaStreams
                .orEmpty()
                .filter { it.type == type }
                .let {
                    if (type == MediaStreamType.SUBTITLE) {
                        it.filter { it.deliveryMethod == SubtitleDeliveryMethod.EMBED || it.deliveryMethod == SubtitleDeliveryMethod.HLS }
                    } else {
                        it
                    }
                }.indexOfFirstOrNull { it.index == serverIndex }
        return playerIndex
    }

    val Tracks.Group.trackFormats: List<Format>
        @OptIn(UnstableApi::class)
        get() =
            (0..<mediaTrackGroup.length)
                .mapNotNull {
                    getTrackFormat(it)
                }

    private val Tracks.Group.isExternal: Boolean
        get() =
            trackFormats.any {
                it.id?.contains(":e:") == true
            }

    private fun Iterable<Tracks.Group>.sortedById(): List<Tracks.Group> =
        mapNotNull { track ->
            if (track.isExternal) {
                // Should be filtered out before calling this though
                track to listOf(Int.MAX_VALUE, Int.MAX_VALUE)
            } else {
                track.trackFormats[0]
                    .id
                    ?.split(":")
                    ?.map { it.toInt() }
                    ?.let {
                        track to it
                    }
            }
        }.sortedWith(compareBy<Pair<Tracks.Group, List<Int>>> { it.second[0] }.thenBy { it.second[1] })
            .map { it.first }
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

/**
 * The result of [TrackSelectionUtils.createTrackSelections]
 */
data class TrackSelectionResult(
    val trackSelectionParameters: TrackSelectionParameters,
    val audio: TrackSelected,
    val subtitle: TrackSelected,
) {
    val audioSelected: Boolean get() = audio == TrackSelected.SELECTED

    val subtitleSelected: Boolean get() = subtitle == TrackSelected.SELECTED

    val bothSelected: Boolean get() = audioSelected && subtitleSelected
}

enum class TrackSelected {
    SELECTED,
    NOT_FOUND,
    UNSUPPORTED,
}
