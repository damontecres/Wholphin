package com.github.damontecres.dolphin.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.CardDefaults
import com.github.damontecres.dolphin.ui.DefaultItemFields
import com.github.damontecres.dolphin.ui.cards.EpisodeCard
import com.github.damontecres.dolphin.ui.cards.ItemRow
import com.github.damontecres.dolphin.ui.cards.SeasonCard
import com.github.damontecres.dolphin.ui.components.SearchEditTextBox
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.tryRequestFocus
import com.github.damontecres.dolphin.util.ApiRequestPager
import com.github.damontecres.dolphin.util.ExceptionHandler
import com.github.damontecres.dolphin.util.GetItemsRequestHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import javax.inject.Inject

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        val api: ApiClient,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val movies = MutableLiveData<List<BaseItem?>>(listOf())
        val series = MutableLiveData<List<BaseItem?>>(listOf())
        val episodes = MutableLiveData<List<BaseItem?>>(listOf())

        fun search(query: String?) {
            movies.value = listOf()
            series.value = listOf()
            episodes.value = listOf()
            if (query.isNotNullOrBlank()) {
                viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                    val request =
                        GetItemsRequest(
                            searchTerm = query,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            fields = DefaultItemFields,
                            limit = 25,
                        )
                    val pager = ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope)
                    pager.init()
                    withContext(Dispatchers.Main) {
                        movies.value = pager
                    }
                }
                viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                    val request =
                        GetItemsRequest(
                            searchTerm = query,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.SERIES),
                            fields = DefaultItemFields,
                            limit = 25,
                        )
                    val pager = ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope)
                    pager.init()
                    withContext(Dispatchers.Main) {
                        series.value = pager
                    }
                }
                viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                    val request =
                        GetItemsRequest(
                            searchTerm = query,
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                            fields = DefaultItemFields,
                            limit = 25,
                        )
                    val pager = ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope)
                    pager.init()
                    withContext(Dispatchers.Main) {
                        episodes.value = pager
                    }
                }
            }
        }

        fun getHints(query: String) {
            // TODO
//        api.searchApi.getSearchHints()
        }
    }

@Composable
fun SearchPage(
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val movies by viewModel.movies.observeAsState(listOf())
    val series by viewModel.series.observeAsState(listOf())
    val episodes by viewModel.episodes.observeAsState(listOf())

    var query by rememberSaveable { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val resultsFocusRequester = remember { FocusRequester() }

    LaunchedEffect(query) {
        delay(750L)
        viewModel.search(query)
    }
    LaunchedEffect(Unit) {
        // TODO focus on back to results when available
        searchFocusRequester.tryRequestFocus()
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SearchEditTextBox(
                    value = query,
                    onValueChange = {
                        query = it
                    },
                    onSearchClick = {
                        viewModel.search(query)
                    },
                    modifier = Modifier.focusRequester(searchFocusRequester),
                )
            }
        }
        if (movies.isNotEmpty()) {
            item {
                ItemRow(
                    title = "Movies",
                    items = movies,
                    onClickItem = {
                        viewModel.navigationManager.navigateTo(it.destination())
                    },
                    onLongClickItem = {},
                    modifier = Modifier,
                    cardContent = @Composable { index, item, mod, onClick, onLongClick ->
                        SeasonCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            imageHeight = CardDefaults.height2x3,
                            modifier = mod,
                        )
                    },
                )
            }
        }
        if (series.isNotEmpty()) {
            item {
                ItemRow(
                    title = "Series",
                    items = series,
                    onClickItem = {
                        viewModel.navigationManager.navigateTo(it.destination())
                    },
                    onLongClickItem = {},
                    modifier = Modifier,
                    cardContent = @Composable { index, item, mod, onClick, onLongClick ->
                        SeasonCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            imageHeight = CardDefaults.height2x3,
                            modifier = mod,
                        )
                    },
                )
            }
        }
        if (episodes.isNotEmpty()) {
            item {
                ItemRow(
                    title = "Episodes",
                    items = episodes,
                    onClickItem = {
                        viewModel.navigationManager.navigateTo(it.destination())
                    },
                    onLongClickItem = {},
                    modifier = Modifier,
                    cardContent = @Composable { index, item, mod, onClick, onLongClick ->
                        EpisodeCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            imageHeight = 140.dp,
                            modifier = mod.padding(horizontal = 8.dp),
                        )
                    },
                )
            }
        }
    }
}
