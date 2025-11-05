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
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.data.EpisodeSortOptions
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.SeriesSortOptions
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.BaseItemKind

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
        )
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(selectedTabIndex) {
        preferencesViewModel.saveRememberedTab(
            preferences,
            NavDrawerItem.Favorites.id,
            selectedTabIndex,
        )
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
        when (selectedTabIndex) {
            0 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = onClickItem,
                    itemId = "${NavDrawerItem.Favorites.id}_movies",
                    initialFilter =
                        GetItemsFilter(
                            favorite = true,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = MovieSortOptions,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                )
            }
            1 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = onClickItem,
                    itemId = "${NavDrawerItem.Favorites.id}_series",
                    initialFilter =
                        GetItemsFilter(
                            favorite = true,
                            includeItemTypes = listOf(BaseItemKind.SERIES),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = SeriesSortOptions,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                )
            }
            2 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = onClickItem,
                    itemId = "${NavDrawerItem.Favorites.id}_episodes",
                    initialFilter =
                        GetItemsFilter(
                            favorite = true,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = EpisodeSortOptions,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                )
            }
            else -> ErrorMessage("Invalid tab index $selectedTabIndex", null)
        }
    }
}
