package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.filter.DefaultForFavoritesFilterOptions
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.components.CollectionFolderViewContent
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GridClickActions
import com.github.damontecres.wholphin.ui.components.KeyedTabbedPage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.defaultViewOptions
import com.github.damontecres.wholphin.ui.components.rememberContextMenu
import com.github.damontecres.wholphin.ui.data.rememberSortOptions
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun FavoritesPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    OneTimeLaunchedEffect { viewModel.init() }
    val state by viewModel.state.collectAsState()

    var showTabs by rememberSaveable { mutableStateOf(true) }

    when (val s = state.loadingState) {
        is FavoritesLoadingState.Error -> {
            ErrorMessage(s.message, s.exception, modifier)
        }

        FavoritesLoadingState.Loading,
        FavoritesLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        FavoritesLoadingState.NoFavorites -> {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    modifier
                        .focusRequester(focusRequester)
                        .focusable(),
            ) {
                Text(
                    text = "No favorites found!",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier,
                )
            }
        }

        FavoritesLoadingState.Success -> {
//    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
            KeyedTabbedPage(
                selectedTabKey = state.tabKey,
                updateSelectedTabKey = viewModel::updateSelectedTabKey,
                tabs = state.tabs,
                showTabs = showTabs,
                isShowClock = state.isShowClock,
                modifier = modifier,
            ) { type, tabDetails ->
                val collectionState = state.favorites[type]
                if (collectionState != null) {
                    val provider = remember(type) { viewModel.createTypedProvider(type) }
                    val contextMenu = rememberContextMenu(preferences, provider)
                    val actions =
                        remember(type) {
                            GridClickActions(
                                onClickItem = { _, item ->
                                    viewModel.navigationManager.navigateTo(
                                        item.destination(),
                                    )
                                },
                                onLongClickItem = contextMenu::showContextMenu,
                            )
                        }
                    CollectionFolderViewContent(
                        preferences = preferences,
                        state = collectionState,
                        savedPosition = 0,
                        itemId = remember { viewModel.libraryDisplayItemId(type) },
                        initialFilter = CollectionFolderFilter(),
                        recursive = true,
                        actions = actions,
                        sortOptions = rememberSortOptions(type),
                        // TODO playEnabled = true for movies & episodes
                        playEnabled = false,
                        defaultViewOptions = type.defaultViewOptions,
                        viewActions = provider,
                        provider = provider,
                        showTitle = false,
                        positionCallback = { columns, index ->
                            showTabs = index < columns
                        },
                        focusRequesterOnEmpty = null,
                        filterOptions = DefaultForFavoritesFilterOptions,
                    )

                    contextMenu.Compose()
                } else {
                    ErrorMessage("Invalid tab $type", null)
                }
            }
        }
    }
}
