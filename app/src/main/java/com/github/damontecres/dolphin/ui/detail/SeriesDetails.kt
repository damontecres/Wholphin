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
import com.github.damontecres.dolphin.ui.components.ErrorMessage
import com.github.damontecres.dolphin.ui.components.LoadingPage
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.util.ApiRequestPager
import com.github.damontecres.dolphin.util.ExceptionHandler
import com.github.damontecres.dolphin.util.GetEpisodesRequestHandler
import com.github.damontecres.dolphin.util.GetItemsRequestHandler
import com.github.damontecres.dolphin.util.LoadingExceptionHandler
import com.github.damontecres.dolphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        private lateinit var seriesId: UUID
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val seasons = MutableLiveData<List<BaseItem?>>(listOf())
        val episodes = MutableLiveData<List<BaseItem?>>(listOf())

        fun init(
            itemId: UUID,
            potential: BaseItem?,
            season: Int?,
            episode: Int?,
        ) {
            this.seriesId = itemId
            viewModelScope.launch(
                LoadingExceptionHandler(
                    loading,
                    "Error loading series $seriesId",
                ) + Dispatchers.IO,
            ) {
                val item = fetchItem(seriesId, potential)
                if (item != null) {
                    val seasonPager = getSeasons(item)
                    val episodePager =
                        season?.let { seasonNum ->
                            // TODO map season number to index in list
                            loadEpisodesInternal(seasonNum)
                        }
                    withContext(Dispatchers.Main) {
                        seasons.value = seasonPager.orEmpty()
                        episodes.value = episodePager.orEmpty()
                        loading.value = LoadingState.Success
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        seasons.value = listOf()
                        episodes.value = listOf()
                        loading.value = LoadingState.Error("Series $seriesId not found")
                    }
                }
            }
        }

        private suspend fun getSeasons(item: BaseItem): ApiRequestPager<GetItemsRequest>? {
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
                ApiRequestPager(
                    api,
                    request,
                    GetItemsRequestHandler,
                    viewModelScope,
                    itemCount = item.data.childCount,
                )
            pager.init()
            Timber.v("Loaded ${pager.size} seasons for series ${item.id}")
            val pairs =
                pager.mapIndexed { index, _ ->
                    val season = pager.getBlocking(index)
                    Pair(season?.indexNumber!!, index)
                }
            mapOf(*pairs.toTypedArray())
            mapOf(*pairs.map { Pair(it.second, it.first) }.toTypedArray())
            return pager
        }

        private suspend fun loadEpisodesInternal(season: Int): ApiRequestPager<GetEpisodesRequest> {
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
            return pager
        }

        fun loadEpisodes(season: Int) =
            viewModelScope.async(ExceptionHandler(true)) {
                val episodePager =
                    try {
                        loadEpisodesInternal(season)
                    } catch (e: Exception) {
                        Timber.e(e, "Error loading episodes for $seriesId for season $season")
                        // TODO show error in UI?
                        listOf()
                    }
                withContext(Dispatchers.Main) {
                    episodes.value = episodePager
                }
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
        viewModel.init(destination.itemId, destination.item, null, null)
    }
    val item by viewModel.item.observeAsState()
    val seasons by viewModel.seasons.observeAsState(listOf())
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)
        LoadingState.Loading -> LoadingPage()
        LoadingState.Success -> {
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
}
