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
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.GenreCardGrid
import com.github.damontecres.wholphin.ui.components.RecommendedCollection
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.logTab
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun CollectionFolderCollection(
    collectionItem: BaseItemDto,
    isFavorite: Boolean,
    onToggleFavorite: (BaseItemDto) -> Unit,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    val tabs =
        listOf(
            stringResource(R.string.recommended),
            stringResource(R.string.content),
            stringResource(R.string.genres),
        )
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val tabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }

    val firstTabFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstTabFocusRequester.tryRequestFocus() }

    LaunchedEffect(selectedTabIndex) {
        logTab("collection", selectedTabIndex)
        preferencesViewModel.backdropService.clearBackdrop()
    }

    var showHeader by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    
    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            showHeader,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier =
                    Modifier
                        .padding(start = 32.dp, top = 16.dp, bottom = 16.dp)
                        .focusRequester(firstTabFocusRequester),
                tabs = tabs,
                focusRequesters = tabFocusRequesters,
                onClick = { selectedTabIndex = it },
            )
        }

        when (selectedTabIndex) {
            0 -> {
                RecommendedCollection(
                    parentId = collectionItem.id,
                    preferences = preferences,
                    onFocusPosition = { _ ->
                        // Handle focus position for backdrop updates
                    },
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                )
            }
            1 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = { _, item ->
                        preferencesViewModel.navigationManager.navigateTo(item.destination())
                    },
                    itemId = collectionItem.id,
                    viewModelKey = "${collectionItem.id}_content",
                    initialFilter = com.github.damontecres.wholphin.data.model.CollectionFolderFilter(
                        filter =
                            com.github.damontecres.wholphin.data.model.GetItemsFilter(
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            ),
                    ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = VideoSortOptions,
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
                )
            }
            2 -> {
                GenreCardGrid(
                    itemId = collectionItem.id,
                    includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                )
            }
        }
    }
}
