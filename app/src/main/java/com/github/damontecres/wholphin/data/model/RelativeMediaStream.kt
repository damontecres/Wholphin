package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType

/**
 * A [MediaStream] with its relative track index, its position within a specific stream type
 */
data class RelativeMediaStream(
    val relativeIndex: Int,
    val stream: MediaStream,
) {
    val type: MediaStreamType get() = stream.type
    val language: String? get() = stream.language
    val isForced: Boolean get() = stream.isForced
    val isExternal: Boolean get() = stream.isExternal
    val isDefault: Boolean get() = stream.isDefault
    val isHearingImpaired: Boolean get() = stream.isHearingImpaired

    companion object {
        fun getRelativeIndex(
            index: Int,
            streams: List<MediaStream>,
        ): Int? {
            val stream = streams.firstOrNull { it.index == index } ?: return null
            return getRelativeIndex(stream, streams)
        }

        fun getRelativeIndex(
            stream: MediaStream,
            streams: List<MediaStream>,
        ): Int? =
            streams
                .filter { it.type == stream.type && it.isExternal == stream.isExternal }
                .sortedBy { it.index }
                .indexOfFirstOrNull { it.index == stream.index }

        fun from(
            index: Int,
            streams: List<MediaStream>,
        ): RelativeMediaStream? {
            val stream = streams.firstOrNull { it.index == index } ?: return null
            val relativeIndex = getRelativeIndex(stream, streams) ?: return null
            return RelativeMediaStream(relativeIndex, stream)
        }
    }
}

/**
 * Maps a list of [MediaStream] into [RelativeMediaStream]
 */
fun List<MediaStream>.asRelative(): List<RelativeMediaStream> {
    val map = this.groupBy { Pair(it.type, it.isExternal) }
    return map.values
        .flatMap { typedStreams ->
            typedStreams.sortedBy { it.index }.mapIndexed { index, stream ->
                RelativeMediaStream(index, stream)
            }
        }.sortedBy { it.stream.index }
}
