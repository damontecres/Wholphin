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
import com.github.damontecres.wholphin.ui.combinePair
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.main.settings.MoveDirection
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.mayakapps.kache.InMemoryKache
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.lyricsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

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
                creationScope = CoroutineScope(WholphinDispatchers.IO)
            }

        init {
            state.update {
                it.copy(currentMediaItem = player.currentMediaItem?.localConfiguration?.tag as? AudioItem)
            }
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
            viewModelScope.launchDefault { backdropService.clearBackdrop() }
            listenForBackdrop()
            listenForLyrics()
        }

        fun reportInteraction() {
            controllerViewState.pulseControls()
        }

        private fun listenForLyrics() {
            viewModelScope.launchDefault {
                state.map { it.currentMediaItem }.distinctUntilChanged().collectLatest { audio ->
                    if (audio != null && audio.hasLyrics) {
                        val lyrics = fetchAndUpdateLyrics(audio)
                        Timber.d("Got lyrics for %s: %s", audio.id, lyrics != null)
                        while (lyrics != null && isActive) {
                            val position = onMain { player.currentPosition }.milliseconds
                            val offset = lyrics.metadata.offset?.ticks ?: Duration.ZERO
                            val lyricPosition = offset + position
                            val lyricIndex =
                                lyrics.lyrics
                                    .indexOfLast {
                                        it.start?.ticks?.let { lyricPosition >= it } == true
                                    }.takeIf { it >= 0 }
                            state.update {
                                it.copy(
                                    lyrics = lyrics,
                                    currentLyricIndex = lyricIndex,
                                )
                            }
                            delay(150.milliseconds)
                        }
                    }
                }
            }
        }

        override fun onMediaItemTransition(
            mediaItem: MediaItem?,
            reason: Int,
        ) {
            val audio = mediaItem?.localConfiguration?.tag as? AudioItem
            Timber.v("onMediaItemTransition to %s", audio?.id)
            state.update {
                it.copy(
                    currentMediaItem = audio,
                    lyrics = null,
                    currentLyricIndex = null,
                )
            }
        }

        private suspend fun fetchAndUpdateLyrics(audio: AudioItem?): LyricDto? =
            audio?.let { audio ->
                if (audio.hasLyrics) {
                    lyricCache.getOrPut(audio.id) {
                        // TODO remote lyrics?
                        try {
                            api.lyricsApi.getLyrics(audio.id).content
                        } catch (ex: CancellationException) {
                            throw ex
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error fetching lyrics for %s", audio.id)
                            null
                        }
                    }
                } else {
                    null
                }
            }

        private fun listenForBackdrop() {
            viewModelScope.launchDefault {
                userPreferencesService.flow
                    .map { it.appPreferences.musicPreferences.showBackdrop }
                    .combinePair(state.map { it.currentMediaItem })
                    .distinctUntilChanged()
                    .collectLatest { (showBackdrop, audio) ->
                        if (showBackdrop && audio != null) {
                            try {
                                val song =
                                    api.userLibraryApi
                                        .getItem(audio.id)
                                        .content
                                val songBackdrops = song.backdropImageUrls()

                                val artistIds = song.artistItems.orEmpty().map { it.id }
                                val artists =
                                    GetItemsRequestHandler
                                        .execute(
                                            api,
                                            GetItemsRequest(
                                                ids = artistIds,
                                                imageTypes = listOf(ImageType.BACKDROP),
                                                limit = 50,
                                                enableImages = true,
                                                enableImageTypes = listOf(ImageType.BACKDROP),
                                            ),
                                        ).content.items
                                val artistBackdrops =
                                    artists.flatMap { artist -> artist.backdropImageUrls() }

                                val list = songBackdrops.shuffled() + artistBackdrops.shuffled()
                                Timber.v("Got %s backdrops for %s", list.size, song.id)
                                if (list.isNotEmpty()) {
                                    val urls = generateSequence { list }.flatten()
                                    urls.forEach {
                                        val (position, duration) = onMain { player.currentPosition to player.duration }
                                        if (duration - position >= 15_000 || state.value.backdropResult == BackdropResult.NONE) {
                                            // Only change if there's backdrop currently, or at 15 seconds left to prevent quick changes
                                            doUpdateBackdrop(audio.id, it)
                                            delay(2.minutes)
                                        }
                                    }
                                } else {
                                    clearBackdrop()
                                }
                            } catch (ex: CancellationException) {
                                throw ex
                            } catch (ex: Exception) {
                                Timber.e(ex, "Error fetching backdrops for %s", audio.id)
                                clearBackdrop()
                            }
                        } else {
                            clearBackdrop()
                        }
                    }
            }
        }

        private fun BaseItemDto.backdropImageUrls() =
            backdropImageTags?.sorted().orEmpty().mapIndexedNotNull { index, tag ->
                imageUrlService.getItemImageUrl(
                    itemId = id,
                    imageType = ImageType.BACKDROP,
                    tag = tag,
                    imageIndex = index,
                )
            }

        private suspend fun doUpdateBackdrop(
            itemId: UUID,
            imageUrl: String,
        ) {
            val (primaryColor, secondaryColor, tertiaryColor) =
                backdropService.extractColorsFromBackdrop(
                    imageUrl,
                )
            val backdropResult =
                BackdropResult(
                    itemId = itemId.toString(),
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
                preferencesDataStore.updateData {
                    prefs
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
