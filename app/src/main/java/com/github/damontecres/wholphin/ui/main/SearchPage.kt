package com.github.damontecres.wholphin.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.EpisodeCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.VoiceInputManager
import com.github.damontecres.wholphin.ui.components.VoiceSearchButton
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchViewModel
    @Inject
    constructor(
        val api: ApiClient,
        val navigationManager: NavigationManager,
        private val seerrService: SeerrService,
        val voiceInputManager: VoiceInputManager,
    ) : ViewModel() {
        val voiceState = voiceInputManager.state
        val soundLevel = voiceInputManager.soundLevel
        val partialResult = voiceInputManager.partialResult
        val seerrActive = seerrService.active

        val movies = MutableLiveData<SearchResult>(SearchResult.NoQuery)
        val series = MutableLiveData<SearchResult>(SearchResult.NoQuery)
        val episodes = MutableLiveData<SearchResult>(SearchResult.NoQuery)
        val collections = MutableLiveData<SearchResult>(SearchResult.NoQuery)
        val seerrResults = MutableLiveData<SearchResult>(SearchResult.NoQuery)
        val combinedResults = MutableLiveData<SearchResult>(SearchResult.NoQuery)

        private var currentQuery: String? = null
        private var combinedMode: Boolean = false

        fun search(
            query: String?,
            combined: Boolean = false,
        ) {
            if (currentQuery == query && combinedMode == combined) {
                return
            }
            currentQuery = query
            combinedMode = combined
            if (query.isNotNullOrBlank()) {
                if (combined) {
                    combinedResults.value = SearchResult.Searching
                    searchCombined(query)
                } else {
                    movies.value = SearchResult.Searching
                    series.value = SearchResult.Searching
                    episodes.value = SearchResult.Searching
                    collections.value = SearchResult.Searching
                    searchInternal(query, BaseItemKind.MOVIE, movies)
                    searchInternal(query, BaseItemKind.SERIES, series)
                    searchInternal(query, BaseItemKind.EPISODE, episodes)
                    searchInternal(query, BaseItemKind.BOX_SET, collections)
                }
                searchSeerr(query)
            } else {
                movies.value = SearchResult.NoQuery
                series.value = SearchResult.NoQuery
                episodes.value = SearchResult.NoQuery
                collections.value = SearchResult.NoQuery
                seerrResults.value = SearchResult.NoQuery
                combinedResults.value = SearchResult.NoQuery
            }
        }

        private fun searchInternal(
            query: String,
            type: BaseItemKind,
            target: MutableLiveData<SearchResult>,
        ) {
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                try {
                    val request =
                        GetItemsRequest(
                            searchTerm = query,
                            recursive = true,
                            includeItemTypes = listOf(type),
                            fields = SlimItemFields,
                            limit = 25,
                        )
                    val pager =
                        ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope)
                    pager.init()
                    withContext(Dispatchers.Main) {
                        target.value = SearchResult.Success(pager)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception searching for $type")
                    withContext(Dispatchers.Main) {
                        target.value = SearchResult.Error(ex)
                    }
                }
            }
        }

        private fun searchCombined(query: String) {
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                try {
                    val request =
                        GetItemsRequest(
                            searchTerm = query,
                            recursive = true,
                            includeItemTypes =
                                listOf(
                                    BaseItemKind.MOVIE,
                                    BaseItemKind.SERIES,
                                    BaseItemKind.EPISODE,
                                    BaseItemKind.BOX_SET,
                                ),
                            fields = SlimItemFields,
                            limit = 50,
                        )
                    val pager =
                        ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope)
                    pager.init()
                    withContext(Dispatchers.Main) {
                        combinedResults.value = SearchResult.Success(pager)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception in combined search")
                    withContext(Dispatchers.Main) {
                        combinedResults.value = SearchResult.Error(ex)
                    }
                }
            }
        }

        private fun searchSeerr(query: String) {
            viewModelScope.launchIO {
                if (seerrService.active.first()) {
                    seerrResults.setValueOnMain(SearchResult.Searching)
                    val results =
                        seerrService
                            .search(query)
                            .map { DiscoverItem(it) }
                            .filter { it.type == SeerrItemType.MOVIE || it.type == SeerrItemType.TV }
                    seerrResults.setValueOnMain(SearchResult.SuccessSeerr(results))
                }
            }
        }

        init {
            addCloseable(voiceInputManager)
        }

        fun getHints(query: String) {
            // TODO
//        api.searchApi.getSearchHints()
        }
    }

