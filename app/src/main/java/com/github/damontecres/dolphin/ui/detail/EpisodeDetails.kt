package com.github.damontecres.dolphin.ui.detail

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.Video
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.api.client.ApiClient
import javax.inject.Inject

@HiltViewModel
class EpisodeViewModel
    @Inject
    constructor(
        api: ApiClient,
    ) : ItemViewModel<Video>(api)

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
    if (item == null) {
        Text(text = "Loading...")
    } else {
        item?.let { item ->
            LazyColumn(modifier = modifier) {
                item {
                    Text(text = item.name ?: "Unknown")
                }
                item {
                    Button(
                        onClick = {
                            navigationManager.navigateTo(Destination.Playback(item.id, 0L, item))
                        },
                    ) {
                        Text(text = "Play")
                    }
                }
            }
        }
    }
}
