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
    val tabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }
    var focusTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        tabFocusRequesters.getOrNull(selectedTabIndex)?.tryRequestFocus()
    }
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
                selectedTabIndex = focusTabIndex,
                modifier =
                    Modifier
                        .padding(start = 32.dp, top = 16.dp, bottom = 16.dp)
                        .focusRestorer(tabFocusRequesters[0])
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
                                        .focusRequester(tabFocusRequesters[index]),
                            )
                        }
                    }
                },
            )
        }
        when (selectedTabIndex) {
            0 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = onClickItem,
                    itemId = null,
                    item = null,
                    initialFilter =
                        GetItemsFilter(
                            favorite = true,
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
            1 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    onClickItem = onClickItem,
                    itemId = null,
                    item = null,
                    initialFilter =
                        GetItemsFilter(
                            favorite = true,
                            includeItemTypes = listOf(BaseItemKind.SERIES),
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
                    itemId = null,
                    item = null,
                    initialFilter =
                        GetItemsFilter(
                            favorite = true,
                            includeItemTypes = listOf(BaseItemKind.EPISODE),
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
            else -> ErrorMessage("Invalid tab index $selectedTabIndex", null)
        }
    }
}
