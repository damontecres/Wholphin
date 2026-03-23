package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.ConfirmDeleteDialog
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.MoreDialogActions
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.buildMoreDialogItemsForHome
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.LoadingState
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID

@Composable
fun CollectionDetails(
    preferences: UserPreferences,
    itemId: UUID,
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel =
        hiltViewModel<CollectionViewModel, CollectionViewModel.Factory>(
            creationCallback = { it.create(itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()

    // Dialogs
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)
    var showDeleteDialog by remember { mutableStateOf<Pair<RowColumn, BaseItem>?>(null) }
    var showViewOptionsDialog by remember { mutableStateOf(false) }

    // Actions
    val onClickItem =
        remember {
            { _: RowColumn, item: BaseItem -> viewModel.navigate(item.destination()) }
        }
    val onLongClickItem =
        remember {
            { position: RowColumn, item: BaseItem ->
                val dialogItems =
                    buildMoreDialogItemsForHome(
                        context = context,
                        item = item,
                        seriesId = item.data.seriesId,
                        playbackPosition = item.playbackPosition,
                        watched = item.played,
                        favorite = item.favorite,
                        canDelete = viewModel.canDelete(item, preferences.appPreferences),
                        actions =
                            MoreDialogActions(
                                navigateTo = viewModel::navigate,
                                onClickWatch = { itemId, watched ->
                                    viewModel.setWatched(itemId, watched, position)
                                },
                                onClickFavorite = { itemId, favorite ->
                                    viewModel.setFavorite(itemId, favorite, position)
                                },
                                onClickAddPlaylist = { itemId ->
                                    playlistViewModel.loadPlaylists(MediaType.VIDEO)
                                    showPlaylistDialog.makePresent(itemId)
                                },
                                onSendMediaInfo = viewModel.mediaReportService::sendReportFor,
                                onClickDelete = { item -> showDeleteDialog = Pair(position, item) },
                            ),
                    )
                moreDialog =
                    DialogParams(
                        fromLongClick = true,
                        title = item.title ?: "",
                        items = dialogItems,
                    )
            }
        }
    val onSortChange =
        remember {
            { sort: SortAndDirection -> viewModel.changeSort(sort) }
        }
    val onFilterChange =
        remember {
            { filter: GetItemsFilter -> viewModel.changeFilter(filter) }
        }
    val onClickPlay = { _: RowColumn, item: BaseItem ->
        viewModel.navigate(Destination.Playback(item = item))
    }
    val onClickPlayAll =
        remember {
            { shuffle: Boolean ->
                TODO()
            }
        }
    val onChangeBackdrop = remember { { item: BaseItem -> viewModel.updateBackdrop(item) } }
    val onClickViewOptions = remember { { showViewOptionsDialog = true } }

    when (val s = state.loadingState) {
        is LoadingState.Error -> {
            ErrorMessage(s, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier,
            ) {
                Text(
                    text = state.collection?.title ?: "",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(.85f),
                )
                if (state.viewOptions.mixed) {
                    CollectionMixedGrid(
                        preferences = preferences,
                        state = state,
                        onClickItem = onClickItem,
                        onLongClickItem = onLongClickItem,
                        onSortChange = onSortChange,
                        onClickPlay = onClickPlay,
                        onClickPlayAll = onClickPlayAll,
                        onChangeBackdrop = onChangeBackdrop,
                        onFilterChange = onFilterChange,
                        getPossibleFilterValues = viewModel::getPossibleFilterValues,
                        letterPosition = viewModel::letterPosition,
                        onClickViewOptions = onClickViewOptions,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CollectionRows(
                        preferences = preferences,
                        state = state,
                        onClickItem = onClickItem,
                        onLongClickItem = onLongClickItem,
                        onSortChange = onSortChange,
                        onClickPlay = onClickPlay,
                        onClickPlayAll = onClickPlayAll,
                        onChangeBackdrop = onChangeBackdrop,
                        onFilterChange = onFilterChange,
                        getPossibleFilterValues = viewModel::getPossibleFilterValues,
                        onClickViewOptions = onClickViewOptions,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
    if (showViewOptionsDialog) {
        CollectionViewOptionsDialog(
            viewOptions = state.viewOptions,
            onDismissRequest = { showViewOptionsDialog = false },
            onViewOptionsChange = viewModel::changeViewOptions,
        )
    }
    moreDialog?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { moreDialog = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
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
    showDeleteDialog?.let { (position, item) ->
        ConfirmDeleteDialog(
            itemTitle = item.title ?: "",
            onCancel = { showDeleteDialog = null },
            onConfirm = {
                viewModel.deleteItem(item, position)
                showDeleteDialog = null
            },
        )
    }
}
