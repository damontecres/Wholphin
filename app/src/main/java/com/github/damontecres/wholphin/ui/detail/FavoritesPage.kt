package com.github.damontecres.wholphin.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.DefaultForFavoritesFilterOptions
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilterOverride
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.components.ViewOptionsSquare
import com.github.damontecres.wholphin.ui.components.ViewOptionsWide
import com.github.damontecres.wholphin.ui.data.EpisodeSortOptions
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.SeriesSortOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.logTab
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder

@Composable
fun FavoritesPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    val uiPrefs = preferences.appPreferences.interfacePreferences
    val rememberedTabIndex =
        remember {
            preferencesViewModel.getRememberedTab(
                preferences,
                NavDrawerItem.Favorites.id,
                0,
            )
        }

    val tabs =
        listOf(
            stringResource(R.string.movies),
            stringResource(R.string.tv_shows),
            stringResource(R.string.episodes),
            stringResource(R.string.videos),
            stringResource(R.string.playlists),
            stringResource(R.string.people),
        )
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedTabIndex) {
        logTab("favorites", selectedTabIndex)
        preferencesViewModel.saveRememberedTab(
            preferences,
            NavDrawerItem.Favorites.id,
            selectedTabIndex,
        )
        preferencesViewModel.backdropService.clearBackdrop()
    }
    var showHeader by rememberSaveable { mutableStateOf(true) }

    val onClickItem = { item: BaseItem ->
        preferencesViewModel.navigationManager.navigateTo(item.destination())
    }

    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Column(
        modifier = modifier,
    ) {
        AnimatedVisibility(
            showHeader,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier =
                    Modifier
                        .padding(start = 32.dp, top = 16.dp, bottom = 16.dp),
                tabs = tabs,
                onClick = { selectedTabIndex = it },
            )
        }
        // TODO playEnabled = true for movies & episodes
        when (selectedTabIndex) {
            // Movies
            0 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = { _, item -> onClickItem.invoke(item) },
                    itemId = "${NavDrawerItem.Favorites.id}_movies",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    favorite = true,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = MovieSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    filterOptions = DefaultForFavoritesFilterOptions,
                )
            }

            // TV
            1 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = { _, item -> onClickItem.invoke(item) },
                    itemId = "${NavDrawerItem.Favorites.id}_series",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    favorite = true,
                                    includeItemTypes = listOf(BaseItemKind.SERIES),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = SeriesSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    filterOptions = DefaultForFavoritesFilterOptions,
                )
            }

            // Episodes
            2 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = { _, item -> onClickItem.invoke(item) },
                    itemId = "${NavDrawerItem.Favorites.id}_episodes",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    favorite = true,
                                    includeItemTypes = listOf(BaseItemKind.EPISODE),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = EpisodeSortOptions,
                    defaultViewOptions = ViewOptionsWide,
                    useSeriesForPrimary = false,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    filterOptions = DefaultForFavoritesFilterOptions,
                )
            }

            // Videos
            3 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = { _, item -> onClickItem.invoke(item) },
                    itemId = "${NavDrawerItem.Favorites.id}_videos",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    favorite = true,
                                    includeItemTypes = listOf(BaseItemKind.VIDEO),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = VideoSortOptions,
                    defaultViewOptions = ViewOptionsWide,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    filterOptions = DefaultForFavoritesFilterOptions,
                )
            }

            // Playlists
            4 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = { _, item -> onClickItem.invoke(item) },
                    itemId = "${NavDrawerItem.Favorites.id}_playlists",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    favorite = true,
                                    includeItemTypes = listOf(BaseItemKind.PLAYLIST),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = VideoSortOptions,
                    defaultViewOptions = ViewOptionsSquare,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    filterOptions = DefaultForFavoritesFilterOptions,
                )
            }

            // People
            5 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = { _, item -> onClickItem.invoke(item) },
                    itemId = "${NavDrawerItem.Favorites.id}_people",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    favorite = true,
                                    override = GetItemsFilterOverride.PERSON,
                                ),
                        ),
                    initialSortAndDirection =
                        SortAndDirection(
                            ItemSortBy.DEFAULT,
                            SortOrder.ASCENDING,
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = listOf(),
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    filterOptions = listOf(),
                )
            }

            else -> {
                ErrorMessage("Invalid tab index $selectedTabIndex", null)
            }
        }
    }
}
