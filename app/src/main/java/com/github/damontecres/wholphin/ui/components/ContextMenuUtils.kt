package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.nav.Destination
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID

/**
 * Simplifies showing a basic context menu
 */
@Composable
fun rememberContextMenu(
    preferences: UserPreferences,
    provider: ContextMenuProvider,
): ContextMenuUtils = remember(preferences, provider) { ContextMenuUtils(preferences, provider) }

class ContextMenuUtils(
    private val preferences: UserPreferences,
    private val provider: ContextMenuProvider,
) {
    private var showContextMenu by mutableStateOf<Pair<Int, BaseItem>?>(null)

    val isShowing: Boolean get() = showContextMenu != null

    /**
     * Show the context menu, typically from a long click
     */
    fun showContextMenu(
        position: Int,
        item: BaseItem,
    ) {
        showContextMenu = Pair(position, item)
    }

    /**
     * Composes the context menu when needed
     */
    @Composable
    fun Compose(playlistViewModel: AddPlaylistViewModel = hiltViewModel()) {
        var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
        var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
        val playlistState by playlistViewModel.playlistState.collectAsState()

        overviewDialog?.let { info ->
            ItemDetailsDialog(
                info = info,
                showFilePath = provider.isAdministrator(),
                onDismissRequest = { overviewDialog = null },
            )
        }
        showContextMenu?.let { (position, item) ->
            val contextActions =
                remember {
                    ContextMenuActions(
                        navigateTo = provider::navigateTo,
                        onClickWatch = { itemId, watched ->
                            provider.setWatched(position, itemId, watched)
                        },
                        onClickFavorite = { itemId, favorite ->
                            provider.setFavorite(position, itemId, favorite)
                        },
                        onClickAddPlaylist = { itemId ->
                            playlistViewModel.loadPlaylists(MediaType.VIDEO)
                            showPlaylistDialog.makePresent(itemId)
                        },
                        onSendMediaInfo = provider::sendReportFor,
                        onDeleteItem = { provider.deleteItem(position, it) },
                        onShowOverview = { overviewDialog = ItemDetailsDialogInfo(it) },
                        onChooseVersion = { _, _ ->
                            // Not supported on this page
                        },
                        onChooseTracks = { result ->
                            // Not supported on this page
                        },
                        onClearChosenStreams = {
                            // Not supported on this page
                        },
                        onClickAddToQueue = playlistViewModel::addToQueue,
                    )
                }
            val contextMenu =
                remember {
                    ContextMenu.ForBaseItem(
                        fromLongClick = true,
                        item = item,
                        chosenStreams = null,
                        showGoTo = true,
                        showStreamChoices = false,
                        canDelete = provider.canDelete(item, preferences.appPreferences),
                        canRemoveContinueWatching = false,
                        canRemoveNextUp = false,
                        actions = contextActions,
                    )
                }
            ContextMenuDialog(
                onDismissRequest = { showContextMenu = null },
                getMediaSource = null,
                contextMenu = contextMenu,
                preferredSubtitleLanguage = null,
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
}

interface ContextMenuProvider {
    fun isAdministrator(): Boolean

    fun navigateTo(destination: Destination)

    fun canDelete(
        item: BaseItem,
        appPreferences: AppPreferences,
    ): Boolean

    fun deleteItem(
        index: Int,
        item: BaseItem,
    )

    fun setWatched(
        position: Int,
        itemId: UUID,
        played: Boolean,
    )

    fun setFavorite(
        position: Int,
        itemId: UUID,
        favorite: Boolean,
    )

    fun sendReportFor(itemId: UUID)
}
