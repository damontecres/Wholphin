package com.github.damontecres.wholphin.util

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.playback.CurrentPlayback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.extensions.inWholeTicks
import timber.log.Timber
import java.util.Timer
import java.util.TimerTask
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Listens to playback and periodically saves playback activity to the server
 */
@OptIn(UnstableApi::class)
class TrackActivityPlaybackListener(
    private val api: ApiClient,
    private val player: Player,
    private val getState: () -> PlaybackItemState?,
) : Player.Listener {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private val task: TimerTask =
        object : TimerTask() {
            override fun run() {
                try {
                    saveActivity(-1L)
                } catch (ex: Exception) {
                    Timber.w(ex, "Exception during track activity timer")
                }
            }
        }

    @Volatile
    private var initialized = false

    fun init() {
        launch("reportPlaybackStart") {
            getState.invoke()?.let { state ->
                Timber.v("reportPlaybackStart for ${state.itemId}")
                api.playStateApi.reportPlaybackStart(
                    PlaybackStartInfo(
                        canSeek = true,
                        itemId = state.itemId,
                        isPaused = withContext(Dispatchers.Main) { !player.isPlaying },
                        playMethod = state.playMethod,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                        isMuted = false,
                        audioStreamIndex = state.audioStreamIndex,
                        subtitleStreamIndex = state.subtitleStreamIndex,
                        playSessionId = state.playSessionId,
                        liveStreamId = state.liveStreamId,
                    ),
                )

                val delay = 5.seconds.inWholeMilliseconds
                // Every x seconds, check if the video is playing
                TIMER.schedule(task, delay, delay)
                initialized = true
            }
        }
    }

    fun release() {
//        player.removeListener(this)
        task.cancel()
        TIMER.purge()
        val position = player.currentPosition.milliseconds
        launch("reportPlaybackStopped") {
            getState.invoke()?.let { state ->
                Timber.v("reportPlaybackStopped for ${state.itemId} at $position")
                api.playStateApi.reportPlaybackStopped(
                    PlaybackStopInfo(
                        itemId = state.itemId,
                        positionTicks = position.inWholeTicks,
                        failed = false,
                        playSessionId = state.playSessionId,
                        liveStreamId = state.liveStreamId,
                    ),
                )
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (initialized) {
            saveActivity(-1)
        } else if (isPlaying) {
            init()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            Timber.v("onPlaybackStateChanged STATE_ENDED")
            saveActivity(player.duration)
        }
    }

    private fun saveActivity(position: Long) {
        launch("saveActivity") {
            getState.invoke()?.let { state ->
                val calcPosition =
                    withContext(Dispatchers.Main) {
                        (if (position >= 0) position else player.currentPosition)
                    }
                if (calcPosition > 0) {
                    val isPaused = withContext(Dispatchers.Main) { !player.isPlaying }
                    Timber.v("saveActivity: itemId=${state.itemId}, pos=$calcPosition")
                    api.playStateApi.reportPlaybackProgress(
                        PlaybackProgressInfo(
                            itemId = state.itemId,
                            positionTicks = calcPosition.milliseconds.inWholeTicks,
                            canSeek = true,
                            isPaused = isPaused,
                            isMuted = false,
                            playMethod = state.playMethod,
                            repeatMode = RepeatMode.REPEAT_NONE,
                            playbackOrder = PlaybackOrder.DEFAULT,
                            audioStreamIndex = state.audioStreamIndex,
                            subtitleStreamIndex = state.subtitleStreamIndex,
                            playSessionId = state.playSessionId,
                            liveStreamId = state.liveStreamId,
                        ),
                    )
                }
            }
        }
    }

    private fun launch(
        name: String,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        coroutineScope.launchIO {
            try {
                block.invoke(this)
            } catch (ex: Exception) {
                Timber.w(ex, "Exception during %s", name)
            }
        }
    }

    companion object {
        private const val TAG = "TrackActivityPlaybackListener"

        private val TIMER by lazy { Timer("$TAG-timer", true) }
    }
}

data class PlaybackItemState(
    val itemId: UUID,
    val playMethod: PlayMethod,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val playSessionId: String? = null,
    val liveStreamId: String? = null,
) {
    constructor(
        playback: CurrentPlayback,
        itemPlayback: ItemPlayback,
    ) : this(
        itemId = itemPlayback.itemId,
        playMethod = playback.playMethod,
        audioStreamIndex = itemPlayback.audioIndex.takeIf { itemPlayback.audioIndexEnabled },
        subtitleStreamIndex = itemPlayback.subtitleIndex.takeIf { itemPlayback.subtitleIndexEnabled },
        playSessionId = playback.playSessionId,
        liveStreamId = playback.liveStreamId,
    )
}
