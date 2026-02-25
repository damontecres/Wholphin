package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RadialGradientShader
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.CrossFadeFactory
import dagger.hilt.android.lifecycle.HiltViewModel
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

data class CurrentItem(
    val item: BaseItem,
    val backdropUrl: String,
    val logoUrl: String?,
    val title: String,
)

@Composable
fun AppScreensaver(
    prefs: AppPreferences,
    modifier: Modifier = Modifier,
    viewModel: ScreensaverViewModel = hiltViewModel(),
) {
    val currentItem by viewModel.currentItem.collectAsState(null)
    AppScreensaverContent(
        currentItem = currentItem,
        showClock = prefs.interfacePreferences.showClock,
        duration = prefs.interfacePreferences.screensaverPreference.duration.milliseconds,
        animate = prefs.interfacePreferences.screensaverPreference.animate,
        modifier = modifier,
    )
}

@OptIn(ExperimentalCoilApi::class)
@Composable
fun AppScreensaverContent(
    currentItem: CurrentItem?,
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
        val alignment = listOf(Alignment.BottomStart, Alignment.BottomEnd).random()
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
                        .align(alignment)
                        .size(width = 240.dp, height = 120.dp)
                        .padding(16.dp),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .align(alignment)
                        .padding(16.dp)
                        .fillMaxWidth(.5f)
                        .fillMaxHeight(.3f),
            ) {
                Text(
                    text = currentItem?.title ?: "",
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(alignment),
                )
            }
        }

        if (showClock) {
            TimeDisplay()
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
        }
    }
}
