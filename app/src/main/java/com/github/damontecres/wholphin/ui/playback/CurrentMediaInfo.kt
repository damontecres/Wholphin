package com.github.damontecres.wholphin.ui.playback

import androidx.core.net.toUri
import androidx.media3.common.MediaMetadata
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.TrickplayInfo
import org.jellyfin.sdk.model.extensions.ticks

/**
 * Metadata about the currently playing media
 *
 * @see CurrentPlayback
 */
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

/**
 * Gets a sorted list of subtitle streams in a [MediaSourceInfo]
 */
internal fun PlaybackViewModel.getSubtitleStreams(mediaSource: MediaSourceInfo): List<SimpleMediaStream> {
    val subtitleLanguagePreference =
        serverRepository.currentUserDto
            ?.configuration
            ?.subtitleLanguagePreference
    return mediaSource.mediaStreams
        ?.filter { it.type == MediaStreamType.SUBTITLE }
        .let {
            if (subtitleLanguagePreference.isNotNullOrBlank()) {
                it?.sortedByDescending { it.language != null && subtitleLanguagePreference == it.language }
            } else {
                it
            }
        }?.map {
            // TODO should use a string provider instead
            SimpleMediaStream.from(context.resources, it, true)
        }.orEmpty()
}

/**
 * Gets a list of audio streams in a [MediaSourceInfo]
 */
internal fun PlaybackViewModel.getAudioStreams(mediaSource: MediaSourceInfo): List<SimpleMediaStream> =
    mediaSource.mediaStreams
        ?.filter { it.type == MediaStreamType.AUDIO }
        ?.map {
            SimpleMediaStream.from(context.resources, it, true)
        }
//                        ?.sortedWith(compareBy<AudioStream> { it.language }.thenByDescending { it.channels })
        .orEmpty()

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
