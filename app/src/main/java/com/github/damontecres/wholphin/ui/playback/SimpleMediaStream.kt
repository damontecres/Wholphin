package com.github.damontecres.wholphin.ui.playback

import android.content.res.Resources
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.util.StreamFormatting.mediaStreamDisplayTitle
import org.jellyfin.sdk.model.api.MediaStream

/**
 * A slimmer [MediaStream] with minimal data for UI purposes
 *
 * @see SimpleVideoStream
 */
data class SimpleMediaStream(
    val index: Int,
    val streamTitle: String?,
    val displayTitle: String,
) {
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
