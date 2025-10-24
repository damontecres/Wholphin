package com.github.damontecres.wholphin.util

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.ui.playback.CurrentPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.extensions.inWholeTicks
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Listens to playback and periodically saves playback activity to the server
 */
@OptIn(UnstableApi::class)
class TrackActivityPlaybackListener(
    private val api: ApiClient,
    private val player: Player,
    var playback: CurrentPlayback,
    var itemPlayback: ItemPlayback,
) : Player.Listener {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val task: TimerTask

    init {
        coroutineScope.launch(Dispatchers.IO + ExceptionHandler()) {
            api.playStateApi.reportPlaybackStart(
                PlaybackStartInfo(
                    canSeek = true,
                    itemId = itemPlayback.itemId,
                    isPaused = withContext(Dispatchers.Main) { !player.isPlaying },
                    playMethod = playback.playMethod,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                    isMuted = false,
                    audioStreamIndex = itemPlayback.audioIndex.takeIf { itemPlayback.audioIndexEnabled },
                    subtitleStreamIndex = itemPlayback.subtitleIndex.takeIf { itemPlayback.subtitleIndexEnabled },
                    liveStreamId = playback.liveStreamId,
                ),
            )
        }
        val delay = 5.seconds.inWholeMilliseconds
        // Every x seconds, check if the video is playing
        task =
            object : TimerTask() {
                override fun run() {
                    try {
                        saveActivity(-1L)
                    } catch (ex: Exception) {
                        Timber.Forest.w(ex, "Exception during track activity timer")
                    }
                }
            }
        TIMER.schedule(task, delay, delay)
    }

    fun release() {
        task.cancel()
        TIMER.purge()
        val position = player.currentPosition.milliseconds.inWholeTicks
        coroutineScope.launch(Dispatchers.IO + ExceptionHandler()) {
            api.playStateApi.reportPlaybackStopped(
                PlaybackStopInfo(
                    itemId = itemPlayback.itemId,
                    positionTicks = position,
                    failed = false,
                    playSessionId = playback.playSessionId,
                    liveStreamId = playback.liveStreamId,
                ),
            )
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        saveActivity(-1)
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            Timber.v("onPlaybackStateChanged STATE_ENDED")
            saveActivity(player.duration)
        }
    }

    private fun saveActivity(position: Long) {
        coroutineScope.launch(Dispatchers.IO + ExceptionHandler()) {
            val calcPosition =
                withContext(Dispatchers.Main) {
                    (if (position >= 0) position else player.currentPosition).milliseconds
                }
//            Timber.v("saveActivity: itemId=${itemPlayback.itemId}, pos=$calcPosition")
            api.playStateApi.reportPlaybackProgress(
                PlaybackProgressInfo(
                    itemId = itemPlayback.itemId,
                    positionTicks = calcPosition.inWholeTicks,
                    canSeek = true,
                    isPaused = withContext(Dispatchers.Main) { !player.isPlaying },
                    isMuted = false,
                    playMethod = playback.playMethod,
                    repeatMode = RepeatMode.REPEAT_NONE,
                    playbackOrder = PlaybackOrder.DEFAULT,
                    playSessionId = playback.playSessionId,
                    audioStreamIndex = itemPlayback.audioIndex.takeIf { itemPlayback.audioIndexEnabled },
                    subtitleStreamIndex = itemPlayback.subtitleIndex.takeIf { itemPlayback.subtitleIndexEnabled },
                    liveStreamId = playback.liveStreamId,
                ),
            )
        }
    }

    companion object {
        private const val TAG = "TrackActivityPlaybackListener"

        private val TIMER by lazy { Timer("$TAG-timer", true) }
    }
}
