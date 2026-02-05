package com.github.damontecres.wholphin.ui.slideshow

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.VideoFilter
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.findActivity
import com.github.damontecres.wholphin.ui.keepScreenOn
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.isDirectionalDpad
import com.github.damontecres.wholphin.ui.playback.isDpad
import com.github.damontecres.wholphin.ui.playback.isEnterKey
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber
import kotlin.math.abs

private const val TAG = "ImagePage"
private const val DEBUG = false

@SuppressLint("ConfigurationScreenWidthHeight")
@OptIn(UnstableApi::class)
@Composable
fun SlideshowPage(
    slideshow: Destination.Slideshow,
    modifier: Modifier = Modifier,
    viewModel: SlideshowViewModel =
        hiltViewModel<SlideshowViewModel, SlideshowViewModel.Factory>(
            creationCallback = {
                it.create(slideshow)
            },
        ),
) {
    val context = LocalContext.current

    val loadingState by viewModel.loadingState.observeAsState(ImageLoadingState.Loading)
    val imageFilter by viewModel.imageFilter.observeAsState(VideoFilter())
    val position by viewModel.position.observeAsState(0)
    val pager by viewModel.pager.observeAsState()
    val imageState by viewModel.image.observeAsState()

    var zoomFactor by rememberSaveable { mutableFloatStateOf(1f) }
    val isZoomed = zoomFactor * 100 > 102
    var rotation by rememberSaveable { mutableFloatStateOf(0f) }
    var showOverlay by rememberSaveable { mutableStateOf(false) }
    var showFilterDialog by rememberSaveable { mutableStateOf(false) }
    var panX by rememberSaveable { mutableFloatStateOf(0f) }
    var panY by rememberSaveable { mutableFloatStateOf(0f) }

    val slideshowControls =
        object : SlideshowControls {
            override fun startSlideshow() {
                showOverlay = false
                viewModel.startSlideshow()
            }

            override fun stopSlideshow() {
                viewModel.stopSlideshow()
            }
        }

    val rotateAnimation: Float by animateFloatAsState(
        targetValue = rotation,
        label = "image_rotation",
    )
    val zoomAnimation: Float by animateFloatAsState(
        targetValue = zoomFactor,
        label = "image_zoom",
    )
    val panXAnimation: Float by animateFloatAsState(
        targetValue = panX,
        label = "image_panX",
    )
    val panYAnimation: Float by animateFloatAsState(
        targetValue = panY,
        label = "image_panY",
    )

    val slideshowState by viewModel.slideshow.collectAsState()
    val slideshowActive by viewModel.slideshowActive.collectAsState(false)

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.tryRequestFocus()
    }

    val density = LocalDensity.current
    val screenHeight = LocalWindowInfo.current.containerSize.height
    val screenWidth = LocalWindowInfo.current.containerSize.width

    val maxPanX = screenWidth * .75f
    val maxPanY = screenHeight * .75f

    fun reset(resetRotate: Boolean) {
        zoomFactor = 1f
        panX = 0f
        panY = 0f
        if (resetRotate) rotation = 0f
    }

    fun pan(
        xFactor: Int,
        yFactor: Int,
    ) {
        if (xFactor != 0) {
            panX = (panX + with(density) { xFactor.dp.toPx() }).coerceIn(-maxPanX, maxPanX)
        }
        if (yFactor != 0) {
            panY = (panY + with(density) { yFactor.dp.toPx() }).coerceIn(-maxPanY, maxPanY)
        }
    }

    fun zoom(factor: Float) {
        if (factor < 0) {
            val diffFactor = factor / (zoomFactor - 1f)
            // zooming out
            val panXDiff = abs(panX * diffFactor)
            val panYDiff = abs(panY * diffFactor)
            if (DEBUG) {
                Timber.d(
                    "zoomFactor=$zoomFactor, factor=$factor, panX=$panX, panY=$panY, panXDiff=$panXDiff, panYDiff=$panYDiff",
                )
            }
            if (panX > 0f) {
                panX -= panXDiff
            } else if (panX < 0f) {
                panX += panXDiff
            }
            if (panY > 0f) {
                panY -= panYDiff
            } else if (panY < 0f) {
                panY += panYDiff
            }
        }
        zoomFactor = (zoomFactor + factor).coerceIn(1f, 5f)
        if (!isZoomed) {
            // Always reset if not zoomed
            panX = 0f
            panY = 0f
        }
    }

    LaunchedEffect(imageState) {
        reset(true)
    }
    val player = viewModel.player
    val presentationState = rememberPresentationState(player)
    LaunchedEffect(slideshowActive) {
        player.repeatMode =
            if (slideshowState.enabled) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
        context.findActivity()?.keepScreenOn(slideshowActive)
    }
    DisposableEffect(Unit) {
        onDispose {
            context.findActivity()?.keepScreenOn(false)
        }
    }

    var longPressing by remember { mutableStateOf(false) }

    val contentModifier =
        Modifier
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = {
                    showOverlay = !showOverlay
                },
            )

    Box(
        modifier =
            modifier
                .background(Color.Black)
                .focusRequester(focusRequester)
                .focusable()
                .onKeyEvent {
                    val isOverlayShowing = showOverlay || showFilterDialog
                    var result = false
                    if (!isOverlayShowing) {
                        if (longPressing && it.type == KeyEventType.KeyUp) {
                            // User stopped long pressing, so cancel the zooming action, but still consume the event so it doesn't move the image
                            longPressing = false
                            return@onKeyEvent true
                        }
                        longPressing =
                            it.nativeKeyEvent.isLongPress ||
                            it.nativeKeyEvent.repeatCount > 0
                        if (longPressing) {
                            when (it.key) {
                                Key.DirectionUp -> zoom(.05f)
                                Key.DirectionDown -> zoom(-.05f)

                                // These work, but feel awkward because Up/Down zoom, so you can't long press them to pan
                                // Key.DirectionLeft -> panX += with(density) { 15.dp.toPx() }
                                // Key.DirectionRight -> panX -= with(density) { 15.dp.toPx() }
                            }
                            return@onKeyEvent true
                        }
                    }
                    if (it.type != KeyEventType.KeyUp) {
                        result = false
                    } else if (!isOverlayShowing && isZoomed && isDirectionalDpad(it)) {
                        // Image is zoomed in
                        when (it.key) {
                            Key.DirectionLeft -> pan(30, 0)
                            Key.DirectionRight -> pan(-30, 0)
                            Key.DirectionUp -> pan(0, 30)
                            Key.DirectionDown -> pan(0, -30)
                        }
                        result = true
                    } else if (!isOverlayShowing && isZoomed && it.key == Key.Back) {
                        reset(false)
                        result = true
                    } else if (!isOverlayShowing && (it.key == Key.DirectionLeft || it.key == Key.DirectionRight)) {
                        when (it.key) {
                            Key.DirectionLeft, Key.DirectionUpLeft, Key.DirectionDownLeft -> {
                                if (!viewModel.previousImage()) {
                                    Toast
                                        .makeText(
                                            context,
                                            R.string.slideshow_at_beginning,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }

                            Key.DirectionRight, Key.DirectionUpRight, Key.DirectionDownRight -> {
                                if (!viewModel.nextImage()) {
                                    Toast
                                        .makeText(
                                            context,
                                            R.string.no_more_images,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }
                        }
                    } else if (isOverlayShowing && it.key == Key.Back) {
                        showOverlay = false
                        viewModel.unpauseSlideshow()
                        result = true
                    } else if (!isOverlayShowing && (isDpad(it) || isEnterKey(it))) {
                        showOverlay = true
                        viewModel.pauseSlideshow()
                        result = true
                    }
                    if (result) {
                        // Handled the key, so reset the slideshow timer
                        viewModel.pulseSlideshow()
                    }
                    result
                },
    ) {
        when (loadingState) {
            ImageLoadingState.Error -> {
                ErrorMessage("Error loading image", null)
            }

            ImageLoadingState.Loading -> {
                LoadingPage()
            }

            is ImageLoadingState.Success -> {
                imageState?.let { imageState ->
                    if (imageState.image.data.mediaType == MediaType.VIDEO) {
                        LaunchedEffect(imageState.id) {
                            val mediaItem =
                                MediaItem
                                    .Builder()
                                    .setUri(imageState.url)
                                    .build()
                            player.setMediaItem(mediaItem)
                            player.repeatMode =
                                if (slideshowState.enabled) {
                                    Player.REPEAT_MODE_OFF
                                } else {
                                    Player.REPEAT_MODE_ONE
                                }
                            player.prepare()
                            player.play()
                            viewModel.pulseSlideshow(Long.MAX_VALUE)
                        }
                        LifecycleStartEffect(Unit) {
                            onStopOrDispose {
                                player.stop()
                            }
                        }
                        val contentScale = ContentScale.Fit
                        val scaledModifier =
                            contentModifier.resizeWithContentScale(
                                contentScale,
                                presentationState.videoSizeDp,
                            )
                        PlayerSurface(
                            player = player,
                            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                            modifier =
                                scaledModifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoomAnimation
                                        scaleY = zoomAnimation
                                        translationX = panXAnimation
                                        translationY = panYAnimation
                                    }.rotate(rotateAnimation),
                        )
                        if (presentationState.coverSurface) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(Color.Black),
                            )
                        }
                    } else {
                        val colorFilter =
                            remember(imageState.id, imageFilter) {
                                if (imageFilter.hasImageFilter()) {
                                    ColorMatrixColorFilter(imageFilter.colorMatrix)
                                } else {
                                    null
                                }
                            }
                        // If the image loading is large, show the thumbnail while waiting
                        // TODO
                        val showLoadingThumbnail = true
                        SubcomposeAsyncImage(
                            modifier =
                                contentModifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoomAnimation
                                        scaleY = zoomAnimation
                                        translationX = panXAnimation
                                        translationY = panYAnimation

                                        val xTransform =
                                            (screenWidth - panXAnimation) / (screenWidth * 2)
                                        val yTransform =
                                            (screenHeight - panYAnimation) / (screenHeight * 2)
                                        if (DEBUG) {
                                            Timber.d(
                                                "graphicsLayer: xTransform=$xTransform, yTransform=$yTransform",
                                            )
                                        }

                                        transformOrigin = TransformOrigin(xTransform, yTransform)
                                    }.rotate(rotateAnimation),
                            model =
                                ImageRequest
                                    .Builder(LocalContext.current)
                                    .data(imageState.url)
                                    .size(Size.ORIGINAL)
                                    .crossfade(!showLoadingThumbnail)
                                    .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            colorFilter = colorFilter,
                            error = {
                                Text(
                                    modifier =
                                        Modifier
                                            .align(Alignment.Center),
                                    text = "Error loading image",
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            },
                            loading = {
                                ImageLoadingPlaceholder(
                                    thumbnailUrl = imageState.thumbnailUrl,
                                    showThumbnail = showLoadingThumbnail,
                                    colorFilter = colorFilter,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            },
                            // Ensure that if an image takes a long time to load, it won't be skipped
                            onLoading = {
                                viewModel.pulseSlideshow(Long.MAX_VALUE)
                            },
                            onSuccess = {
                                viewModel.pulseSlideshow()
                            },
                            onError = {
                                Timber.e(
                                    it.result.throwable,
                                    "Error loading image ${imageState.id}",
                                )
                                Toast
                                    .makeText(
                                        context,
                                        "Error loading image: ${it.result.throwable.localizedMessage}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                viewModel.pulseSlideshow()
                            },
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            showOverlay,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            imageState?.let { imageState ->
                ImageOverlay(
                    modifier =
                        contentModifier
                            .fillMaxWidth()
                            .background(AppColors.TransparentBlack50),
                    onDismiss = { showOverlay = false },
                    player = player,
                    slideshowControls = slideshowControls,
                    slideshowEnabled = slideshowState.enabled,
                    image = imageState,
                    position = position,
                    count = pager?.size ?: -1,
                    onClickItem = {},
                    onLongClickItem = {},
                    onZoom = ::zoom,
                    onRotate = { rotation += it },
                    onReset = { reset(true) },
                    onShowFilterDialogClick = {
                        showFilterDialog = true
                        showOverlay = false
                        viewModel.pauseSlideshow()
                    },
                )
            }
        }
        AnimatedVisibility(showFilterDialog) {
            ImageFilterDialog(
                filter = imageFilter,
                showVideoOptions = false,
                showSaveGalleryButton = true,
                onChange = viewModel::updateImageFilter,
                onClickSave = viewModel::saveImageFilter,
                onClickSaveGallery = viewModel::saveGalleryFilter,
                onDismissRequest = {
                    showFilterDialog = false
                    viewModel.unpauseSlideshow()
                    viewModel.pulseSlideshow()
                },
            )
        }
    }
}
