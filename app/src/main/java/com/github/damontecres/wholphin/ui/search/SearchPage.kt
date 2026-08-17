package com.github.damontecres.wholphin.ui.search

import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.BannerCardWithTitle
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.ItemRowTitle
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.VoiceSearchButton
import com.github.damontecres.wholphin.ui.components.rememberContextMenu
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.detail.GridItemDetails
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.preferences.SwitchColors
import com.github.damontecres.wholphin.ui.titleStringRes
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

internal const val SEARCH_ROW = 0
internal const val TAB_ROW = SEARCH_ROW + 1
internal const val SEERR_ROW = TAB_ROW + 1
internal const val COMBINED_ROW = SEERR_ROW
internal const val RESULTS_START = SEERR_ROW + 1

@Composable
fun SearchPage(
    initialQuery: String,
    userPreferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val state by viewModel.state.collectAsState()

    // Start with current preferences, but collect updates when view options change
    val prefs =
        viewModel.userPreferencesService.flow
            .collectAsState(userPreferences)
            .value.appPreferences.interfacePreferences.searchPreferences
    val combinedMode = prefs.combinedSearchResults
    val voiceSearchButtonVisible = prefs.showVoiceSearchButton

//    val query = rememberTextFieldState()
    var query by rememberSaveable { mutableStateOf(initialQuery) }
    val focusRequesters =
        remember { List(RESULTS_START + searchableTypes.size) { FocusRequester() } }

    val seerrActive by viewModel.seerrActive.collectAsState(initial = false)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showViewOptions by rememberSaveable { mutableStateOf(false) }
    var searchClicked by rememberSaveable(query) { mutableStateOf(false) }
    var immediateSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }

    val position by viewModel.position.collectAsState()

    fun setPosition(pos: RowColumn) {
        Timber.v("pos=%s", pos)
        viewModel.position.value = pos
    }

    val contextMenu = rememberContextMenu(userPreferences, viewModel)

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
                delay(750.milliseconds)
                viewModel.search(query, combinedMode)
            }
        }
    }
    LaunchedEffect(Unit) {
        focusRequesters.getOrNull(position.row)?.tryRequestFocus()
    }
    val onClickItem = { _: Int, item: BaseItem ->
        viewModel.navigationManager.navigateTo(item.destination())
    }
    val onLongClickItem = { rowIndex: Int, index: Int, item: BaseItem ->
        setPosition(RowColumn(rowIndex, index))
        contextMenu.showContextMenu(index, item)
    }
    val onPlayItem = { _: Int, item: BaseItem ->
        viewModel.navigationManager.navigateTo(Destination.Playback(item))
    }

    val onClickDiscover = { _: Int, item: DiscoverItem ->
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
    }

    var showHeader by rememberSaveable { mutableStateOf(true) }
    val positionCallback = { columns: Int, index: Int ->
        showHeader = index < columns
    }
    val showTabs = seerrActive && query.isNotBlank() && showHeader && combinedMode
    val isLibraryTab = selectedTab == 0

    LaunchedEffect(seerrActive, query) {
        if (!seerrActive || query.isBlank()) {
            selectedTab = 0
        }
    }

    LaunchedEffect(
        searchClicked,
        state,
        combinedMode,
        selectedTab,
        seerrActive,
    ) {
        if (!searchClicked || position.row > TAB_ROW) return@LaunchedEffect

        withContext(WholphinDispatchers.IO) {
            // Want to focus on the first successful row after all the ones before it are finished searching
            val results =
                if (isLibraryTab) {
                    if (combinedMode) {
                        listOf(state.combinedResults)
                    } else {
                        listOf(
                            state.movies,
                            state.series,
                            state.episodes,
                            state.collections,
                            state.albums,
                            state.artists,
                            state.songs,
                        )
                    }
                } else {
                    listOf(state.seerrResults)
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
                                firstSuccess
                            }
                        } else {
                            SEERR_ROW
                        }
//                    setPosition(RowColumn(targetRow, 0))
                    onMain { focusRequesters.getOrNull(targetRow)?.tryRequestFocus() }
                }
            }
        }
    }

    val tabs =
        remember {
            listOf(
                TabDetails(R.string.library),
                TabDetails(R.string.discover),
            )
        }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        .focusGroup()
                        .focusRestorer(textFieldFocusRequester)
                        .focusRequester(focusRequesters[SEARCH_ROW]),
            ) {
                AnimatedVisibility(
                    visible = voiceSearchButtonVisible,
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                ) {
                    VoiceSearchButton(
                        onSpeechResult = { spokenText ->
                            query = spokenText
                            triggerImmediateSearch(spokenText)
                        },
                        voiceInputManager = viewModel.voiceInputManager,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }

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

                ExpandableFaButton(
                    title = R.string.view_options,
                    iconStringRes = R.string.fa_sliders,
                    onClick = { showViewOptions = true },
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = showTabs,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                tabs = tabs,
                onClick = {
                    selectedTab = it
                    val row =
                        when (selectedTab) {
                            0 -> COMBINED_ROW
                            else -> SEERR_ROW
                        }
                    setPosition(RowColumn(row, 0))
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp)
                        .onFocusChanged {
                            if (it.hasFocus) setPosition(RowColumn(TAB_ROW, 0))
                        },
            )
        }
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            SideEffect {
                Timber.v("isLibraryTab=%s, combinedMode=%s", isLibraryTab, combinedMode)
            }
            when {
                isLibraryTab && combinedMode -> {
                    SearchCombinedResults(
                        result = state.combinedResults,
                        focusRequester = focusRequesters[COMBINED_ROW],
                        onClickItem = onClickItem,
                        onLongClickItem = { index, item ->
                            onLongClickItem(COMBINED_ROW, index, item)
                        },
                        onPlayItem = onPlayItem,
                        onClickPosition = { setPosition(it) },
                        onClickDiscover = onClickDiscover,
                        positionCallback = positionCallback,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                !isLibraryTab && combinedMode -> {
                    SearchCombinedResults(
                        result = state.seerrResults,
                        focusRequester = focusRequesters[SEERR_ROW],
                        onClickItem = onClickItem,
                        onLongClickItem = { _, _ -> },
                        onPlayItem = onPlayItem,
                        onClickPosition = { setPosition(it) },
                        onClickDiscover = onClickDiscover,
                        positionCallback = positionCallback,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                isLibraryTab -> {
                    LazyColumn(
                        contentPadding =
                            PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 44.dp,
                            ),
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier.focusGroup(),
                    ) {
                        itemsIndexed(searchableTypes) { index, type ->
                            val rowIndex = RESULTS_START + index
                            val cardContent: @Composable (
                                index: Int,
                                item: BaseItem?,
                                modifier: Modifier,
                                onClick: () -> Unit,
                                onLongClick: () -> Unit,
                            ) -> Unit =
                                when (type) {
                                    BaseItemKind.EPISODE -> {
                                        { index, item, mod, onClick, onLongClick ->
                                            BannerCardWithTitle(
                                                title = item?.title,
                                                subtitle = item?.subtitle,
                                                item = item,
                                                onClick = {
                                                    setPosition(RowColumn(rowIndex, index))
                                                    onClick.invoke()
                                                },
                                                onLongClick = onLongClick,
                                                modifier = mod.padding(horizontal = 8.dp),
                                                cardHeight = Cards.heightEpisode,
                                            )
                                        }
                                    }

                                    BaseItemKind.MUSIC_ALBUM,
                                    BaseItemKind.MUSIC_ARTIST,
                                    BaseItemKind.AUDIO,
                                    -> {
                                        { index, item, mod, onClick, onLongClick ->
                                            SeasonCard(
                                                item = item,
                                                onClick = {
                                                    setPosition(RowColumn(rowIndex, index))
                                                    onClick.invoke()
                                                },
                                                onLongClick = onLongClick,
                                                imageHeight = Cards.heightEpisode,
                                                aspectRatio = AspectRatios.SQUARE,
                                                showImageOverlay = true,
                                                modifier = mod,
                                            )
                                        }
                                    }

                                    else -> {
                                        { index, item, mod, onClick, onLongClick ->
                                            SeasonCard(
                                                item = item,
                                                onClick = {
                                                    setPosition(RowColumn(rowIndex, index))
                                                    onClick.invoke()
                                                },
                                                onLongClick = onLongClick,
                                                imageHeight = Cards.height2x3,
                                                showImageOverlay = true,
                                                modifier = mod,
                                            )
                                        }
                                    }
                                }
                            val result = state.results.getOrDefault(type, SearchResult.Searching)
                            SearchRowResult(
                                title = type.titleStringRes,
                                result = result,
                                rowIndex = rowIndex,
                                position = position,
                                focusRequester = focusRequesters[rowIndex],
                                onClickItem = onClickItem,
                                onLongClickItem = { index, item ->
                                    onLongClickItem(rowIndex, index, item)
                                },
                                onClickPosition = { setPosition(it) },
                                modifier = Modifier.fillMaxWidth(),
                                cardContent = cardContent,
                            )
                        }

                        searchResultRow(
                            title = R.string.discover,
                            result = state.seerrResults,
                            rowIndex = SEERR_ROW,
                            position = position,
                            focusRequester = focusRequesters[SEERR_ROW],
                            onClickItem = onClickItem,
                            onLongClickItem = { _, _ -> },
                            onClickDiscover = onClickDiscover,
                            onClickPosition = { setPosition(it) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        contextMenu.Compose()
    }

    if (showViewOptions) {
        SearchViewOptionsDialog(
            combinedResults = combinedMode,
            onCombinedResultsChange = viewModel::setCombinedResults,
            voiceSearchButtonVisible = voiceSearchButtonVisible,
            onVoiceSearchButtonVisibleChange = viewModel::setVoiceSearchButtonVisible,
            onDismissRequest = { showViewOptions = false },
        )
    }
}

@Composable
fun SearchViewOptionsDialog(
    combinedResults: Boolean,
    onCombinedResultsChange: (Boolean) -> Unit,
    voiceSearchButtonVisible: Boolean,
    onVoiceSearchButtonVisibleChange: (Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.CENTER)

        Box(
            modifier =
                Modifier
                    .width(400.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        RoundedCornerShape(28.dp),
                    ).padding(24.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.view_options),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                ListItem(
                    selected = false,
                    headlineContent = {
                        Text(stringResource(R.string.combined_search_results))
                    },
                    supportingContent = {
                        Text(
                            if (combinedResults) {
                                stringResource(R.string.combined_search_results_on)
                            } else {
                                stringResource(R.string.combined_search_results_off)
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = combinedResults,
                            onCheckedChange = onCombinedResultsChange,
                            colors = SwitchColors(),
                        )
                    },
                    onClick = { onCombinedResultsChange(!combinedResults) },
                    modifier = Modifier.fillMaxWidth(),
                )

                ListItem(
                    selected = false,
                    headlineContent = {
                        Text(stringResource(R.string.show_voice_search_button))
                    },
                    supportingContent = {
                        Text(
                            if (voiceSearchButtonVisible) {
                                stringResource(R.string.visible_ui)
                            } else {
                                stringResource(R.string.hidden_ui)
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = voiceSearchButtonVisible,
                            onCheckedChange = onVoiceSearchButtonVisibleChange,
                            colors = SwitchColors(),
                        )
                    },
                    onClick = { onVoiceSearchButtonVisibleChange(!voiceSearchButtonVisible) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun SearchCombinedResults(
    result: SearchResult,
    focusRequester: FocusRequester,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onPlayItem: (Int, BaseItem) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    onClickDiscover: (Int, DiscoverItem) -> Unit,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (result) {
        SearchResult.NoQuery -> {}

        SearchResult.Searching -> {
            SearchResultPlaceholder(
                title = stringResource(R.string.results),
                message = stringResource(R.string.searching),
                modifier = modifier.padding(16.dp),
            )
        }

        is SearchResult.Error -> {
            SearchResultPlaceholder(
                title = stringResource(R.string.results),
                message = result.ex.localizedMessage ?: "Error occurred during search",
                messageColor = MaterialTheme.colorScheme.error,
                modifier = modifier.padding(16.dp),
            )
        }

        is SearchResult.Success -> {
            if (result.items.isEmpty()) {
                SearchResultPlaceholder(
                    title = stringResource(R.string.results),
                    message = stringResource(R.string.no_results),
                    modifier = modifier.padding(16.dp),
                )
            } else {
                SearchGrid(
                    items = result.items,
                    focusRequester = focusRequester,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    onPlayItem = onPlayItem,
                    onClickPosition = onClickPosition,
                    positionCallback = positionCallback,
                    cardContent = { details ->
                        GridCard(
                            item = details.item,
                            onClick = details.onClick,
                            onLongClick = details.onLongClick,
                            modifier = details.mod,
                            fillWidth = details.widthPx,
                        )
                    },
                    modifier = modifier,
                )
            }
        }

        is SearchResult.SuccessSeerr -> {
            if (result.items.isEmpty()) {
                SearchResultPlaceholder(
                    title = stringResource(R.string.results),
                    message = stringResource(R.string.no_results),
                    modifier = modifier.padding(16.dp),
                )
            } else {
                SearchGrid(
                    items = result.items,
                    focusRequester = focusRequester,
                    onClickItem = onClickDiscover,
                    onLongClickItem = { _, _ -> },
                    onPlayItem = { _, _ -> },
                    onClickPosition = onClickPosition,
                    positionCallback = positionCallback,
                    cardContent = { details ->
                        DiscoverItemCard(
                            item = details.item,
                            onClick = details.onClick,
                            onLongClick = details.onLongClick,
                            modifier = details.mod,
                        )
                    },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun <T : CardGridItem> SearchGrid(
    items: List<T?>,
    focusRequester: FocusRequester,
    onClickItem: (Int, T) -> Unit,
    onLongClickItem: (Int, T) -> Unit,
    onPlayItem: (Int, T) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    cardContent: @Composable (GridItemDetails<T>) -> Unit,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier,
    ) {
        ItemRowTitle(stringResource(R.string.results))

        CardGrid(
            pager = items,
            onClickItem = { index, item ->
                onClickPosition.invoke(RowColumn(COMBINED_ROW, index))
                onClickItem.invoke(index, item)
            },
            onLongClickItem = onLongClickItem,
            onClickPlay = { index, item ->
                onClickPosition.invoke(RowColumn(COMBINED_ROW, index))
                onPlayItem.invoke(index, item)
            },
            letterPosition = { -1 },
            gridFocusRequester = focusRequester,
            showJumpButtons = false,
            showLetterButtons = false,
            positionCallback = positionCallback,
            modifier = Modifier.fillMaxSize(),
            cardContent = cardContent,
        )
    }
}

fun LazyListScope.searchResultRow(
    @StringRes title: Int,
    result: SearchResult,
    rowIndex: Int,
    position: RowColumn,
    focusRequester: FocusRequester,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
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
            showImageOverlay = true,
            modifier = mod,
        )
    },
) {
    item {
        when (val r = result) {
            is SearchResult.Error -> {
                SearchResultPlaceholder(
                    title = stringResource(title),
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
                    title = stringResource(title),
                    message = stringResource(R.string.searching),
                    modifier = modifier,
                )
            }

            is SearchResult.Success -> {
                if (r.items.isNotEmpty()) {
                    ItemRow(
                        title = stringResource(title),
                        items = r.items,
                        onClickItem = onClickItem,
                        onLongClickItem = onLongClickItem,
                        modifier = modifier.focusRequester(focusRequester),
                        cardContent = cardContent,
                    )
                }
            }

            is SearchResult.SuccessSeerr -> {
                if (r.items.isNotEmpty()) {
                    ItemRow(
                        title = stringResource(title),
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
fun SearchRowResult(
    @StringRes title: Int,
    result: SearchResult,
    rowIndex: Int,
    position: RowColumn,
    focusRequester: FocusRequester,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    onClickDiscover: ((Int, DiscoverItem) -> Unit)? = null,
    cardContent: @Composable (
        index: Int,
        item: BaseItem?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit,
) {
    when (val r = result) {
        is SearchResult.Error -> {
            SearchResultPlaceholder(
                title = stringResource(title),
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
                title = stringResource(title),
                message = stringResource(R.string.searching),
                modifier = modifier,
            )
        }

        is SearchResult.Success -> {
            if (r.items.isNotEmpty()) {
                ItemRow(
                    title = stringResource(title),
                    items = r.items,
                    onClickItem = onClickItem,
                    onLongClickItem = onLongClickItem,
                    modifier = modifier.focusRequester(focusRequester),
                    cardContent = cardContent,
                )
            }
        }

        is SearchResult.SuccessSeerr -> {
            if (r.items.isNotEmpty()) {
                ItemRow(
                    title = stringResource(title),
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
