package com.github.damontecres.wholphin.ui.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import kotlin.math.max

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
                            group.type == C.TRACK_TYPE_TEXT &&
                                group.trackFormats.any { it.id?.contains(":e:") == true }
                        }
                    } else {
                        val playerIndex =
                            getPlayerIndex(subtitleIndex, source, MediaStreamType.SUBTITLE)
                        if (playerIndex != null) {
                            tracks.groups
                                .filter { group ->
                                    group.type == C.TRACK_TYPE_TEXT && group.isSupported && group.length >= 1
                                }
                                // TODO why are exoplayer tracks out of order sometimes?
                                .sortedBy { it.trackFormats[0].id }
                                .getOrNull(playerIndex)
                        } else {
                            null
                        }
                    }
                chosenTrack?.let {
                    paramsBuilder
                        .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(
                            TrackSelectionOverride(chosenTrack.mediaTrackGroup, 0),
                        )
                }
                chosenTrack != null
            } else {
                paramsBuilder
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                true
            }

        val audioSelected =
            if (audioIndex != null && audioIndex >= 0) {
                val playerIndex = getPlayerIndex(audioIndex, source, MediaStreamType.AUDIO)
                val chosenTrack =
                    if (playerIndex != null) {
                        tracks.groups
                            .filter { group ->
                                group.type == C.TRACK_TYPE_AUDIO && group.isSupported && group.length >= 1
                            }
                            // TODO why are exoplayer tracks out of order sometimes?
                            .sortedBy { it.trackFormats[0].id }
                            .getOrNull(playerIndex)
                    } else {
                        null
                    }
                chosenTrack?.let {
                    paramsBuilder
                        .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(
                            TrackSelectionOverride(chosenTrack.mediaTrackGroup, 0),
                        )
                }
                chosenTrack != null
            } else {
                true
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

            PlayerBackend.EXTERNAL_PLAYER -> {
                throw IllegalStateException("Cannot calculate tracks external playback")
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

/**
 * The result of [TrackSelectionUtils.createTrackSelections]
 */
data class TrackSelectionResult(
    val trackSelectionParameters: TrackSelectionParameters,
    val audioSelected: Boolean,
    val subtitleSelected: Boolean,
) {
    val bothSelected: Boolean = audioSelected && subtitleSelected
}