sealed interface SearchResult {
    data object NoQuery : SearchResult

    data object Searching : SearchResult

    data class Error(
        val ex: Exception,
    ) : SearchResult

    data class Success(
        val items: List<BaseItem?>,
    ) : SearchResult

    data class SuccessSeerr(
        val items: List<DiscoverItem>,
    ) : SearchResult
}

private const val SEARCH_ROW = 0
private const val TAB_ROW = SEARCH_ROW + 1
private const val MOVIE_ROW = TAB_ROW + 1
private const val SERIES_ROW = MOVIE_ROW + 1
private const val EPISODE_ROW = SERIES_ROW + 1
private const val COLLECTION_ROW = EPISODE_ROW + 1
private const val SEERR_ROW = COLLECTION_ROW + 1
private const val COMBINED_ROW = TAB_ROW + 1

/** Delay for focus to settle after voice search dialog dismisses. */
private const val VOICE_RESULT_FOCUS_DELAY_MS = 350L

@Composable
fun SearchPage(
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val movies by viewModel.movies.observeAsState(SearchResult.NoQuery)
    val collections by viewModel.collections.observeAsState(SearchResult.NoQuery)
    val series by viewModel.series.observeAsState(SearchResult.NoQuery)
    val episodes by viewModel.episodes.observeAsState(SearchResult.NoQuery)
    val seerrResults by viewModel.seerrResults.observeAsState(SearchResult.NoQuery)
    val combinedResults by viewModel.combinedResults.observeAsState(SearchResult.NoQuery)
    val combinedMode = userPreferences.appPreferences.interfacePreferences.combinedSearchResults

//    val query = rememberTextFieldState()
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequesters = remember { List(SEERR_ROW + 1) { FocusRequester() } }
    val tabFocusRequesters = remember { List(2) { FocusRequester() } }

    val seerrActive by viewModel.seerrActive.collectAsState(initial = false)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    var position by rememberPosition(0, 0)
    var searchClicked by rememberSaveable { mutableStateOf(false) }
    var immediateSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            viewModel.voiceInputManager.stopListening()
        }
    }

    fun triggerImmediateSearch(searchQuery: String) {
        immediateSearchQuery = searchQuery
        searchClicked = true
        viewModel.search(searchQuery, combinedMode)
    }

    LaunchedEffect(query, combinedMode) {
        when {
            immediateSearchQuery == query -> {
                immediateSearchQuery = null
            }

            else -> {
                delay(750L)
                viewModel.search(query, combinedMode)
            }
        }
    }
    LaunchedEffect(Unit) {
        focusRequesters.getOrNull(position.row)?.tryRequestFocus()
    }
    val onClickItem = { index: Int, item: BaseItem ->
        viewModel.navigationManager.navigateTo(item.destination())
    }

    LaunchedEffect(
        searchClicked,
        movies,
        collections,
        series,
        episodes,
        seerrResults,
        combinedResults,
        combinedMode,
        selectedTab,
        seerrActive,
    ) {
        if (!searchClicked) return@LaunchedEffect

        withContext(Dispatchers.IO) {
            // Want to focus on the first successful row after all of the ones before it are finished searching
            // Only consider visible rows based on tab selection
            val isLibraryTab = selectedTab == 0 || !seerrActive || query.isBlank()
            val results =
                if (isLibraryTab) {
                    if (combinedMode) {
                        listOf(combinedResults)
                    } else {
                        listOf(movies, series, episodes, collections)
                    }
                } else {
                    listOf(seerrResults)
                }
            val firstSuccess =
                results.indexOfFirst { it is SearchResult.Success || it is SearchResult.SuccessSeerr }
            if (firstSuccess >= 0) {
                val anyBeforeSearching =
                    results.subList(0, firstSuccess).any { it is SearchResult.Searching }
                if (!anyBeforeSearching) {
                    val targetRow =
                        if (isLibraryTab) {
                            if (combinedMode) {
                                COMBINED_ROW
                            } else {
                                MOVIE_ROW + firstSuccess
                            }
                        } else {
                            SEERR_ROW
                        }
                    position = RowColumn(targetRow, 0)
                    onMain { focusRequesters[targetRow].tryRequestFocus() }
                }
            }
        }
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 44.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.focusGroup(),
    ) {
        item {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                var isSearchActive by remember { mutableStateOf(false) }
                var isTextFieldFocused by remember { mutableStateOf(false) }
                val textFieldFocusRequester = remember { FocusRequester() }

                BackHandler(isTextFieldFocused) {
                    when {
                        isSearchActive -> {
                            isSearchActive = false
                            keyboardController?.hide()
                        }

                        else -> {
                            focusManager.moveFocus(FocusDirection.Next)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .focusGroup()
                            .focusRestorer(textFieldFocusRequester)
                            .focusRequester(focusRequesters[SEARCH_ROW]),
                ) {
                    VoiceSearchButton(
                        onSpeechResult = { spokenText ->
                            query = spokenText
                            triggerImmediateSearch(spokenText)
                        },
                        voiceInputManager = viewModel.voiceInputManager,
                    )

                    SearchEditTextBox(
                        value = query,
                        onValueChange = {
                            isSearchActive = true
                            query = it
                        },
                        onSearchClick = { triggerImmediateSearch(query) },
                        readOnly = !isSearchActive,
                        modifier =
                            Modifier
                                .focusRequester(textFieldFocusRequester)
                                .onFocusChanged { state ->
                                    isTextFieldFocused = state.isFocused
                                    if (!state.isFocused) isSearchActive = false
                                }.onPreviewKeyEvent { event ->
                                    val isActivationKey =
                                        event.key in listOf(Key.DirectionCenter, Key.Enter)
                                    if (event.type == KeyEventType.KeyUp && isActivationKey && !isSearchActive) {
                                        isSearchActive = true
                                        keyboardController?.show()
                                        true
                                    } else {
                                        false
                                    }
                                },
                    )
                }
            }
        }
        if (seerrActive && query.isNotBlank()) {
            item {
                TabRow(
                    selectedTabIndex = selectedTab,
                    tabs =
                        listOf(
                            context.getString(R.string.library),
                            context.getString(R.string.discover),
                        ),
                    focusRequesters = tabFocusRequesters,
                    onClick = { selectedTab = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (selectedTab == 0 || !seerrActive || query.isBlank()) {
            if (combinedMode) {
                searchResultRow(
                    title = context.getString(R.string.results),
                    result = combinedResults,
                    rowIndex = COMBINED_ROW,
                    position = position,
                    focusRequester = focusRequesters[COMBINED_ROW],
                    onClickItem = onClickItem,
                    onClickPosition = { position = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                searchResultRow(
                    title = context.getString(R.string.movies),
                    result = movies,
                    rowIndex = MOVIE_ROW,
                    position = position,
                    focusRequester = focusRequesters[MOVIE_ROW],
                    onClickItem = onClickItem,
                    onClickPosition = { position = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                searchResultRow(
                    title = context.getString(R.string.tv_shows),
                    result = series,
                    rowIndex = SERIES_ROW,
                    position = position,
                    focusRequester = focusRequesters[SERIES_ROW],
                    onClickItem = onClickItem,
                    onClickPosition = { position = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                searchResultRow(
                    title = context.getString(R.string.episodes),
                    result = episodes,
                    rowIndex = EPISODE_ROW,
                    position = position,
                    focusRequester = focusRequesters[EPISODE_ROW],
                    onClickItem = onClickItem,
                    onClickPosition = { position = it },
                    modifier = Modifier.fillMaxWidth(),
                    cardContent = @Composable { index, item, mod, onClick, onLongClick ->
                        EpisodeCard(
                            item = item,
                            onClick = {
                                position = RowColumn(EPISODE_ROW, index)
                                onClick.invoke()
                            },
                            onLongClick = onLongClick,
                            imageHeight = 140.dp,
                            modifier = mod.padding(horizontal = 8.dp),
                        )
                    },
                )
                searchResultRow(
                    title = context.getString(R.string.collections),
                    result = collections,
                    rowIndex = COLLECTION_ROW,
                    position = position,
                    focusRequester = focusRequesters[COLLECTION_ROW],
                    onClickItem = onClickItem,
                    onClickPosition = { position = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (selectedTab == 1 && seerrActive && query.isNotBlank()) {
            searchResultRow(
                title = context.getString(R.string.discover),
                result = seerrResults,
                rowIndex = SEERR_ROW,
                position = position,
                focusRequester = focusRequesters[SEERR_ROW],
                onClickItem = { _, _ ->
                    // no-op
                },
                onClickDiscover = { _, item ->
                    val dest =
                        if (item.jellyfinItemId != null && item.type.baseItemKind != null) {
                            Destination.MediaItem(
                                itemId = item.jellyfinItemId,
                                type = item.type.baseItemKind,
                            )
                        } else {
                            Destination.DiscoveredItem(item)
                        }
                    viewModel.navigationManager.navigateTo(dest)
                },
                onClickPosition = { position = it },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

fun LazyListScope.searchResultRow(
    title: String,
    result: SearchResult,
    rowIndex: Int,
    position: RowColumn,
    focusRequester: FocusRequester,
    onClickItem: (Int, BaseItem) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    onClickDiscover: ((Int, DiscoverItem) -> Unit)? = null,
    cardContent: @Composable (
        index: Int,
        item: BaseItem?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit = @Composable { index, item, mod, onClick, onLongClick ->
        SeasonCard(
            item = item,
            onClick = {
                onClickPosition.invoke(RowColumn(rowIndex, index))
                onClick.invoke()
            },
            onLongClick = onLongClick,
            imageHeight = Cards.height2x3,
            modifier = mod,
        )
    },
) {
    item {
        when (val r = result) {
            is SearchResult.Error -> {
                SearchResultPlaceholder(
                    title = title,
                    message = r.ex.localizedMessage ?: "Error occurred during search",
                    messageColor = MaterialTheme.colorScheme.error,
                    modifier = Modifier,
                )
            }

            SearchResult.NoQuery -> {
                // no-op
            }

            SearchResult.Searching -> {
                SearchResultPlaceholder(
                    title = title,
                    message = stringResource(R.string.searching),
                    modifier = modifier,
                )
            }

            is SearchResult.Success -> {
                if (r.items.isNotEmpty()) {
                    ItemRow(
                        title = title,
                        items = r.items,
                        onClickItem = onClickItem,
                        onLongClickItem = { _, _ -> },
                        modifier = modifier.focusRequester(focusRequester),
                        cardContent = cardContent,
                    )
                }
            }

            is SearchResult.SuccessSeerr -> {
                if (r.items.isNotEmpty()) {
                    ItemRow(
                        title = title,
                        items = r.items,
                        onClickItem = { index, item ->
                            onClickPosition.invoke(RowColumn(rowIndex, index))
                            onClickDiscover?.invoke(index, item)
                        },
                        onLongClickItem = { _, _ -> },
                        modifier = modifier.focusRequester(focusRequester),
                        cardContent = { index: Int, item: DiscoverItem?, mod: Modifier, onClick: () -> Unit, onLongClick: () -> Unit ->
                            DiscoverItemCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                showOverlay = true,
                                modifier = mod,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultPlaceholder(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    messageColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(bottom = 32.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = messageColor,
        )
    }
}
