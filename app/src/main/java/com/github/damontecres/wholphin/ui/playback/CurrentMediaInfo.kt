package com.github.damontecres.wholphin.ui.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.TrickplayInfo
import org.jellyfin.sdk.model.extensions.ticks

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

fun BaseItem.toMediaMetadata(imageUrl: String?): MediaMetadata =
    MediaMetadata
        .Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setReleaseYear(data.productionYear)
        .setDescription(data.overview)
        .setArtworkUri(imageUrl?.toUri())
        .setDurationMs(data.runTimeTicks?.ticks?.inWholeMilliseconds)
        .setMediaType(
            when (type) {
                BaseItemKind.MOVIE -> MediaMetadata.MEDIA_TYPE_MOVIE
                BaseItemKind.EPISODE -> MediaMetadata.MEDIA_TYPE_TV_SHOW
                BaseItemKind.VIDEO -> MediaMetadata.MEDIA_TYPE_VIDEO
                BaseItemKind.TV_CHANNEL, BaseItemKind.CHANNEL, BaseItemKind.LIVE_TV_CHANNEL -> MediaMetadata.MEDIA_TYPE_TV_CHANNEL
                else -> MediaMetadata.MEDIA_TYPE_VIDEO
            },
        ).build()
