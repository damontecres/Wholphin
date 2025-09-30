package com.github.damontecres.dolphin.ui.detail

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
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
import com.github.damontecres.dolphin.util.ItemPager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SeriesViewModel
    @Inject
    constructor(
        api: ApiClient,
    ) : ItemViewModel<Video>(api) {
        val seasons = MutableLiveData<List<BaseItem?>>(listOf())

        override fun init(
            itemId: UUID,
            potential: BaseItem?,
        ): Job =
            viewModelScope.launch {
                super.init(itemId, potential)?.join()
                item.value?.let { item ->
                    val request =
                        GetItemsRequest(
                            parentId = item.id,
                            recursive = false,
                            includeItemTypes = listOf(BaseItemKind.SEASON),
                            sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                            sortOrder = listOf(SortOrder.ASCENDING),
                            fields =
                                listOf(
                                    ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                                    ItemFields.CHILD_COUNT,
                                    ItemFields.SEASON_USER_DATA,
                                ),
                        )
                    val pager =
                        ItemPager(api, request, viewModelScope, itemCount = item.data.childCount)
                    pager.init()
                    seasons.value = pager
                    Timber.v("Loaded ${pager.size} seasons for series ${item.id}")
                }
            }

        fun init(
            itemId: UUID,
            potential: BaseItem?,
            season: Int?,
        ) {
            viewModelScope.launch {
                init(itemId, potential).join()
                season?.let {
                    (seasons.value!! as ItemPager)
                        .getBlocking(season)
                        ?.let {
                            loadEpisodes(it.id)
                        }
                }
            }
        }

        val episodes = MutableLiveData<List<BaseItem?>>(listOf())

        fun loadEpisodes(seasonId: UUID): Job {
            Timber.v("Loading episodes for season $seasonId")
            episodes.value = listOf()
            return viewModelScope.launch {
                val request =
                    GetItemsRequest(
                        parentId = seasonId,
                        recursive = false,
                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                        sortBy = listOf(ItemSortBy.INDEX_NUMBER),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        fields =
                            listOf(
                                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                                ItemFields.CHILD_COUNT,
                                ItemFields.MEDIA_STREAMS,
                                ItemFields.OVERVIEW,
                            ),
                    )
                val pager = ItemPager(api, request, viewModelScope)
                pager.init()
                Timber.v("Loaded ${pager.size} episodes for season $seasonId")
                episodes.value = pager
            }
        }
    }

@Composable
fun SeriesDetails(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.init(destination.itemId, destination.item)
    }
    val item by viewModel.item.observeAsState()
    val seasons by viewModel.seasons.observeAsState(listOf())
    if (item == null) {
        Text(text = "Loading...")
    } else {
        item?.let { item ->
            LazyColumn(modifier = modifier) {
                item {
                    Text(text = item.name ?: "Unknown")
                }
                item {
                    ItemRow(
                        title = "Seasons",
                        items = seasons,
                        onClickItem = { navigationManager.navigateTo(it.destination()) },
                        onLongClickItem = { },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
