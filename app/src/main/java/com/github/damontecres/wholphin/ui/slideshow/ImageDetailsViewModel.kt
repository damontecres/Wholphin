package com.github.damontecres.wholphin.ui.slideshow

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Stable
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.github.damontecres.wholphin.data.PlaybackEffectDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.PlaybackEffect
import com.github.damontecres.wholphin.data.model.VideoFilter
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.launchIO
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import kotlin.properties.Delegates

@HiltViewModel(assistedFactory = ImageDetailsViewModel.Factory::class)
class ImageDetailsViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val playerFactory: PlayerFactory,
        private val playbackEffectDao: PlaybackEffectDao,
        private val serverRepository: ServerRepository,
        private val imageUrlService: ImageUrlService,
        private val userPreferencesService: UserPreferencesService,
        @Assisted val parentId: UUID,
        @Assisted val startIndex: Int,
    ) : ViewModel(),
        Player.Listener {
        @AssistedFactory
        interface Factory {
            fun create(
                parentId: UUID,
                startIndex: Int,
            ): ImageDetailsViewModel
        }

        val player by lazy {
            playerFactory.createVideoPlayer()
        }

        private var saveFilters = true

        private val _slideshow = MutableLiveData(false)

        /**
         * Whether slideshow mode is on or off
         */
        val slideshow: LiveData<Boolean> = _slideshow
        private val _slideshowPaused = MutableLiveData(false)
        val slideshowPaused: LiveData<Boolean> = _slideshowPaused

        /**
         * Whether the slideshow is actively running meaning slideshow mode is ON and is currently NOT paused
         */
        val slideshowActive =
            slideshow
                .asFlow()
                .combine(slideshowPaused.asFlow()) { slideshow, paused ->
                    slideshow && !paused
                }.asLiveData()

        var slideshowDelay by Delegates.notNull<Long>()

        private val album = MutableLiveData<BaseItem>()
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
                player.removeListener(this@ImageDetailsViewModel)
                player.release()
            }
            player.addListener(this@ImageDetailsViewModel)
            viewModelScope.launchIO {
                slideshowDelay =
                    userPreferencesService
                        .getCurrent()
                        .appPreferences.photoPreferences.slideshowDuration
                val album =
                    api.userLibraryApi
                        .getItem(
                            itemId = parentId,
                        ).content
                        .let { BaseItem(it, false) }
                this@ImageDetailsViewModel.album.setValueOnMain(album)
                val request =
                    GetItemsRequest(
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.PHOTO, BaseItemKind.VIDEO),
                        fields = DefaultItemFields,
                        recursive = true,
                    )
                serverRepository.currentUser.value?.let { user ->
                    val filter =
                        playbackEffectDao
                            .getPlaybackEffect(
                                user.rowId,
                                album.id,
                                BaseItemKind.PHOTO_ALBUM,
                            )?.videoFilter
                    if (filter != null) {
                        Timber.v("Got filter for album %s", album.id)
                        albumImageFilter = filter
                    }
                }
                val pager =
                    ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope)
                        .init(startIndex)
                this@ImageDetailsViewModel._pager.setValueOnMain(pager)
                updatePosition(startIndex)
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

        fun updatePosition(position: Int) {
            _pager.value?.let { pager ->
                viewModelScope.launchIO {
                    try {
                        val image = pager.getBlocking(position)
                        Timber.v("Got image for $position: ${image != null}")
                        if (image != null) {
                            this@ImageDetailsViewModel.position.setValueOnMain(position)

                            val url =
                                if (image.data.mediaType == MediaType.VIDEO) {
                                    // TODO this assumes direct play
                                    api.videosApi.getVideoStreamUrl(
                                        itemId = image.id,
                                    )
                                } else {
                                    api.libraryApi.getDownloadUrl(image.id)
                                }

                            val imageState =
                                ImageState(
                                    image,
                                    url,
                                    imageUrlService.getItemImageUrl(image, ImageType.THUMB),
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
        }

        private var slideshowJob: Job? = null

        fun startSlideshow() {
            _slideshow.value = true
            _slideshowPaused.value = false
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
            _slideshow.value = false
        }

        fun pauseSlideshow() {
            if (_slideshow.value == true) {
                Timber.v("pauseSlideshow")
                _slideshowPaused.value = true
                slideshowJob?.cancel()
            }
        }

        fun unpauseSlideshow() {
            if (_slideshow.value == true) {
                Timber.v("unpauseSlideshow")
                _slideshowPaused.value = false
            }
        }

        fun pulseSlideshow() = pulseSlideshow(slideshowDelay)

        fun pulseSlideshow(milliseconds: Long) {
            Timber.v("pulseSlideshow $milliseconds")
            slideshowJob?.cancel()
            if (slideshow.value!!) {
                slideshowJob =
                    viewModelScope
                        .launchIO {
                            delay(milliseconds)
                            Timber.v("pulseSlideshow after delay")
                            if (slideshowActive.value == true) {
                                nextImage()
                            }
                        }.apply {
                            invokeOnCompletion { if (it !is CancellationException) pulseSlideshow() }
                        }
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
            album.value?.let { album ->
                viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                    val vf = _imageFilter.value
                    if (vf != null) {
                        albumImageFilter = vf
                        serverRepository.currentUser.value?.let { user ->
                            playbackEffectDao
                                .insert(
                                    PlaybackEffect(
                                        user.rowId,
                                        album.id,
                                        BaseItemKind.PHOTO_ALBUM,
                                        vf,
                                    ),
                                )
                            Timber.d("Saved VideoFilter for album %s", album.id)
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
) {
    val id: UUID get() = image.id
}
