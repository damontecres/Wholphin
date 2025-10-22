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
import androidx.compose.runtime.livedata.observeAsState
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabDefaults
import androidx.tv.material3.TabRow
import androidx.tv.material3.TabRowDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.detail.livetv.DvrSchedule
import com.github.damontecres.wholphin.ui.detail.livetv.TvGuideGrid
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.RememberTabManager
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LiveTvCollectionViewModel
    @Inject
    constructor(
        val api: ApiClient,
        val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        val rememberTabManager: RememberTabManager,
    ) : ViewModel(),
        RememberTabManager by rememberTabManager {
        val recordingFolders = MutableLiveData<List<TabId>>()

        init {
            viewModelScope.launchIO {
                val folders =
                    api.liveTvApi
                        .getRecordingFolders(userId = serverRepository.currentUser?.id)
                        .content.items
                        .map { TabId(it.name ?: "Recordings", it.id) }
                this@LiveTvCollectionViewModel.recordingFolders.setValueOnMain(folders)
            }
        }
    }

data class TabId(
    val title: String,
    val id: UUID,
)

@Composable
fun CollectionFolderLiveTv(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: LiveTvCollectionViewModel = hiltViewModel(),
) {
    val rememberedTabIndex =
        remember { viewModel.getRememberedTab(preferences, destination.itemId, 0) }
    val folders by viewModel.recordingFolders.observeAsState(listOf())

    val tabs =
        listOf(
            TabId("Guide", UUID.randomUUID()),
            TabId("DVR Schedule", UUID.randomUUID()),
        ) + folders

    var focusTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    val focusRequester = remember { FocusRequester() }

    val firstTabFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstTabFocusRequester.tryRequestFocus() }

    LaunchedEffect(selectedTabIndex) {
        viewModel.saveRememberedTab(preferences, destination.itemId, selectedTabIndex)
    }
    val onClickItem = { item: BaseItem ->
        viewModel.navigationManager.navigateTo(item.destination())
    }

    var showHeader by rememberSaveable { mutableStateOf(true) }

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
                    tabs.forEachIndexed { index, tabId ->
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
                                text = tabId.title,
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
                TvGuideGrid(
                    true,
                    Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester),
                )
            }
            1 -> {
                DvrSchedule(
                    true,
                    Modifier
                        .fillMaxSize()
                        .focusRequester(focusRequester),
                )
            }

            else -> {
                val folderIndex = selectedTabIndex - 2
                if (folderIndex in folders.indices) {
                    CollectionFolderGrid(
                        preferences = preferences,
                        onClickItem = onClickItem,
                        itemId = folders[folderIndex].id,
                        item = null,
                        initialFilter = GetItemsFilter(),
                        showTitle = showHeader,
                        recursive = false,
                        modifier =
                            Modifier
                                .padding(start = 16.dp),
                        positionCallback = { columns, position ->
                            showHeader = position < columns
                        },
                    )
                } else {
                    ErrorMessage("Invalid tab index $selectedTabIndex", null)
                }
            }
        }
    }
}
