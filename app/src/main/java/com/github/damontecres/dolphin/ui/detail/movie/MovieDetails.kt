package com.github.damontecres.dolphin.ui.detail.movie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.data.model.Video
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.components.ErrorMessage
import com.github.damontecres.dolphin.ui.components.ExpandablePlayButtons
import com.github.damontecres.dolphin.ui.components.LoadingPage
import com.github.damontecres.dolphin.ui.detail.LoadingItemViewModel
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.tryRequestFocus
import com.github.damontecres.dolphin.util.ExceptionHandler
import com.github.damontecres.dolphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.extensions.ticks
import javax.inject.Inject
import kotlin.time.Duration

@HiltViewModel
class MovieViewModel
    @Inject
    constructor(
        api: ApiClient,
    ) : LoadingItemViewModel<Video>(api)

@Composable
fun MovieDetails(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: MovieViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.init(destination.itemId, destination.item)
    }
    val item by viewModel.item.observeAsState()
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)
        LoadingState.Loading -> LoadingPage()
        LoadingState.Success -> {
            item?.let {
                MovieDetailsContent(
                    preferences,
                    navigationManager,
                    it,
                    modifier,
                )
            }
        }
    }
}

@Composable
fun MovieDetailsContent(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    movie: BaseItem,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val dto = movie.data
    val backdropImageUrl = movie.backdropImageUrl
    val resumePosition = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
        bringIntoViewRequester.bringIntoView()
        focusRequester.tryRequestFocus()
    }
    Box(modifier = modifier) {
        if (backdropImageUrl.isNotNullOrBlank()) {
            val gradientColor = MaterialTheme.colorScheme.background
            AsyncImage(
                model = backdropImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier =
                    Modifier
                        .fillMaxHeight(.75f)
                        .alpha(.5f)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, gradientColor),
                                    startY = size.height * .5f,
                                ),
                            )
                            drawRect(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, gradientColor),
                                    endX = 0f,
                                    startX = size.width * .75f,
                                ),
                            )
                        },
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(32.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                MovieDetailsHeader(
                    movie = movie,
                    bringIntoViewRequester = bringIntoViewRequester,
                    overviewOnClick = {},
                    Modifier
                        .fillMaxWidth(.7f),
                )
            }
            item {
                ExpandablePlayButtons(
                    resumePosition = resumePosition,
                    watched = dto.userData?.played ?: false,
                    playOnClick = {
                        navigationManager.navigateTo(
                            Destination.Playback(
                                movie.id,
                                it.inWholeMilliseconds,
                                movie,
                            ),
                        )
                    },
                    moreOnClick = {
                        // TODO
                    },
                    watchOnClick = {
                        // TODO
                    },
                    buttonOnFocusChanged = {
                        scope.launch(ExceptionHandler()) {
                            bringIntoViewRequester.bringIntoView()
                        }
                    },
                    modifier = Modifier.focusRequester(focusRequester),
                )
            }
        }
    }
}
