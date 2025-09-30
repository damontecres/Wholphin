package com.github.damontecres.dolphin.util.profile

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import com.github.damontecres.dolphin.ui.indexOfFirstOrNull
import com.github.damontecres.dolphin.util.TrackSupportReason
import com.github.damontecres.dolphin.util.TrackType
import com.github.damontecres.dolphin.util.checkForSupport
import timber.log.Timber
import java.util.Locale

class PlaybackListener : Player.Listener {
    var mediaIndexSubtitlesActivated = -1
    var currentPlaylistIndex = -2

    override fun onCues(cueGroup: CueGroup) {
//                    val cues =
//                        cueGroup.cues
//                            .mapNotNull { it.text }
//                            .joinToString("\n")
//                    Log.v(TAG, "onCues: \n$cues")
        val subtitles = cueGroup.cues.ifEmpty { null }
    }

    override fun onTracksChanged(tracks: Tracks) {
        val trackInfo = checkForSupport(tracks)
        Timber.v("Track info: $trackInfo")
        val audioTracks =
            trackInfo
                .filter { it.type == TrackType.AUDIO && it.supported == TrackSupportReason.HANDLED }
        val audioIndex = audioTracks.indexOfFirstOrNull { it.selected }
        val audioOptions =
            audioTracks.map { it.labels.joinToString(", ").ifBlank { "Default" } }
        val captions =
            trackInfo.filter { it.type == TrackType.TEXT && it.supported == TrackSupportReason.HANDLED }

        // TODO user preference
        val captionsByDefault = true
        if (captionsByDefault && captions.isNotEmpty()) {
            // TODO Captions will be empty when transitioning to new media item
            // Only want to activate subtitles once in case the user turns them off
            mediaIndexSubtitlesActivated = currentPlaylistIndex
            val languageCode = Locale.getDefault().language
            captions.indexOfFirstOrNull { it.format.language == languageCode }?.let {
                Timber.v("Found default subtitle track for $languageCode: $it")
//                if (toggleSubtitles(player, null, it)) {
//                    subtitleIndex = it
//                }
            }
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
    }

    override fun onPlayerError(error: PlaybackException) {
        Timber.e(error, "Playback error")
    }
}
