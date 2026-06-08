package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.DefaultForFavoritesFilterOptions
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilterOverride
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.CollectionFolderView
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GridClickActions
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.components.ViewOptionsSquare
import com.github.damontecres.wholphin.ui.components.ViewOptionsWide
import com.github.damontecres.wholphin.ui.data.EpisodeSortOptions
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.SeriesSortOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel
    @Inject
    constructor(
        val navigationManager: NavigationManager,
    ) : ViewModel()

@Composable
fun FavoritesPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val tabs =
        remember {
            listOf(
                TabDetails(R.string.movies),
                TabDetails(R.string.tv_shows),
                TabDetails(R.string.episodes),
                TabDetails(R.string.videos),
                TabDetails(R.string.playlists),
                TabDetails(R.string.people),
            )
        }
    val actions =
        remember {
            GridClickActions(
                onClickItem = { _, item -> viewModel.navigationManager.navigateTo(item.destination()) },
            )
        }

    var showHeader by rememberSaveable { mutableStateOf(true) }

//    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    TabbedPage(
        itemId = "favorites",
        tabs = tabs,
        showTabs = showHeader,
        modifier = modifier,
    ) { tabIndex, tabDetails ->
        // TODO playEnabled = true for movies & episodes
        when (tabIndex) {
            // Movies
            0 -> {
                CollectionFolderView(
                    preferences = preferences,
                    actions = actions,
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
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    filterOptions = DefaultForFavoritesFilterOptions,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // TV
            1 -> {
                CollectionFolderView(
                    preferences = preferences,
                    actions = actions,
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
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    filterOptions = DefaultForFavoritesFilterOptions,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Episodes
            2 -> {
                CollectionFolderView(
                    preferences = preferences,
                    actions = actions,
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
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    filterOptions = DefaultForFavoritesFilterOptions,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Videos
            3 -> {
                CollectionFolderView(
                    preferences = preferences,
                    actions = actions,
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
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    filterOptions = DefaultForFavoritesFilterOptions,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Playlists
            4 -> {
                CollectionFolderView(
                    preferences = preferences,
                    actions = actions,
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
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    filterOptions = DefaultForFavoritesFilterOptions,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // People
            5 -> {
                CollectionFolderView(
                    preferences = preferences,
                    actions = actions,
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
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    filterOptions = listOf(),
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            else -> {
                ErrorMessage("Invalid tab index $tabIndex", null)
            }
        }
    }
}
