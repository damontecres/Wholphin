package com.github.damontecres.wholphin.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.data.filter.DefaultForFavoritesFilterOptions
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderViewContent
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GridClickActions
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.components.defaultViewOptions
import com.github.damontecres.wholphin.ui.data.rememberSortOptions

@Composable
fun FavoritesPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val tabs = state.tabDetails
    val actions =
        remember {
            GridClickActions(
                onClickItem = { _, item -> viewModel.navigationManager.navigateTo(item.destination()) },
            )
        }

    var showTabs by rememberSaveable { mutableStateOf(true) }

//    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    TabbedPage(
        selectedTabIndex = state.tabIndex,
        updateSelectedTabIndex = viewModel::updateSelectedTabIndex,
        tabs = tabs,
        showTabs = showTabs,
        modifier = modifier,
    ) { tabIndex, tabDetails ->
        val type = favoriteOptions.getOrNull(tabIndex)
        val collectionState = state.favorites[type]

        if (type != null && collectionState != null) {
            val provider = remember { viewModel.createTypedProvider(type) }
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
        } else {
            ErrorMessage("Invalid tab index $tabIndex", null)
        }
    }
}
