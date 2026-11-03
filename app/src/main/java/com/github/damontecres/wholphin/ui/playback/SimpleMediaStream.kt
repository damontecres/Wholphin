package com.github.damontecres.wholphin.ui.playback

import android.content.res.Resources
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.joinNotBlank
import com.github.damontecres.wholphin.ui.util.StreamFormatting.mediaStreamDisplayTitle
import com.github.damontecres.wholphin.util.TrackSupport
import org.jellyfin.sdk.model.api.MediaStream

sealed interface StreamTrack {
    val id: String
    val streamTitle: String?
    val displayTitle: String
}

/**
 * A slimmer [MediaStream] with minimal data for UI purposes
 *
 * @see SimpleVideoStream
 */
data class SimpleMediaStream(
    val index: Int,
    override val streamTitle: String?,
    override val displayTitle: String,
) : StreamTrack {
    override val id: String get() = index.toString()

    companion object {
        fun from(
            resources: Resources,
            mediaStream: MediaStream,
            includeFlags: Boolean = true,
        ): SimpleMediaStream =
            SimpleMediaStream(
                index = mediaStream.index,
                streamTitle = mediaStream.title?.takeIf { it.isNotNullOrBlank() },
                displayTitle = mediaStreamDisplayTitle(resources, mediaStream, includeFlags),
            )
    }
}

data class SimpleVideoStream(
    val index: Int,
    val hdr: Boolean,
    val is4k: Boolean,
)

data class PlayerStream(
    override val id: String,
    override val streamTitle: String?,
    override val displayTitle: String,
) : StreamTrack {
    companion object {
        fun from(track: TrackSupport): PlayerStream? {
            return track.id?.let {
                val displayTitle =
                    listOf(
                        track.format.codecs,
                        track.format.label,
                        track.format.language,
                    ).joinNotBlank(" - ") ?: "Unknown"
                return PlayerStream(track.id, null, displayTitle)
            }
        }
    }
}

data object TrackDisabled : StreamTrack {
    override val id: String
        get() = TrackIndex.DISABLED.toString()
    override val streamTitle: String?
        get() = null
    override val displayTitle: String
        get() = ""
}

data object TrackOnlyForced : StreamTrack {
    override val id: String
        get() = TrackIndex.ONLY_FORCED.toString()
    override val streamTitle: String?
        get() = null
    override val displayTitle: String
        get() = ""
}
