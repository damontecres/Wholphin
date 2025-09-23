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
            val dto = item.data
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(32.dp),
                modifier = modifier,
            ) {
                item {
                    Text(text = item.name ?: "Unknown")
                }
                item {
                    if (dto.parentIndexNumber != null && dto.indexNumber != null) {
                        Text(
                            text = "S${dto.parentIndexNumber} E${dto.indexNumber}",
                        )
                    }
                }
                dto.overview?.let {
                    item {
                        Text(text = it)
                    }
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
