package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.data.model.Chapter
import org.jellyfin.sdk.model.api.TrickplayInfo

data class CurrentMediaInfo(
    val sourceId: String?,
    val videoStream: SimpleVideoStream?,
    val audioStreams: List<SimpleMediaStream>,
    val subtitleStreams: List<SimpleMediaStream>,
    val chapters: List<Chapter>,
    val trickPlayInfo: TrickplayInfo?,
) {
    companion object {
        val EMPTY = CurrentMediaInfo(null, null, listOf(), listOf(), listOf(), null)
    }
}
