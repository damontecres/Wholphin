package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.data.model.Chapter
import org.jellyfin.sdk.model.api.TrickplayInfoDto

data class CurrentMediaInfo(
    val audioStreams: List<AudioStream>,
    val subtitleStreams: List<SubtitleStream>,
    val chapters: List<Chapter>,
    val trickPlayInfo: TrickplayInfoDto?,
) {
    companion object {
        val EMPTY = CurrentMediaInfo(listOf(), listOf(), listOf(), null)
    }
}
