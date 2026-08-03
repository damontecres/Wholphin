package com.github.damontecres.wholphin.ui.playback

import androidx.media3.common.Player
import androidx.media3.common.Tracks
import org.jellyfin.sdk.model.api.MediaSourceInfo
import timber.log.Timber

class TracksChangedListener(
    val player: Player,
    val audioIndex: Int?,
    val subtitleIndex: Int?,
    val source: MediaSourceInfo,
    val onFailure: () -> Unit,
) : Player.Listener {
    override fun onTracksChanged(tracks: Tracks) {
        Timber.v("onTracksChanged: $tracks")
        if (tracks.groups.isNotEmpty()) {
            val result =
                TrackSelectionUtils.createTrackSelections(
                    player.trackSelectionParameters,
                    player.currentTracks,
                    audioIndex,
                    subtitleIndex,
                    source,
                )
            Timber.v("onTracksChanged: %s", result)
            player.removeListener(this)
            if (result.bothSelected) {
                player.trackSelectionParameters =
                    result.trackSelectionParameters
            } else {
                // Fall back to transcoding
                Timber.w(
                    "Failed to select tracks, falling back to transcoding: %s",
                    result,
                )
                onFailure.invoke()
            }
        }
    }
}
