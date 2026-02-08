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
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GenreCardGrid
import com.github.damontecres.wholphin.ui.components.RecommendedMovie
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.logTab
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun CollectionFolderMovie(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    val rememberedTabIndex =
        remember { preferencesViewModel.getRememberedTab(preferences, destination.itemId, 0) }

    val tabs =
        listOf(
            stringResource(R.string.recommended),
            stringResource(R.string.library),
            stringResource(R.string.collections),
            stringResource(R.string.genres),
        )
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    val focusRequester = remember { FocusRequester() }
    val tabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }

    val firstTabFocusRequester = remember { FocusRequester() }
//    LaunchedEffect(Unit) { firstTabFocusRequester.tryRequestFocus() }

    LaunchedEffect(selectedTabIndex) {
        logTab("movie", selectedTabIndex)
        preferencesViewModel.saveRememberedTab(preferences, destination.itemId, selectedTabIndex)
        preferencesViewModel.backdropService.clearBackdrop()
    }

    var showHeader by rememberSaveable { mutableStateOf(true) }

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
                        .padding(vertical = 16.dp)
                        .focusRequester(firstTabFocusRequester),
                tabs = tabs,
                onClick = { selectedTabIndex = it },
                focusRequesters = tabFocusRequesters,
            )
        }
        when (selectedTabIndex) {
            // Recommended
            0 -> {
                RecommendedMovie(
                    preferences = preferences,
                    parentId = destination.itemId,
                    onFocusPosition = { pos ->
                        showHeader = pos.row < 1
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                )
            }

            // Library
            1 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = { _, item ->
                        preferencesViewModel.navigationManager.navigateTo(item.destination())
                    },
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_library",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = MovieSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = true,
                    focusRequesterOnEmpty = tabFocusRequesters.getOrNull(selectedTabIndex),
                )
            }

            // Collections
            2 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = { _, item ->
                        preferencesViewModel.navigationManager.navigateTo(item.destination())
                    },
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_collection",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.BOX_SET),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = VideoSortOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    focusRequesterOnEmpty = tabFocusRequesters.getOrNull(selectedTabIndex),
                )
            }

            // Genres
            3 -> {
                GenreCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                )
            }

            else -> {
                ErrorMessage("Invalid tab index $selectedTabIndex", null)
            }
        }
    }
}
