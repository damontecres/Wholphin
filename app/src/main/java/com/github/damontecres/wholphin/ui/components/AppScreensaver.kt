package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.CrossFadeFactory
import com.github.damontecres.wholphin.ui.nav.TOP_SCRIM_ALPHA
import com.github.damontecres.wholphin.ui.nav.TOP_SCRIM_END_FRACTION
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel
class ScreensaverViewModel
    @Inject
    constructor(
        private val screensaverService: ScreensaverService,
        val preferencesDataStore: DataStore<AppPreferences>,
    ) : ViewModel() {
        val currentItem = screensaverService.createItemFlow(viewModelScope)
    }

sealed interface ScreensaverItem {
    data class Error(
        val exception: Exception,
    ) : ScreensaverItem

    data object Empty : ScreensaverItem

    data class CurrentItem(
        val item: BaseItem,
        val backdropUrl: String,
        val logoUrl: String?,
        val title: String,
    ) : ScreensaverItem
}

@Composable
fun AppScreensaver(
    prefs: AppPreferences,
    modifier: Modifier = Modifier,
    viewModel: ScreensaverViewModel = hiltViewModel(),
) {
    val currentItem by viewModel.currentItem.collectAsState(null)
    AppScreensaverContent(
        currentItem = currentItem,
        showClock = prefs.interfacePreferences.screensaverPreference.showClock,
        duration = prefs.interfacePreferences.screensaverPreference.duration.milliseconds,
        animate = prefs.interfacePreferences.screensaverPreference.animate,
        modifier = modifier,
    )
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun AppScreensaverContent(
    currentItem: ScreensaverItem?,
    showClock: Boolean,
    duration: Duration,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .background(Color.Black),
    ) {
        val infiniteTransition = rememberInfiniteTransition()
        val scale by infiniteTransition.animateFloat(
            1f,
            if (animate) 1.1f else 1f,
            infiniteRepeatable(
                tween(
                    durationMillis = duration.inWholeMilliseconds.toInt(),
                    delayMillis = 500,
                    easing = LinearEasing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
        )
        when (currentItem) {
            ScreensaverItem.Empty -> {
                ScreensaverPlaceholder(
                    text = stringResource(R.string.no_results),
                    duration = duration,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            is ScreensaverItem.Error -> {
                ScreensaverPlaceholder(
                    text = "Error connecting to Jellyfin server",
                    duration = duration,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            null,
            is ScreensaverItem.CurrentItem,
            -> {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(currentItem?.backdropUrl)
                            .transitionFactory(CrossFadeFactory(2000.milliseconds))
                            .useExistingImageAsPlaceholder(true)
                            .build(),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            },
                )

                var logoError by remember(currentItem) { mutableStateOf(false) }
                if (!logoError) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalContext.current)
                                .data(currentItem?.logoUrl)
                                .transitionFactory(CrossFadeFactory(750.milliseconds))
                                .build(),
                        contentDescription = "Logo",
                        onError = {
                            logoError = true
                        },
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .size(width = 240.dp, height = 120.dp)
                                .padding(16.dp),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp)
                                .fillMaxWidth(.5f)
                                .fillMaxHeight(.3f),
                    ) {
                        Text(
                            text = currentItem?.title ?: "",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.displaySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart),
                        )
                    }
                }
            }
        }

        val largeRadialGradient =
            remember {
                object : ShaderBrush() {
                    override fun createShader(size: Size): Shader {
                        val biggerDimension = maxOf(size.height, size.width)
                        return RadialGradientShader(
                            colors = listOf(Color.Transparent, AppColors.TransparentBlack25),
                            center = size.center,
                            radius = biggerDimension / 1.5f,
                            colorStops = listOf(0f, 0.85f),
                        )
                    }
                }
            }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = largeRadialGradient,
                blendMode = BlendMode.Multiply,
            )
            if (showClock) {
                // Add scrim to make clock more readable
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0f to Color.Black.copy(alpha = TOP_SCRIM_ALPHA),
                                    TOP_SCRIM_END_FRACTION to Color.Transparent,
                                ),
                        ),
                    blendMode = BlendMode.Multiply,
                )
            }
        }
        if (showClock) {
            TimeDisplay()
        }
    }
}

@Composable
fun ScreensaverPlaceholder(
    text: String,
    duration: Duration,
    modifier: Modifier = Modifier,
) {
    var alignment by remember { mutableStateOf(Alignment.BottomStart) }
    val alignments =
        remember(alignment) {
            mutableListOf(
                Alignment.TopStart,
                Alignment.TopCenter,
                Alignment.TopEnd,
                Alignment.CenterStart,
                Alignment.Center,
                Alignment.CenterEnd,
                Alignment.BottomStart,
                Alignment.BottomCenter,
                Alignment.BottomEnd,
            ).apply { remove(alignment) }
        }
    LaunchedEffect(Unit) {
        while (true) {
            delay(duration)
            alignment = alignments.random()
        }
    }
    AnimatedContent(
        targetState = alignment,
        label = "alignment animation",
        transitionSpec = {
            fadeIn(animationSpec = tween(500, delayMillis = 100))
                .togetherWith(fadeOut(animationSpec = tween(500, 100)))
        },
        modifier = modifier,
    ) { align ->
        Box(Modifier.fillMaxSize()) {
            Text(
                text = text,
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .align(align)
                        .padding(40.dp),
            )
        }
    }
}
