package com.github.damontecres.wholphin.ui.playback

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication

data class SubtitleStream(
    val index: Int,
    val language: String?,
    val title: String?,
    val codec: String?,
    val codecTag: String?,
    val external: Boolean,
    val forced: Boolean,
    val default: Boolean,
    val displayTitle: String?,
) {
    val displayName: String
        get() =
            displayTitle ?: listOfNotNull(
                language,
                title,
                codec,
            ).joinToString(" - ")
                .ifBlank { WholphinApplication.instance.getString(R.string.unknown) }
}

data class AudioStream(
    val index: Int,
    val language: String?,
    val title: String?,
    val codec: String?,
    val codecTag: String?,
    val channels: Int?,
    val channelLayout: String?,
) {
    val displayName: String
        get() =
            listOfNotNull(
                language,
                title,
                codec,
                channelLayout?.ifBlank { null } ?: channels?.let { "$it ch" },
            ).joinToString(" - ").ifBlank { "Unknown" }
}
