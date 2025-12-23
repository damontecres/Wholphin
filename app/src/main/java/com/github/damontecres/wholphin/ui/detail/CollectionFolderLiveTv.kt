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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.detail.livetv.DvrSchedule
import com.github.damontecres.wholphin.ui.detail.livetv.TvGuideGrid
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.logTab
import com.github.damontecres.wholphin.ui.nav.Destination
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
        val backdropService: BackdropService,
    ) : ViewModel(),
        RememberTabManager by rememberTabManager {
        val recordingFolders = MutableLiveData<List<TabId>>()

        init {
            viewModelScope.launchIO {
                val folders =
                    api.liveTvApi
                        .getRecordingFolders(userId = serverRepository.currentUser.value?.id)
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
            TabId(stringResource(R.string.tv_guide), UUID.randomUUID()),
            TabId(stringResource(R.string.tv_dvr_schedule), UUID.randomUUID()),
        ) + folders

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    val focusRequester = remember { FocusRequester() }

    val firstTabFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstTabFocusRequester.tryRequestFocus() }

    LaunchedEffect(selectedTabIndex) {
        logTab("livetv", selectedTabIndex)
        viewModel.saveRememberedTab(preferences, destination.itemId, selectedTabIndex)
        viewModel.backdropService.clearBackdrop()
    }
    val onClickItem = { position: Int, item: BaseItem ->
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
                selectedTabIndex = selectedTabIndex,
                modifier =
                    Modifier
                        .padding(start = 32.dp, top = 16.dp, bottom = 16.dp)
                        .focusRequester(firstTabFocusRequester),
                tabs = tabs.map { it.title },
                onClick = { selectedTabIndex = it },
            )
        }
        when (selectedTabIndex) {
            0 -> {
                TvGuideGrid(
                    true,
                    onRowPosition = {
                        showHeader = it <= 0
                    },
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
                        initialFilter = CollectionFolderFilter(),
                        showTitle = false,
                        recursive = false,
                        sortOptions = VideoSortOptions,
                        modifier =
                            Modifier
                                .padding(start = 16.dp),
                        positionCallback = { columns, position ->
                            showHeader = position < columns
                        },
                        playEnabled = false,
                        defaultViewOptions = ViewOptions(),
                    )
                } else {
                    ErrorMessage("Invalid tab index $selectedTabIndex", null)
                }
            }
        }
    }
}
