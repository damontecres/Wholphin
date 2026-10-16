package com.github.damontecres.wholphin.util

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.playback.CurrentPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import kotlin.time.Duration
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
    private val coroutineScope = CoroutineScope(SupervisorJob() + WholphinDispatchers.Main)
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

    @Volatile private var released = false
    @Volatile private var starting = false

    fun init() {
        synchronized(task) {
            if (released || starting || initialized) return
            starting = true
        }
        launch("reportPlaybackStart") {
            try {
                getState.invoke()?.let { state ->
                    Timber.v("reportPlaybackStart for ${state.itemId}")
                    api.playStateApi.reportPlaybackStart(
                        PlaybackStartInfo(
                            canSeek = true,
                            itemId = state.itemId,
                            isPaused = withContext(WholphinDispatchers.Main) { !player.isPlaying },
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
                    synchronized(task) {
                        if (!released) {
                            TIMER.schedule(task, delay, delay)
                            initialized = true
                        }
                    }
                }
            } finally {
                starting = false
            }
        }
    }

    fun release() {
        synchronized(task) {
            if (released) return
            released = true
            task.cancel()
            TIMER.purge()
        }
        // Stop owns a snapshot, never a delayed read of a player whose thread was released.
        val state = getState.invoke()
        val position = player.currentPosition.milliseconds
        coroutineScope.cancel()
        launch("reportPlaybackStopped", CoroutineScope(WholphinDispatchers.Main)) {
            state?.let {
                Timber.v("reportPlaybackStopped for ${it.itemId} at $position")
                if (position < Duration.ZERO) {
                    Timber.w("Negative position when reporting playback stopped: %s", position)
                }
                api.playStateApi.reportPlaybackStopped(
                    PlaybackStopInfo(
                        itemId = it.itemId,
                        positionTicks = position.inWholeTicks.takeIf { it >= 0 },
                        failed = false,
                        playSessionId = it.playSessionId,
                        liveStreamId = it.liveStreamId,
                    ),
                )
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (released) return
        if (initialized) {
            saveActivity(-1)
        } else if (isPlaying) {
            init()
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (released) return
        if (playbackState == Player.STATE_ENDED) {
            Timber.v("onPlaybackStateChanged STATE_ENDED")
            saveActivity(player.duration)
        }
    }

    private fun saveActivity(position: Long) {
        if (released) return
        launch("saveActivity") {
            getState.invoke()?.let { state ->
                val calcPosition =
                    withContext(WholphinDispatchers.Main) {
                        (if (position >= 0) position else player.currentPosition)
                    }
                if (calcPosition > 0) {
                    val isPaused = withContext(WholphinDispatchers.Main) { !player.isPlaying }
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
        scope: CoroutineScope = coroutineScope,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        scope.launchIO {
            try {
                block.invoke(this)
            } catch (ex: CancellationException) {
                throw ex
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
    ) : this(
        itemId = playback.item.id,
        playMethod = playback.playMethod,
        audioStreamIndex = playback.audioIndex.takeIf { it >= 0 },
        subtitleStreamIndex = playback.subtitleIndex.takeIf { it >= 0 },
        playSessionId = playback.playSessionId,
        liveStreamId = playback.liveStreamId,
    )
}
