package com.github.damontecres.dolphin.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.data.model.Video
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.components.ErrorMessage
import com.github.damontecres.dolphin.ui.components.LoadingPage
import com.github.damontecres.dolphin.ui.components.details.VideoDetailsHeader
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.util.LoadingState
import com.github.damontecres.dolphin.util.seasonEpisode
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class EpisodeViewModel
    @Inject
    constructor(
        api: ApiClient,
    ) : LoadingItemViewModel<Video>(api)

@Composable
fun EpisodeDetails(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: EpisodeViewModel = hiltViewModel(),
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
            item?.let { item ->
                EpisodeDetailsContent(
                    item = item,
                    backdropImageUrl =
                        remember {
                            item.data.parentBackdropItemId?.let {
                                viewModel.imageUrl(it, ImageType.BACKDROP)
                            }
                        },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
fun EpisodeDetailsContent(
    item: BaseItem,
    backdropImageUrl: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    val dto = item.data
    val title = item.name ?: "Unknown"
    val subtitle = dto.seriesName
    val description = dto.overview

    val details =
        buildList {
            dto.seasonEpisode?.let(::add)
            dto.mediaSources?.firstOrNull()?.runTimeTicks?.ticks?.inWholeMinutes?.toString()?.let {
                add(it)
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
//                .fillMaxHeight(.33f)
                .height(460.dp)
                .bringIntoViewRequester(bringIntoViewRequester),
    ) {
        if (backdropImageUrl.isNotNullOrBlank()) {
            Timber.v("Banner image url: $backdropImageUrl")
            val gradientColor = MaterialTheme.colorScheme.background
            AsyncImage(
                model = backdropImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, gradientColor),
                                    startY = 500f,
                                ),
                            )
                            drawRect(
                                Brush.horizontalGradient(
                                    colors = listOf(gradientColor, Color.Transparent),
                                    endX = 400f,
                                    startX = 100f,
                                ),
                            )
                        },
            )
        }
        Column(modifier = Modifier.fillMaxWidth(0.8f)) {
            Spacer(modifier = Modifier.height(60.dp))
            VideoDetailsHeader(
                title = title,
                subtitle = subtitle,
                description = description,
                details = details,
                moreDetails = sortedMapOf(),
                rating = 0f,
                resumeTime = dto.userData?.playbackPositionTicks?.ticks ?: 0.seconds,
                watched = dto.userData?.played ?: false,
                favorite = dto.userData?.isFavorite ?: false,
                bringIntoViewRequester = bringIntoViewRequester,
                descriptionOnClick = {},
                moreOnClick = {},
                modifier = Modifier,
            )
        }
    }
}
