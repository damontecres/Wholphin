package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import android.media.audiofx.Visualizer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.main.settings.MoveDirection
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.mayakapps.kache.InMemoryKache
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.lyricsApi
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel(assistedFactory = NowPlayingViewModel.Factory::class)
class NowPlayingViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        val navigationManager: NavigationManager,
        private val musicService: MusicService,
        val userPreferencesService: UserPreferencesService,
    ) : ViewModel(),
        Visualizer.OnDataCaptureListener,
        Player.Listener {
        @AssistedFactory
        interface Factory {
            fun create(): NowPlayingViewModel
        }

        private val visualizer by lazy {
            Visualizer(player.audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
            }
        }

        val controllerViewState =
            ControllerViewState(
                AppPreference.Companion.ControllerTimeout.defaultValue,
                true,
            )

        val state = MutableStateFlow(NowPlayingState(musicService.state.value))
        val player get() = musicService.player

        val viz = MutableStateFlow<IntArray>(IntArray(0))

        private val lyricCache =
            InMemoryKache<UUID, LyricDto>(20) {
                creationScope = CoroutineScope(Dispatchers.IO)
            }

        init {
            player.addListener(this)
            visualizer.setDataCaptureListener(
                this,
                Visualizer.getMaxCaptureRate() / 2,
                true,
                false,
            )
            visualizer.enabled = true

            addCloseable {
                player.removeListener(this)
                visualizer.release()
            }
            viewModelScope.launchDefault {
                musicService.state.collectLatest { musicServiceState ->
                    state.update { it.copy(musicServiceState = musicServiceState) }
                }
            }
            viewModelScope.launchDefault {
                viewModelScope
                    .launchDefault {
                        controllerViewState.observe()
                    }.join()
                controllerViewState.pulseControls()
            }
            playbackLoop()
        }

        fun reportInteraction() {
            controllerViewState.pulseControls()
        }

        private suspend fun getCurrent(): AudioItem? {
            val mediaItem =
                onMain {
                    player.currentMediaItemIndex
                        .takeIf { it in 0..<player.mediaItemCount }
                        ?.let { player.getMediaItemAt(it) }
                }
            return mediaItem?.localConfiguration?.tag as? AudioItem
        }

        private fun playbackLoop() {
            viewModelScope.launchDefault {
                while (isActive) {
                    val position = onMain { player.currentPosition }.milliseconds
//                    Timber.v("playbackLoop: %s", position)
                    getCurrent()?.let { audio ->
//                        Timber.v("Got current %s", audio.id)
                        if (audio.hasLyrics) {
                            val lyrics =
                                lyricCache.getOrPut(audio.id) {
                                    // TODO remote lyrics?
                                    api.lyricsApi.getLyrics(audio.id).content
                                }
                            val lyricIndex =
                                if (lyrics != null) {
                                    val offset = lyrics.metadata.offset?.ticks ?: Duration.ZERO
                                    val lyricPosition = offset + position
                                    lyrics.lyrics
                                        .indexOfLast {
                                            it.start?.ticks?.let { lyricPosition >= it } == true
                                        }.takeIf { it >= 0 }
                                } else {
                                    null
                                }
//                            Timber.v("lyricIndex=$lyricIndex")
                            state.update {
                                it.copy(
                                    lyrics = lyrics,
                                    currentLyricIndex = lyricIndex,
                                )
                            }
                        }
                    }

                    delay(150)
                }
            }
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
            viewModelScope.launchDefault {
                val audio = mediaItem?.localConfiguration?.tag as? AudioItem
                Timber.v("onMediaItemTransition to %s", audio?.id)
                state.update {
                    it.copy(
                        lyrics = null,
                        currentLyricIndex = null,
                    )
                }
                audio?.let { audio ->
                    if (audio.hasLyrics) {
                        val lyrics =
                            lyricCache.getOrPut(audio.id) {
                                // TODO remote lyrics?
                                api.lyricsApi.getLyrics(audio.id).content
                            }
                        Timber.d("Got lyrics for %s: %s", audio.id, lyrics != null)
                        state.update {
                            it.copy(
                                lyrics = lyrics,
                            )
                        }
                    }
                }
            }
        }

        fun moveQueue(
            index: Int,
            direction: MoveDirection,
        ) = viewModelScope.launchDefault { musicService.moveQueue(index, direction) }

        fun play(index: Int) = viewModelScope.launchDefault { musicService.playIndex(index) }

        fun playNext(index: Int) = viewModelScope.launchDefault { musicService.moveQueue(index, 1) }

        fun removeFromQueue(index: Int) = viewModelScope.launchDefault { musicService.removeFromQueue(index) }

        fun stop() {
            viewModelScope.launchDefault {
                musicService.stop()
                navigationManager.goBack()
            }
        }

        override fun onFftDataCapture(
            visualizer: Visualizer,
            fft: ByteArray,
            samplingRate: Int,
        ) {
        }

        override fun onWaveFormDataCapture(
            visualizer: Visualizer,
            waveform: ByteArray,
            samplingRate: Int,
        ) {
            val resolution = 64
            val processed = IntArray(resolution)
            val captureSize =
                Visualizer.getCaptureSizeRange()[1]
//            val groupSize = captureSize / resolution
            val groupSize = captureSize / resolution.toFloat()
            for (i in 0 until resolution) {
//                processed[i] =
//                    waveform
//                        .map { abs(it.toInt()) }
//                        .map { it.toInt() }
//                        .subList(i * groupSize, min((i + 1) * groupSize, waveform.size))
//                        .average()
//                        .toInt()
                val v = abs(waveform[ceil(i * groupSize).toInt()].toInt())
                processed[i] = v * v / (128)
            }
            viz.update { processed }
        }
    }
