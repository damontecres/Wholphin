package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.util.StreamFormatting.mediaStreamDisplayTitle
import org.jellyfin.sdk.model.api.MediaStream

data class SimpleMediaStream(
    val index: Int,
    val streamTitle: String?,
    val displayTitle: String,
) {
    companion object {
        fun from(
            context: Context,
            mediaStream: MediaStream,
            includeFlags: Boolean = true,
        ): SimpleMediaStream =
            SimpleMediaStream(
                index = mediaStream.index,
                streamTitle = mediaStream.title?.takeIf { it.isNotNullOrBlank() },
                displayTitle = mediaStreamDisplayTitle(context, mediaStream, includeFlags),
            )
    }
}

data class SimpleVideoStream(
    val index: Int,
    val hdr: Boolean,
)
