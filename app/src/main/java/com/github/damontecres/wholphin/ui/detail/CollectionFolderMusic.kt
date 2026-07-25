package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilterOverride
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.CollectionFolderView
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GenreCardGrid
import com.github.damontecres.wholphin.ui.components.GridClickActions
import com.github.damontecres.wholphin.ui.components.RecommendedMusic
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.components.ViewOptionsSquare
import com.github.damontecres.wholphin.ui.data.AlbumSortOptions
import com.github.damontecres.wholphin.ui.data.ArtistSortOptions
import com.github.damontecres.wholphin.ui.data.SongSortOptions
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
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
        @Assisted private val itemId: UUID,
    ) : ViewModel() {
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
                        userId = serverRepository.currentUser?.id,
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
    val tabs =
        remember {
            listOf(
                TabDetails(R.string.recommended),
                TabDetails(R.string.albums),
                TabDetails(R.string.artists),
                TabDetails(R.string.genres),
                TabDetails(R.string.songs),
            )
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
    TabbedPage(
        itemId = destination.itemId.toString(),
        tabs = tabs,
        modifier = modifier,
        showTabs = showHeader,
    ) { tabIndex, tabDetails ->
        when (tabIndex) {
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
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            // Albums
            1 -> {
                CollectionFolderView(
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
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = true,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Artists
            2 -> {
                CollectionFolderView(
                    preferences = preferences,
                    actions = actions,
                    itemId = destination.itemId,
                    viewModelKey = "${destination.itemId}_artists",
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    override = GetItemsFilterOverride.ARTIST,
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = ArtistSortOptions,
                    defaultViewOptions = ViewOptionsSquare,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = false,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            // Genres
            3 -> {
                GenreCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                    collectionType = CollectionType.MUSIC,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            // Songs
            4 -> {
                CollectionFolderView(
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
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    playEnabled = true,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            else -> {
                ErrorMessage("Invalid tab index $tabIndex", null)
            }
        }
    }
}
