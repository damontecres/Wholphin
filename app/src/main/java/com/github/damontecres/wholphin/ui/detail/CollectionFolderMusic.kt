package com.github.damontecres.wholphin.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GenreCardGrid
import com.github.damontecres.wholphin.ui.components.GridClickActions
import com.github.damontecres.wholphin.ui.components.RecommendedMusic
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.ViewOptionsSquare
import com.github.damontecres.wholphin.ui.data.AlbumSortOptions
import com.github.damontecres.wholphin.ui.data.ArtistSortOptions
import com.github.damontecres.wholphin.ui.data.SongSortOptions
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.logTab
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.RememberTabManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

@HiltViewModel(assistedFactory = CollectionFolderMusicViewModel.Factory::class)
class CollectionFolderMusicViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val musicService: MusicService,
        private val navigationManager: NavigationManager,
        val backdropService: BackdropService,
        private val rememberTabManager: RememberTabManager,
        @Assisted private val itemId: UUID,
    ) : ViewModel(),
        RememberTabManager by rememberTabManager {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): CollectionFolderMusicViewModel
        }

        fun play(item: BaseItem) {
            if (item.type == BaseItemKind.AUDIO) {
                viewModelScope.launchDefault {
                    musicService.setQueue(listOf(item), false)
                }
            }
        }

        fun onClick(
            index: Int,
            item: BaseItem,
        ) {
            if (item.type == BaseItemKind.AUDIO) {
                viewModelScope.launchDefault {
                    musicService.setQueue(listOf(item), false)
                }
            } else {
                navigationManager.navigateTo(item.destination())
            }
        }

        fun onClickPlayAll(shuffle: Boolean) {
            viewModelScope.launchDefault {
                val request =
                    GetItemsRequest(
                        userId = serverRepository.currentUser.value?.id,
                        parentId = itemId,
                        includeItemTypes = listOf(BaseItemKind.AUDIO),
                        recursive = true,
                    )
                val pager = ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
                musicService.setQueue(pager, 0, shuffle)
            }
        }

        fun onClickPlayRemoteButton(
            index: Int,
            item: BaseItem,
        ) {
            if (item.type == BaseItemKind.AUDIO) {
                viewModelScope.launchDefault {
                    musicService.setQueue(listOf(item), false)
                }
            }
        }
    }

@Composable
fun CollectionFolderMusic(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: CollectionFolderMusicViewModel =
        hiltViewModel<CollectionFolderMusicViewModel, CollectionFolderMusicViewModel.Factory>(
            creationCallback = { it.create(destination.itemId) },
        ),
) {
    val rememberedTabIndex =
        remember { viewModel.getRememberedTab(preferences, destination.itemId, 0) }

    val tabs =
        listOf(
            stringResource(R.string.recommended),
            stringResource(R.string.albums),
            stringResource(R.string.artists),
            stringResource(R.string.genres),
            stringResource(R.string.songs),
        )
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    val focusRequester = remember { FocusRequester() }
    val tabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }

    val firstTabFocusRequester = remember { FocusRequester() }
//    LaunchedEffect(Unit) { firstTabFocusRequester.tryRequestFocus() }

    LaunchedEffect(selectedTabIndex) {
        logTab("music", selectedTabIndex)
        viewModel.saveRememberedTab(preferences, destination.itemId, selectedTabIndex)
        viewModel.backdropService.clearBackdrop()
    }

    var showHeader by rememberSaveable { mutableStateOf(true) }

    val actions =
        remember {
            GridClickActions(
                onClickItem = viewModel::onClick,
                onLongClickItem = null,
                onClickPlayAll = viewModel::onClickPlayAll,
                onClickPlayRemoteButton = viewModel::onClickPlayRemoteButton,
            )
        }

    Column(
        modifier = modifier,
    ) {
        AnimatedVisibility(
            showHeader,
            enter = expandVertically(),
            exit = shrinkVertically(),
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
                RecommendedMusic(
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

            // Albums
            1 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    actions = actions,
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_albums",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = AlbumSortOptions,
                    defaultViewOptions = ViewOptionsSquare,
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

            // Artists
            2 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    actions = actions,
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_artists",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.MUSIC_ARTIST),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = ArtistSortOptions,
                    defaultViewOptions = ViewOptionsSquare,
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
                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                )
            }

            // Songs
            4 -> {
                CollectionFolderGrid(
                    preferences = preferences,
                    actions = actions,
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_songs",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.AUDIO),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = SongSortOptions,
                    defaultViewOptions = ViewOptionsSquare,
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

            else -> {
                ErrorMessage("Invalid tab index $selectedTabIndex", null)
            }
        }
    }
}
