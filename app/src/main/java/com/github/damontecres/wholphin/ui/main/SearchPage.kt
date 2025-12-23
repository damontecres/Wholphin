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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.EpisodeCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.components.VoiceSearchButton
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    ) : ViewModel() {
        val movies = MutableLiveData<SearchResult>(SearchResult.NoQuery)
        val series = MutableLiveData<SearchResult>(SearchResult.NoQuery)
        val episodes = MutableLiveData<SearchResult>(SearchResult.NoQuery)
        val collections = MutableLiveData<SearchResult>(SearchResult.NoQuery)

        private var currentQuery: String? = null

        fun search(query: String?) {
            if (currentQuery == query) {
                return
            }
            currentQuery = query
            if (query.isNotNullOrBlank()) {
                movies.value = SearchResult.Searching
                series.value = SearchResult.Searching
                episodes.value = SearchResult.Searching
                collections.value = SearchResult.Searching
                searchInternal(query, BaseItemKind.MOVIE, movies)
                searchInternal(query, BaseItemKind.SERIES, series)
                searchInternal(query, BaseItemKind.EPISODE, episodes)
                searchInternal(query, BaseItemKind.BOX_SET, collections)
            } else {
                movies.value = SearchResult.NoQuery
                series.value = SearchResult.NoQuery
                episodes.value = SearchResult.NoQuery
                collections.value = SearchResult.NoQuery
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
}

private const val MOVIE_ROW = 0
private const val COLLECTION_ROW = MOVIE_ROW + 1
private const val SERIES_ROW = COLLECTION_ROW + 1
private const val EPISODE_ROW = SERIES_ROW + 1

@Composable
fun SearchPage(
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val movies by viewModel.movies.observeAsState(SearchResult.NoQuery)
    val collections by viewModel.collections.observeAsState(SearchResult.NoQuery)
    val series by viewModel.series.observeAsState(SearchResult.NoQuery)
    val episodes by viewModel.episodes.observeAsState(SearchResult.NoQuery)

//    val query = rememberTextFieldState()
    var query by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    var position by rememberPosition()
    var pendingImmediateSearch by rememberSaveable { mutableStateOf(false) }
    var immediateSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }

    fun triggerImmediateSearch(searchQuery: String) {
        immediateSearchQuery = searchQuery
        pendingImmediateSearch = true
        viewModel.search(searchQuery)
    }

    LaunchedEffect(query) {
        if (immediateSearchQuery != query) {
            delay(750L)
            viewModel.search(query)
        }
        if (immediateSearchQuery == query) {
            immediateSearchQuery = null
        }
    }
    LaunchedEffect(Unit) {
        focusRequester.tryRequestFocus()
    }
    val onClickItem = { index: Int, item: BaseItem ->
        viewModel.navigationManager.navigateTo(item.destination())
    }
    // Resolve strings in Composable scope before LazyListScope
    val moviesTitle = stringResource(R.string.movies)
    val collectionsTitle = stringResource(R.string.collections)
    val tvShowsTitle = stringResource(R.string.tv_shows)
    val episodesTitle = stringResource(R.string.episodes)

    LaunchedEffect(pendingImmediateSearch, movies, collections, series, episodes) {
        if (pendingImmediateSearch) {
            if (listOf(movies, collections, series, episodes).any { it is SearchResult.Success }) {
                focusManager.moveFocus(FocusDirection.Next)
                pendingImmediateSearch = false
            }
        }
    }

    val scope = rememberCoroutineScope()

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

                BackHandler(isTextFieldFocused) {
                    if (isSearchActive) {
                        isSearchActive = false
                        keyboardController?.hide()
                    } else {
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                }
                val textFieldFocusRequester = remember { FocusRequester() }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .focusGroup()
                            .focusRestorer(textFieldFocusRequester)
                            .ifElse(
                                position.row < MOVIE_ROW,
                                Modifier.focusRequester(focusRequester),
                            ),
                ) {
                    SearchEditTextBox(
                        value = query,
                        onValueChange = {
                            isSearchActive = true
                            query = it
                        },
                        onSearchClick = {
                            triggerImmediateSearch(query)
                        },
                        readOnly = !isSearchActive,
                        modifier =
                            Modifier
                                .focusRequester(textFieldFocusRequester)
                                .onFocusChanged { focusState ->
                                    isTextFieldFocused = focusState.isFocused
                                    if (!focusState.isFocused) {
                                        isSearchActive = false
                                    }
                                }
                                // Use onPreviewKeyEvent for D-Pad navigation instead of
                                // interactionSource, as D-Pad uses KeyEvents not touch events
                                .onPreviewKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyUp) {
                                        when (keyEvent.key) {
                                            Key.DirectionCenter, Key.Enter -> {
                                                if (!isSearchActive) {
                                                    isSearchActive = true
                                                    // Explicitly show keyboard - required on TV devices
                                                    // where D-Pad events don't auto-trigger the keyboard
                                                    keyboardController?.show()
                                                    true
                                                } else {
                                                    false
                                                }
                                            }
                                            else -> false
                                        }
                                    } else {
                                        false
                                    }
                                },
                    )
                    VoiceSearchButton(
                        onSpeechResult = { spokenText ->
                            query = spokenText
                            triggerImmediateSearch(spokenText)
                            // Reclaim focus after voice search returns to prevent
                            // focus from jumping to the Navigation Drawer
                            scope.launch {
                                delay(100L)
                                textFieldFocusRequester.requestFocus()
                            }
                        },
                    )
                }
            }
        }
        searchResultRow(
            title = moviesTitle,
            result = movies,
            rowIndex = MOVIE_ROW,
            position = position,
            focusRequester = focusRequester,
            onClickItem = onClickItem,
            onClickPosition = { position = it },
            modifier = Modifier.fillMaxWidth(),
        )
        searchResultRow(
            title = collectionsTitle,
            result = collections,
            rowIndex = COLLECTION_ROW,
            position = position,
            focusRequester = focusRequester,
            onClickItem = onClickItem,
            onClickPosition = { position = it },
            modifier = Modifier.fillMaxWidth(),
        )
        searchResultRow(
            title = tvShowsTitle,
            result = series,
            rowIndex = SERIES_ROW,
            position = position,
            focusRequester = focusRequester,
            onClickItem = onClickItem,
            onClickPosition = { position = it },
            modifier = Modifier.fillMaxWidth(),
        )
        searchResultRow(
            title = episodesTitle,
            result = episodes,
            rowIndex = EPISODE_ROW,
            position = position,
            focusRequester = focusRequester,
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
                    modifier =
                        mod
                            .padding(horizontal = 8.dp)
                            .ifElse(
                                position.row == EPISODE_ROW && position.column == index,
                                Modifier.focusRequester(focusRequester),
                            ),
                )
            },
        )
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
            modifier =
                mod
                    .ifElse(
                        position.row == rowIndex && position.column == index,
                        Modifier.focusRequester(focusRequester),
                    ),
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
                if (r.items.isEmpty()) {
                    SearchResultPlaceholder(
                        title = title,
                        message = stringResource(R.string.no_results),
                        modifier = modifier,
                    )
                } else {
                    ItemRow(
                        title = title,
                        items = r.items,
                        onClickItem = onClickItem,
                        onLongClickItem = { _, _ -> },
                        modifier = modifier,
                        cardContent = cardContent,
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
