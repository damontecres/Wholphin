package com.github.damontecres.wholphin.ui.detail.music

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import androidx.core.content.ContextCompat
import androidx.datastore.core.DataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.updateMusicPreferences
import com.github.damontecres.wholphin.services.BackdropResult
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.NowPlayingStatus
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.lyricsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@UnstableApi
@HiltViewModel(assistedFactory = NowPlayingViewModel.Factory::class)
class NowPlayingViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val musicService: MusicService,
        private val backdropService: BackdropService,
        private val imageUrlService: ImageUrlService,
        private val preferencesDataStore: DataStore<AppPreferences>,
        val navigationManager: NavigationManager,
        val userPreferencesService: UserPreferencesService,
    ) : ViewModel(),
        Visualizer.OnDataCaptureListener,
        Player.Listener {
        @AssistedFactory
        interface Factory {
            fun create(): NowPlayingViewModel
        }

        private val visualizerMutex = Mutex()
        private var visualizer: Visualizer? = null

        val controllerViewState =
            ControllerViewState(
                AppPreference.ControllerTimeout.defaultValue,
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
            addCloseable {
                player.removeListener(this)
                visualizer?.release()
            }
            val visualizerPermissions =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO,
                ) == PackageManager.PERMISSION_GRANTED
            startVisualizer(visualizerPermissions, false)
            viewModelScope.launchDefault {
                musicService.state.collectLatest { musicServiceState ->
                    if (musicServiceState.status != NowPlayingStatus.IDLE) {
                        visualizer?.enabled = musicServiceState.status == NowPlayingStatus.PLAYING
                    }

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
            viewModelScope.launchDefault {
                backdropService.clearBackdrop()
                updateBackdrop(getCurrent())
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
            val audio = mediaItem?.localConfiguration?.tag as? AudioItem
            Timber.v("onMediaItemTransition to %s", audio?.id)
            updateBackdrop(audio)
            viewModelScope.launchDefault {
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

        private var backDropJob: Job? = null

        private fun updateBackdrop(audio: AudioItem?) {
            backDropJob?.cancel()
            backDropJob =
                viewModelScope.launchDefault {
                    val showBackdrop =
                        userPreferencesService
                            .getCurrent()
                            .appPreferences.musicPreferences.showBackdrop
                    if (showBackdrop) {
                        var backdropItem: BaseItem? = null
                        try {
                            if (audio?.artistId != null) {
                                api.userLibraryApi.getItem(audio.artistId).content.let {
                                    if (it.backdropImageTags?.isNotEmpty() == true) {
                                        backdropItem = BaseItem(it, false)
                                    }
                                }
                            }
                            if (backdropItem == null && audio?.albumId != null) {
                                api.userLibraryApi.getItem(audio.albumId).content.let {
                                    backdropItem =
                                        getBackdropItemForAlbum(api, BaseItem(it, false))
                                }
                            }
                            if (backdropItem != null) {
                                doUpdateBackdrop(backdropItem)
                            } else {
                                doUpdateBackdropRandom()
                            }
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error fetching backdrop")
                            doUpdateBackdropRandom()
                        }
                        delay(60.seconds)
                        doUpdateBackdropRandom()
                    }
                }
        }

        private suspend fun doUpdateBackdropRandom() {
            val randomArtist =
                api.itemsApi
                    .getItems(
                        recursive = true,
                        imageTypes = listOf(ImageType.BACKDROP),
                        includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
                        sortBy = listOf(ItemSortBy.RANDOM),
                        limit = 1,
                    ).content.items
                    .firstOrNull()
            if (randomArtist != null) {
                doUpdateBackdrop(BaseItem(randomArtist))
            } else {
                clearBackdrop()
            }
        }

        private suspend fun doUpdateBackdrop(item: BaseItem) {
            val imageUrl = imageUrlService.getItemImageUrl(item, ImageType.BACKDROP)
            val (primaryColor, secondaryColor, tertiaryColor) =
                backdropService.extractColorsFromBackdrop(
                    imageUrl,
                )
            val backdropResult =
                BackdropResult(
                    itemId = item.id.toString(),
                    imageUrl = imageUrl,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor,
                    tertiaryColor = tertiaryColor,
                )
            state.update { it.copy(backdropResult = backdropResult) }
        }

        private fun clearBackdrop() {
            state.update { it.copy(backdropResult = BackdropResult.NONE) }
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
            val resolution = 96
            val captureSize =
                Visualizer.getCaptureSizeRange()[1]
            val groupSize = (captureSize / resolution.toFloat()).toInt()
            val processed =
                waveform
                    .toList()
                    .chunked(groupSize)
                    .map { it.average().toInt() + 128 }
                    .toIntArray()
            viz.update { processed }
        }

        fun updatePreferences(prefs: AppPreferences) {
            viewModelScope.launchDefault {
                var backdropChanged = false
                preferencesDataStore.updateData {
                    backdropChanged =
                        it.musicPreferences.showBackdrop != prefs.musicPreferences.showBackdrop
                    prefs
                }
                if (backdropChanged) {
                    if (prefs.musicPreferences.showBackdrop) {
                        updateBackdrop(getCurrent())
                    } else {
                        clearBackdrop()
                    }
                }
            }
        }

        private fun initVisualizer() {
            viewModelScope.launchDefault {
                visualizerMutex.withLock {
                    val prefs = preferencesDataStore.data.first()
                    if (visualizer == null &&
                        state.value.visualizerPermissions &&
                        prefs.musicPreferences.showVisualizer
                    ) {
                        Timber.v("Creating visualizer")
                        visualizer =
                            Visualizer(onMain { player.audioSessionId }).apply {
                                captureSize = Visualizer.getCaptureSizeRange()[1]
                                setDataCaptureListener(
                                    this@NowPlayingViewModel,
                                    Visualizer.getMaxCaptureRate() / 3,
                                    true,
                                    false,
                                )
                                enabled = true
                            }
                    }
                }
            }
        }

        fun startVisualizer(
            permissionGranted: Boolean,
            updatePreferences: Boolean,
        ) {
            Timber.v("startVisualizer: permissionGranted=%s", permissionGranted)
            state.update {
                it.copy(
                    visualizerPermissions = permissionGranted,
                )
            }
            viewModelScope.launchDefault {
                if (updatePreferences || !permissionGranted) {
                    preferencesDataStore.updateData {
                        it.updateMusicPreferences { showVisualizer = permissionGranted }
                    }
                }
                initVisualizer()
            }
        }
    }
