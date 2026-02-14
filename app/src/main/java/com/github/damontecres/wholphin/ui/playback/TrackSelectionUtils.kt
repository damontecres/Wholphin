package com.github.damontecres.wholphin.ui.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
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
import timber.log.Timber

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
        // This function's implementation assumes that indexes for each MediaStream in the MediaSourceInfo
        // will be ordered as: external subtitles, video stream, audio stream, embedded subtitles

        val paramsBuilder = trackSelectionParams.buildUpon()
        val groups = tracks.groups

        val subtitleSelected =
            if (subtitleIndex != null && subtitleIndex >= 0) {
                val subtitleIsExternal = source.findExternalSubtitle(subtitleIndex) != null
                val chosenTrack =
                    if (subtitleIsExternal) {
                        // If external, only one external track should exist, so just find it
                        val group =
                            groups.firstOrNull { group ->
                                group.type == C.TRACK_TYPE_TEXT && group.isSupported &&
                                    (0..<group.mediaTrackGroup.length)
                                        .any {
                                            group.getTrackFormat(0).id?.contains("e:") == true
                                        }
                            }
                        Timber.v(
                            "Chosen external subtitle ($subtitleIndex/) track: %s",
                            group,
                        )
                        group
                    } else {
                        // Not external
                        // Find the first index of a embedded subtitle to use as an offset for desired subtitle index
                        val firstEmbeddedSubtitleIndex =
                            source.mediaStreams
                                .orEmpty()
                                .indexOfFirstOrNull {
                                    it.type == MediaStreamType.SUBTITLE &&
                                        !(it.deliveryMethod == SubtitleDeliveryMethod.EXTERNAL || it.isExternal)
                                }
                        if (firstEmbeddedSubtitleIndex != null) {
                            val indexToFind = subtitleIndex - firstEmbeddedSubtitleIndex
                            val subtitleTracks =
                                groups.filter { group ->
                                    group.type == C.TRACK_TYPE_TEXT && group.isSupported &&
                                        (0..<group.mediaTrackGroup.length)
                                            .none {
                                                group.getTrackFormat(0).id?.contains("e:") == true
                                            }
                                }
                            Timber.v(
                                "Chosen eembedded subtitle ($subtitleIndex/$indexToFind) track: %s",
                                subtitleTracks.getOrNull(indexToFind),
                            )
                            subtitleTracks.getOrNull(indexToFind)
                        } else {
                            null
                        }
                    }
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
                paramsBuilder
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)

                true
            }
        val audioSelected =
            if (audioIndex != null && supportsDirectPlay) {
                // Find the first index of an audio stream to use as an offset for desired index
                val firstAudioIndex =
                    source.mediaStreams
                        .orEmpty()
                        .indexOfFirstOrNull { it.type == MediaStreamType.AUDIO }
                val chosenTrack =
                    if (firstAudioIndex != null) {
                        val indexToFind = audioIndex - firstAudioIndex
                        val audioGroups =
                            groups.filter { group -> group.type == C.TRACK_TYPE_AUDIO && group.isSupported }
                        Timber.v(
                            "Chosen audio ($audioIndex/$indexToFind) track: %s",
                            audioGroups.getOrNull(indexToFind),
                        )
                        audioGroups.getOrNull(indexToFind)
                    } else {
                        null
                    }
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
