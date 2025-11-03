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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GenreCardGrid
import com.github.damontecres.wholphin.ui.components.RecommendedMovie
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.tryRequestFocus
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
    var focusTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    val focusRequester = remember { FocusRequester() }

    val firstTabFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstTabFocusRequester.tryRequestFocus() }

    LaunchedEffect(selectedTabIndex) {
        preferencesViewModel.saveRememberedTab(preferences, destination.itemId, selectedTabIndex)
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
                selectedTabIndex = focusTabIndex,
                modifier =
                    Modifier
                        .padding(start = 32.dp, top = 16.dp, bottom = 16.dp)
                        .focusRestorer(firstTabFocusRequester)
                        .onFocusChanged {
                            if (!it.isFocused) {
                                focusTabIndex = selectedTabIndex
                            }
                        },
                indicator =
                    @Composable { tabPositions, doesTabRowHaveFocus ->
                        tabPositions.getOrNull(focusTabIndex)?.let { currentTabPosition ->
//                        TabRowDefaults.PillIndicator(
//                            currentTabPosition = currentTabPosition,
//                            doesTabRowHaveFocus = doesTabRowHaveFocus,
//                        )
                            TabRowDefaults.UnderlinedIndicator(
                                currentTabPosition = currentTabPosition,
                                doesTabRowHaveFocus = doesTabRowHaveFocus,
                                activeColor = MaterialTheme.colorScheme.border,
                            )
                        }
                    },
                tabs = {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = focusTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            onFocus = { focusTabIndex = index },
                            colors =
                                TabDefaults.pillIndicatorTabColors(
                                    focusedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium,
                                modifier =
                                    Modifier
                                        .padding(8.dp)
                                        .ifElse(
                                            index == selectedTabIndex,
                                            Modifier.focusRequester(firstTabFocusRequester),
                                        ),
                            )
                        }
                    }
                },
            )
        }
        when (selectedTabIndex) {
            0 -> {
                RecommendedMovie(
                    preferences = preferences,
                    onClickItem = onClickItem,
                    parentId = destination.itemId,
                    onFocusPosition = { pos ->
                        showHeader = pos.row < 1
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
                    onClickItem = onClickItem,
                    itemId = destination.itemId,
                    item = destination.item,
                    initialFilter =
                        GetItemsFilter(
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                        ),
                    showTitle = false,
                    recursive = true,
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
                    itemId = destination.itemId,
                    item = destination.item,
                    initialFilter =
                        GetItemsFilter(
                            includeItemTypes = listOf(BaseItemKind.BOX_SET),
                        ),
                    showTitle = false,
                    recursive = true,
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

            3 -> {
                GenreCardGrid(
                    itemId = destination.itemId,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                )
            }
            else -> ErrorMessage("Invalid tab index $selectedTabIndex", null)
        }
    }
}
