package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.ui.joinNotBlank
import com.github.damontecres.wholphin.util.TrackSupport

/**
 * Stores basic information about a track (stream) within media
 */
sealed interface MediaTrack {
    val id: String
    val streamTitle: String?
    val displayTitle: String
}

/**
 * A [MediaTrack] from actual media playback such as from [androidx.media3.common.Tracks]
 */
data class PlaybackMediaTrack(
    override val id: String,
    override val streamTitle: String?,
    override val displayTitle: String,
) : MediaTrack {
    companion object {
        fun from(track: TrackSupport): PlaybackMediaTrack? {
            return track.id?.let {
                val displayTitle =
                    listOf(
                        track.format.codecs,
                        track.format.label,
                        track.format.language,
                    ).joinNotBlank(" - ") ?: "Unknown"
                return PlaybackMediaTrack(track.id, null, displayTitle)
            }
        }
    }
}

/**
 * A [MediaTrack] representing that the track type is disabled
 *
 * @see [TrackIndex.DISABLED]
 */
data object MediaTrackDisabled : MediaTrack {
    override val id: String = TrackIndex.DISABLED.toString()
    override val streamTitle: String?
        get() = null
    override val displayTitle: String
        get() = ""
}

/**
 * A [MediaTrack] representing that the track type is "only forced"
 *
 * @see [TrackIndex.ONLY_FORCED]
 */
data object StreamTrackOnlyForced : MediaTrack {
    override val id: String = TrackIndex.ONLY_FORCED.toString()
    override val streamTitle: String?
        get() = null
    override val displayTitle: String
        get() = ""
}
