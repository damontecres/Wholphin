package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.data.model.Chapter
import org.jellyfin.sdk.model.api.TrickplayInfo

data class CurrentMediaInfo(
    val audioStreams: List<SimpleMediaStream>,
    val subtitleStreams: List<SimpleMediaStream>,
    val chapters: List<Chapter>,
    val trickPlayInfo: TrickplayInfo?,
) {
    companion object {
        val EMPTY = CurrentMediaInfo(listOf(), listOf(), listOf(), null)
    }
}
