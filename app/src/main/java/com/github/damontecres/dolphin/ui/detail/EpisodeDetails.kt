package com.github.damontecres.dolphin.ui.detail

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class EpisodeViewModel
    @Inject
    constructor(
        val api: ApiClient,
    ) : ViewModel() {
        val item = MutableLiveData<BaseItemDto?>(null)

        fun init(
            itemId: UUID,
            potential: BaseItemDto?,
        ) {
            if (item.value == null && potential?.id == itemId) {
                item.value = potential
                return
            }
            if (item.value?.id == itemId) {
                return
            }
            viewModelScope.launch {
                try {
                    item.value = api.userLibraryApi.getItem(itemId).content
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load item $itemId")
                    item.value = null
                }
            }
        }
    }

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
