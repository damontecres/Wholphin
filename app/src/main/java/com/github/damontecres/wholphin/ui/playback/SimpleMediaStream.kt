package com.github.damontecres.wholphin.ui.playback

import android.content.res.Resources
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.util.StreamFormatting.mediaStreamDisplayTitle
import org.jellyfin.sdk.model.api.MediaStream

/**
 * A slimmer [MediaStream] with minimal data for UI purposes
 *
 * This is also a [MediaTrack] provided by the server's media source
 *
 * @see SimpleVideoStream
 */
data class SimpleMediaStream(
    val index: Int,
    override val streamTitle: String?,
    override val displayTitle: String,
) : MediaTrack {
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
