package com.github.damontecres.dolphin.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.Video
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.components.ErrorMessage
import com.github.damontecres.dolphin.ui.components.LoadingPage
import com.github.damontecres.dolphin.ui.detail.movie.MovieViewModel
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.extensions.ticks
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class VideoViewModel
    @Inject
    constructor(
        api: ApiClient,
        val navigationManager: NavigationManager,
    ) : LoadingItemViewModel<Video>(api)

@Composable
fun VideoDetails(
    preferences: UserPreferences,
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
            item?.let { item ->
                val dto = item.data
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(32.dp),
                    modifier = modifier,
                ) {
                    item {
                        Text(text = item.name ?: "Unknown")
                    }
                    dto.overview?.let {
                        item {
                            Text(text = it)
                        }
                    }
                    dto.userData?.playbackPositionTicks?.ticks?.let {
                        if (it > 60.seconds) {
                            item {
                                Button(
                                    onClick = {
                                        viewModel.navigationManager.navigateTo(
                                            Destination.Playback(
                                                item.id,
                                                it.inWholeMilliseconds,
                                                item,
                                            ),
                                        )
                                    },
                                ) {
                                    Text(text = "Resume")
                                }
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = {
                                viewModel.navigationManager.navigateTo(
                                    Destination.Playback(
                                        item.id,
                                        0L,
                                        item,
                                    ),
                                )
                            },
                        ) {
                            Text(text = "Play")
                        }
                    }
                }
            }
        }
    }
}
