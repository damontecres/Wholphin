package com.github.damontecres.wholphin.ui.slideshow

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.PlaybackEffectDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.PlaybackEffect
import com.github.damontecres.wholphin.data.model.VideoFilter
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.PhotoItemFields
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.util.ThrottledLiveData
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import kotlin.properties.Delegates

@HiltViewModel(assistedFactory = SlideshowViewModel.Factory::class)
class SlideshowViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val playerFactory: PlayerFactory,
        private val playbackEffectDao: PlaybackEffectDao,
        private val serverRepository: ServerRepository,
        private val imageUrlService: ImageUrlService,
        private val userPreferencesService: UserPreferencesService,
        @Assisted val slideshowSettings: Destination.Slideshow,
    ) : ViewModel(),
        Player.Listener {
        @AssistedFactory
        interface Factory {
            fun create(slideshow: Destination.Slideshow): SlideshowViewModel
        }

        val player by lazy {
            playerFactory.createVideoPlayer()
        }

        private var saveFilters = true

        /**
         * Whether slideshow mode is on or off
         */
        private val _slideshow = MutableStateFlow<SlideshowState>(SlideshowState(false, false))
        val slideshow: StateFlow<SlideshowState> = _slideshow

        /**
         * Whether the slideshow is actively running meaning slideshow mode is ON and is currently NOT paused
         */
        val slideshowActive = slideshow.map { it.enabled && !it.paused }

        var slideshowDelay by Delegates.notNull<Long>()

        //        private val album = MutableLiveData<BaseItem>()
        private val _pager = MutableLiveData<ApiRequestPager<GetItemsRequest>>()
        val pager: LiveData<List<BaseItem?>> = _pager.map { it }
        val position = MutableLiveData(0)

        private val _image = MutableLiveData<ImageState>()
        val image: LiveData<ImageState> = _image

        val loadingState = MutableLiveData<ImageLoadingState>(ImageLoadingState.Loading)
        private val _imageFilter = MutableLiveData(VideoFilter())
        val imageFilter = ThrottledLiveData(_imageFilter, 500L)

        private var albumImageFilter = VideoFilter()

        init {
            addCloseable {
                player.removeListener(this@SlideshowViewModel)
                player.release()
            }
            player.addListener(this@SlideshowViewModel)
            viewModelScope.launchIO {
                val photoPrefs = userPreferencesService.getCurrent().appPreferences.photoPreferences
                slideshowDelay =
                    photoPrefs.slideshowDuration.takeIf { it >= AppPreference.SlideshowDuration.min }
                        ?: AppPreference.SlideshowDuration.defaultValue
//                val album =
//                    api.userLibraryApi
//                        .getItem(
//                            itemId = slideshowSettings.parentId,
//                        ).content
//                        .let { BaseItem(it, false) }
//                this@SlideshowViewModel.album.setValueOnMain(album)
                val includeItemTypes =
                    if (photoPrefs.slideshowPlayVideos) {
                        listOf(BaseItemKind.PHOTO, BaseItemKind.VIDEO)
                    } else {
                        listOf(BaseItemKind.PHOTO)
                    }
                val request =
                    slideshowSettings.filter.filter.applyTo(
                        GetItemsRequest(
                            parentId = slideshowSettings.parentId,
                            includeItemTypes = includeItemTypes,
                            fields = PhotoItemFields,
                            recursive = true,
                            sortBy = listOf(slideshowSettings.sortAndDirection.sort),
                            sortOrder = listOf(slideshowSettings.sortAndDirection.direction),
                        ),
                    )
                serverRepository.currentUser.value?.let { user ->
                    val filter =
                        playbackEffectDao
                            .getPlaybackEffect(
                                user.rowId,
                                slideshowSettings.parentId,
                                BaseItemKind.PHOTO_ALBUM,
                            )?.videoFilter
                    if (filter != null) {
                        Timber.v("Got filter for album %s", slideshowSettings.parentId)
                        albumImageFilter = filter
                    }
                }
                val pager =
                    ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope)
                        .init(slideshowSettings.index)
                this@SlideshowViewModel._pager.setValueOnMain(pager)
                updatePosition(slideshowSettings.index)?.join()
                if (slideshowSettings.startSlideshow) onMain { startSlideshow() }
            }
        }

        fun nextImage(): Boolean {
            val size = pager.value?.size
            val newPosition = position.value!! + 1
            return if (size != null && newPosition < size) {
                updatePosition(newPosition)
                true
            } else {
                false
            }
        }

        fun previousImage(): Boolean {
            val newPosition = position.value!! - 1
            return if (newPosition >= 0) {
                updatePosition(newPosition)
                true
            } else {
                false
            }
        }

        fun updatePosition(position: Int): Job? =
            _pager.value?.let { pager ->
                viewModelScope.launchIO {
                    try {
                        val image = pager.getBlocking(position)
                        Timber.v("Got image for $position: ${image != null}")
                        if (image != null) {
                            this@SlideshowViewModel.position.setValueOnMain(position)

                            val url =
                                if (image.data.mediaType == MediaType.VIDEO) {
                                    // TODO this assumes direct play
                                    api.videosApi.getVideoStreamUrl(
                                        itemId = image.id,
                                    )
                                } else {
                                    api.libraryApi.getDownloadUrl(image.id)
                                }
                            val chosenStreams =
                                if (image.data.mediaType == MediaType.VIDEO) {
                                    image.data.mediaSources?.firstOrNull()?.let { source ->
                                        val video =
                                            source.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
                                        val audio =
                                            source.mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }
                                        ChosenStreams(
                                            itemPlayback = null,
                                            plc = null,
                                            itemId = image.id,
                                            source = source,
                                            videoStream = video,
                                            audioStream = audio,
                                            subtitleStream = null,
                                            subtitlesDisabled = false,
                                        )
                                    }
                                } else {
                                    null
                                }

                            val imageState =
                                ImageState(
                                    image,
                                    url,
                                    imageUrlService.getItemImageUrl(image, ImageType.THUMB),
                                    chosenStreams,
                                )
                            // reset image filter
                            updateImageFilter(albumImageFilter)
                            if (saveFilters) {
                                viewModelScope.launchIO {
                                    serverRepository.currentUser.value?.let { user ->
                                        val vf =
                                            playbackEffectDao
                                                .getPlaybackEffect(
                                                    user.rowId,
                                                    image.id,
                                                    BaseItemKind.PHOTO,
                                                )
                                        if (vf != null && vf.videoFilter.hasImageFilter()) {
                                            Timber.d(
                                                "Loaded VideoFilter for image ${image.id}",
                                            )
                                            withContext(Dispatchers.Main) {
                                                // Pause throttling so that the image loads with the filter applied immediately
                                                imageFilter.stopThrottling(true)
                                                updateImageFilter(vf.videoFilter)
                                                imageFilter.startThrottling()
                                            }
                                        }
                                        withContext(Dispatchers.Main) {
                                            _image.value = imageState
                                            loadingState.value =
                                                ImageLoadingState.Success(imageState)
                                        }
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    _image.value = imageState
                                    loadingState.value = ImageLoadingState.Success(imageState)
                                }
                            }
                        } else {
                            loadingState.setValueOnMain(ImageLoadingState.Error)
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex)
                        loadingState.setValueOnMain(ImageLoadingState.Error)
                    }
                }
            }

        private var slideshowJob: Job? = null

        fun startSlideshow() {
            _slideshow.update {
                SlideshowState(enabled = true, paused = false)
            }
            if (_image.value
                    ?.image
                    ?.data
                    ?.mediaType != MediaType.VIDEO
            ) {
                pulseSlideshow()
            }
        }

        fun stopSlideshow() {
            slideshowJob?.cancel()
            _slideshow.update {
                SlideshowState(enabled = false, paused = false)
            }
        }

        fun pauseSlideshow() {
            Timber.v("pauseSlideshow")
            _slideshow.update {
                if (it.enabled) {
                    slideshowJob?.cancel()
                    it.copy(paused = true)
                } else {
                    it
                }
            }
        }

        fun unpauseSlideshow() {
            Timber.v("unpauseSlideshow")
            _slideshow.update {
                if (it.enabled) {
                    it.copy(paused = false)
                } else {
                    it
                }
            }
        }

        fun pulseSlideshow() = pulseSlideshow(slideshowDelay)

        fun pulseSlideshow(milliseconds: Long) {
            Timber.v("pulseSlideshow $milliseconds")
            slideshowJob?.cancel()
            slideshowJob =
                viewModelScope
                    .launchIO {
                        delay(milliseconds)
//                        Timber.v("pulseSlideshow after delay")
                        if (slideshowActive.first()) {
                            // Next image or loop to beginning
                            if (!nextImage()) updatePosition(0)
                        }
                    }.apply {
                        invokeOnCompletion { if (it !is CancellationException) pulseSlideshow() }
                    }
        }

        fun updateImageFilter(newFilter: VideoFilter) {
            viewModelScope.launchIO {
                _imageFilter.setValueOnMain(newFilter)
            }
        }

        fun saveImageFilter() {
            image.value?.let {
                viewModelScope.launchIO {
                    val vf = _imageFilter.value
                    if (vf != null) {
                        serverRepository.currentUser.value?.let { user ->
                            playbackEffectDao
                                .insert(
                                    PlaybackEffect(
                                        user.rowId,
                                        it.image.id,
                                        BaseItemKind.PHOTO,
                                        vf,
                                    ),
                                )
                            Timber.d("Saved VideoFilter for image %s", it.image.id)
                            withContext(Dispatchers.Main) {
                                showToast(
                                    context,
                                    "Saved",
                                    Toast.LENGTH_SHORT,
                                )
                            }
                        }
                    }
                }
            }
        }

        fun saveGalleryFilter() {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                val vf = _imageFilter.value
                if (vf != null) {
                    albumImageFilter = vf
                    serverRepository.currentUser.value?.let { user ->
                        playbackEffectDao
                            .insert(
                                PlaybackEffect(
                                    user.rowId,
                                    slideshowSettings.parentId,
                                    BaseItemKind.PHOTO_ALBUM,
                                    vf,
                                ),
                            )
                        Timber.d("Saved VideoFilter for album %s", slideshowSettings.parentId)
                        withContext(Dispatchers.Main) {
                            showToast(
                                context,
                                "Saved",
                                Toast.LENGTH_SHORT,
                            )
                        }
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                pulseSlideshow(slideshowDelay)
            }
        }
    }

interface SlideshowControls {
    fun startSlideshow()

    fun stopSlideshow()
}

sealed class ImageLoadingState {
    data object Loading : ImageLoadingState()

    data object Error : ImageLoadingState()

    data class Success(
        val image: ImageState,
    ) : ImageLoadingState()
}

@Stable
data class ImageState(
    val image: BaseItem,
    val url: String,
    val thumbnailUrl: String?,
    val chosenStreams: ChosenStreams?,
) {
    val id: UUID get() = image.id
}

data class SlideshowState(
    val enabled: Boolean,
    val paused: Boolean,
)
