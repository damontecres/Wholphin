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
import com.github.damontecres.dolphin.ui.indexOfFirstOrNull
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.util.ApiRequestPager
import com.github.damontecres.dolphin.util.ExceptionHandler
import com.github.damontecres.dolphin.util.GetEpisodesRequestHandler
import com.github.damontecres.dolphin.util.ItemPager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
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
        val episodes = MutableLiveData<List<BaseItem?>>(listOf())

        override fun init(
            itemId: UUID,
            potential: BaseItem?,
        ): Job =
            viewModelScope.launch(ExceptionHandler()) {
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
                    val pairs =
                        pager.mapIndexed { index, _ ->
                            val season = pager.getBlocking(index)
                            Pair(season?.indexNumber!!, index)
                        }
                    mapOf(*pairs.toTypedArray())
                    mapOf(*pairs.map { Pair(it.second, it.first) }.toTypedArray())
                }
            }

        fun init(
            itemId: UUID,
            potential: BaseItem?,
            season: Int?,
            episode: Int?,
        ) {
            viewModelScope.launch(ExceptionHandler()) {
                init(itemId, potential).join()
                season?.let { seasonNum ->
                    val targetSeasonPosition =
                        (seasons.value!! as ItemPager)
                            .toBlockingList()
                            .indexOfFirstOrNull { it.indexNumber == seasonNum }
                    loadEpisodes(seasonNum)
                }
            }
        }

        fun loadEpisodes(season: Int): Deferred<ApiRequestPager<*>> =
            viewModelScope.async(ExceptionHandler()) {
                val request =
                    GetEpisodesRequest(
                        seriesId = item.value!!.id,
                        season = season,
                        sortBy = ItemSortBy.INDEX_NUMBER,
                        fields =
                            listOf(
                                ItemFields.MEDIA_SOURCES,
                                ItemFields.MEDIA_STREAMS,
                                ItemFields.OVERVIEW,
                                ItemFields.CUSTOM_RATING,
                                ItemFields.TRICKPLAY,
                                ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
                            ),
                    )
                val pager = ApiRequestPager(api, request, GetEpisodesRequestHandler, viewModelScope)
                pager.init()
                Timber.v("Loaded ${pager.size} episodes for season $season")
                episodes.value = pager
                pager
            }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
            listIndex: Int,
        ) = viewModelScope.launch(ExceptionHandler()) {
            if (played) {
                api.playStateApi.markPlayedItem(itemId)
            } else {
                api.playStateApi.markUnplayedItem(itemId)
            }
            refreshEpisode(itemId, listIndex)
        }

        fun refreshEpisode(
            itemId: UUID,
            listIndex: Int,
        ) = viewModelScope.launch(ExceptionHandler()) {
            val base = api.userLibraryApi.getItem(itemId).content
            val item = BaseItem.from(base, api)
            episodes.value =
                episodes.value!!.toMutableList().apply {
                    this[listIndex] = item
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
