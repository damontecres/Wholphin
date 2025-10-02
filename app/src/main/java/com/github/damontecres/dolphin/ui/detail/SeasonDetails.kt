package com.github.damontecres.dolphin.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.data.model.Video
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.cards.ItemRow
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.util.ApiRequestPager
import com.github.damontecres.dolphin.util.ExceptionHandler
import com.github.damontecres.dolphin.util.GetItemsRequestHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SeasonViewModel
    @Inject
    constructor(
        api: ApiClient,
    ) : ItemViewModel<Video>(api) {
        val episodes = MutableLiveData<List<BaseItem?>>(listOf())

        override fun init(
            itemId: UUID,
            potential: BaseItem?,
        ): Job? =
            viewModelScope.launch(ExceptionHandler()) {
                super.init(itemId, potential)?.join()
                item.value?.let { item ->
                    val request =
                        GetItemsRequest(
                            parentId = item.id,
                            recursive = false,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                            sortOrder = listOf(SortOrder.ASCENDING),
                            fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO, ItemFields.CHILD_COUNT),
                        )
                    val pager =
                        ApiRequestPager(
                            api,
                            request,
                            GetItemsRequestHandler,
                            viewModelScope,
                            itemCount = item.data.childCount,
                        )
                    pager.init()
                    episodes.value = pager
                }
            }
    }

@Composable
fun SeasonDetails(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: SeasonViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.init(destination.itemId, destination.item)
    }
    val item by viewModel.item.observeAsState()
    val episodes by viewModel.episodes.observeAsState(listOf())
    if (item == null) {
        Text(text = "Loading...")
    } else {
        item?.let { item ->
            var focusedChild by remember { mutableIntStateOf(0) }
            LazyColumn(modifier = modifier) {
                item {
                    Text(text = item.name ?: "Unknown")
                }
                item {
                }
                item {
                    ItemRow(
                        title = "Episodes",
                        items = episodes,
                        onClickItem = { navigationManager.navigateTo(it.destination()) },
                        onLongClickItem = { },
                        cardOnFocus = { isFocused, index ->
                            if (isFocused) focusedChild = index
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodeHeader(
    item: BaseItem,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
    }
}
