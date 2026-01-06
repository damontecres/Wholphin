package com.github.damontecres.wholphin.ui.slideshow

import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.VideoFilter
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.util.ThrottledLiveData
import com.github.damontecres.wholphin.util.ExceptionHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import java.util.UUID
import kotlin.properties.Delegates

@HiltViewModel(assistedFactory = ImageDetailsViewModel.Factory::class)
class ImageDetailsViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val playerFactory: PlayerFactory,
        @Assisted val parentId: UUID,
        @Assisted val startIndex: Int,
    ) : ViewModel() {
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

        val pager = MutableLiveData<List<BaseItem?>>()
        val position = MutableLiveData(0)

        private val _image = MutableLiveData<BaseItem>()
        val image: LiveData<BaseItem> = _image

        val loadingState = MutableLiveData<ImageLoadingState>(ImageLoadingState.Loading)
        private val _imageFilter = MutableLiveData(VideoFilter())
        val imageFilter = ThrottledLiveData(_imageFilter, 500L)

        private var galleryImageFilter = VideoFilter()

        init {
            viewModelScope.launchIO {
                val album =
                    api.userLibraryApi.getItem(
                        itemId = parentId,
                    )
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
            pager.value?.let { pager ->
                viewModelScope.launchIO {
                    try {
                        val image = pager.getBlocking(position)
                        Log.v(TAG, "Got image for $position: ${image != null}")
                        if (image != null) {
                            this@ImageDetailsViewModel.position.value = position
                            // reset image filter
                            updateImageFilter(galleryImageFilter)
                            if (saveFilters) {
                                viewModelScope.launchIO {
                                    val vf =
                                        playbackEffectsDao
                                            .getPlaybackEffect(server!!.url, image.id, DataType.IMAGE)
                                    if (vf != null && vf.videoFilter.hasImageFilter()) {
                                        Log.d(
                                            TAG,
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
                                        _image.value = image
                                        loadingState.value = ImageLoadingState.Success(image)
                                    }
                                }
                            } else {
                                _image.value = image
                                loadingState.value = ImageLoadingState.Success(image)
                            }
                        } else {
                            loadingState.value = ImageLoadingState.Error
                        }
                    } catch (ex: Exception) {
                        loadingState.value = ImageLoadingState.Error
                    }
                }
            }
        }

        private var slideshowJob: Job? = null

        fun startSlideshow() {
            _slideshow.value = true
            _slideshowPaused.value = false
            if (_image.value?.isImageClip == false) {
                pulseSlideshow()
            }
        }

        fun stopSlideshow() {
            slideshowJob?.cancel()
            _slideshow.value = false
        }

        fun pauseSlideshow() {
            if (_slideshow.value == true) {
                Log.v(TAG, "pauseSlideshow")
                _slideshowPaused.value = true
                slideshowJob?.cancel()
            }
        }

        fun unpauseSlideshow() {
            if (_slideshow.value == true) {
                Log.v(TAG, "unpauseSlideshow")
                _slideshowPaused.value = false
            }
        }

        fun pulseSlideshow() = pulseSlideshow(slideshowDelay)

        fun pulseSlideshow(milliseconds: Long) {
            Log.v(TAG, "pulseSlideshow $milliseconds")
            slideshowJob?.cancel()
            if (slideshow.value!!) {
                slideshowJob =
                    viewModelScope
                        .launchIO {
                            delay(milliseconds)
                            Log.v(TAG, "pulseSlideshow after delay")
                            if (slideshowActive.value == true) {
                                nextImage()
                            }
                        }.apply {
                            invokeOnCompletion { if (it !is CancellationException) pulseSlideshow() }
                        }
            }
        }

        fun updateImageFilter(newFilter: VideoFilter) {
            _imageFilter.value = newFilter
        }

        fun saveImageFilter() {
            image.value?.let {
                viewModelScope.launchIO {
                    val vf = _imageFilter.value
                    if (vf != null) {
                        playbackEffectsDao
                            .insert(PlaybackEffect(server!!.url, it.id, DataType.IMAGE, vf))
                        Log.d(TAG, "Saved VideoFilter for image ${it.id}")
                        withContext(Dispatchers.Main) {
                            Toast
                                .makeText(
                                    context,
                                    "Saved",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    }
                }
            }
        }

        fun saveGalleryFilter() {
            galleryId.value?.let { galleryId ->
                viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                    val vf = _imageFilter.value
                    if (vf != null) {
                        galleryImageFilter = vf
                        playbackEffectsDao
                            .insert(PlaybackEffect(server!!.url, galleryId, DataType.GALLERY, vf))
                        Log.d(TAG, "Saved VideoFilter for gallery $galleryId")
                        withContext(Dispatchers.Main) {
                            Toast
                                .makeText(
                                    context,
                                    "Saved",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    }
                }
            }
        }

        companion object {
            private const val TAG = "ImageDetailsViewModel"
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
        val image: BaseItem,
    ) : ImageLoadingState()
}
