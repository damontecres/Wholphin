package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.HomePageContent
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.FavoriteWatchManager
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID

abstract class RecommendedViewModel(
    val navigationManager: NavigationManager,
    val favoriteWatchManager: FavoriteWatchManager,
) : ViewModel() {
    abstract fun init()

    abstract val rows: MutableStateFlow<MutableList<HomeRowLoadingState>>

    val loading = MutableLiveData<LoadingState>(LoadingState.Loading)

    fun refreshItem(
        position: RowColumn,
        itemId: UUID,
    ) {
        viewModelScope.launchIO {
            val row = rows.value.getOrNull(position.row)
            if (row is HomeRowLoadingState.Success) {
                (row.items as? ApiRequestPager<*>)?.refreshItem(position.column, itemId)
            }
        }
    }

    fun setWatched(
        position: RowColumn,
        itemId: UUID,
        watched: Boolean,
    ) {
        viewModelScope.launchIO {
            favoriteWatchManager.setWatched(itemId, watched)
            refreshItem(position, itemId)
        }
    }

    fun setFavorite(
        position: RowColumn,
        itemId: UUID,
        watched: Boolean,
    ) {
        viewModelScope.launchIO {
            favoriteWatchManager.setFavorite(itemId, watched)
            refreshItem(position, itemId)
        }
    }
}

@Composable
fun RecommendedContent(
    preferences: UserPreferences,
    viewModel: RecommendedViewModel,
    modifier: Modifier = Modifier,
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    onFocusPosition: ((RowColumn) -> Unit)? = null,
) {
    val context = LocalContext.current
    var moreDialog by remember { mutableStateOf<Optional<RowColumnItem>>(Optional.absent()) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)

    OneTimeLaunchedEffect {
        viewModel.init()
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val rows by viewModel.rows.collectAsState()

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)

        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()

        LoadingState.Success ->
            HomePageContent(
                homeRows = rows,
                onClickItem = { _, item ->
                    viewModel.navigationManager.navigateTo(item.destination())
                },
                onLongClickItem = { position, item ->
                    moreDialog.makePresent(RowColumnItem(position, item))
                },
                onFocusPosition = onFocusPosition,
                showClock = preferences.appPreferences.interfacePreferences.showClock,
                modifier = modifier,
            )
    }
    moreDialog.compose { (position, item) ->
        DialogPopup(
            showDialog = true,
            title = item.title ?: "",
            dialogItems =
                buildMoreDialogItemsForHome(
                    context = context,
                    item = item,
                    seriesId = null,
                    playbackPosition = item.playbackPosition,
                    watched = item.played,
                    favorite = item.favorite,
                    actions =
                        MoreDialogActions(
                            navigateTo = { viewModel.navigationManager.navigateTo(it) },
                            onClickWatch = { itemId, watched ->
                                viewModel.setWatched(position, itemId, watched)
                            },
                            onClickFavorite = { itemId, watched ->
                                viewModel.setFavorite(position, itemId, watched)
                            },
                            onClickAddPlaylist = {
                                playlistViewModel.loadPlaylists(MediaType.VIDEO)
                                showPlaylistDialog.makePresent(it)
                            },
                        ),
                ),
            onDismissRequest = { moreDialog.makeAbsent() },
            dismissOnClick = true,
            waitToLoad = true,
        )
    }
    showPlaylistDialog.compose { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog.makeAbsent()
            },
            elevation = 3.dp,
        )
    }
}

private data class RowColumnItem(
    val position: RowColumn,
    val item: BaseItem,
)
