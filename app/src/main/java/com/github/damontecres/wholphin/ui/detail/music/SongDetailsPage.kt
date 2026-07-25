package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.RequestOrRestoreFocus
import com.github.damontecres.wholphin.ui.components.ContextMenu
import com.github.damontecres.wholphin.ui.components.ContextMenuDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.MusicContextActions
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

@HiltViewModel(assistedFactory = SongViewModel.Factory::class)
class SongViewModel
    @AssistedInject
    constructor(
        @ApplicationContext context: Context,
        api: ApiClient,
        musicService: MusicService,
        navigationManager: NavigationManager,
        mediaManagementService: MediaManagementService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val imageUrlService: ImageUrlService,
        @Assisted itemId: UUID,
    ) : MusicViewModel(itemId, context, api, musicService, navigationManager, mediaManagementService) {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): SongViewModel
        }

        private val _state = MutableStateFlow(SongState.EMPTY)
        val state: StateFlow<SongState> = _state

        init {
            init()
            viewModelScope.launchDefault {
                mediaManagementService.collectCanDelete(state.map { it.song }) { canDelete ->
                    _state.update { it.copy(canDelete = canDelete) }
                }
            }
        }

        override fun init() {
            viewModelScope.launchIO {
                try {
                    val song =
                        api.userLibraryApi
                            .getItem(itemId = itemId)
                            .content
                            .let { BaseItem(it, false) }
                    val imageUrl = imageUrlService.getItemImageUrl(song, ImageType.PRIMARY)
                    _state.update {
                        it.copy(
                            song = song,
                            imageUrl = imageUrl,
                            loading = LoadingState.Success,
                        )
                    }
                } catch (ex: Exception) {
                    _state.update { it.copy(loading = LoadingState.Error(ex)) }
                }
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            // The toggle returns the updated user data, so no refetch of the song is needed
            val userData = favoriteWatchManager.setFavorite(itemId, favorite)
            _state.update { state ->
                state.copy(
                    song = state.song?.let { it.copy(data = it.data.copy(userData = userData)) },
                )
            }
        }
    }

data class SongState(
    val song: BaseItem?,
    val imageUrl: String?,
    val loading: LoadingState,
    val canDelete: Boolean = false,
) {
    companion object {
        val EMPTY = SongState(null, null, LoadingState.Pending)
    }
}

/**
 * Details page for a song that has no album, and therefore no album page to open.
 */
@Composable
fun SongDetailsPage(
    itemId: UUID,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SongViewModel =
        hiltViewModel<SongViewModel, SongViewModel.Factory>(
            creationCallback = { it.create(itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.collectAsState()
    var showContextMenu by remember { mutableStateOf<ContextMenu?>(null) }
    val moreDialogActions =
        remember {
            MusicContextActions(
                navigateTo = { viewModel.navigationManager.navigateTo(it) },
                onClickPlay = { _, song -> viewModel.play(song) },
                onClickPlayNext = { _, song -> viewModel.playNext(song) },
                onClickAddToQueue = { item -> viewModel.addToQueue(item, -1) },
                onClickFavorite = { id, favorite -> viewModel.setFavorite(id, favorite) },
                onClickAddPlaylist = { id ->
                    playlistViewModel.loadPlaylists()
                    showPlaylistDialog.makePresent(id)
                },
                onClickRemoveFromQueue = { _, _ -> },
                onDeleteItem = viewModel::deleteItem,
            )
        }
    when (val loading = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(loading, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            val song = state.song!!
            val bringIntoViewRequester = remember { BringIntoViewRequester() }
            val buttonsFocusRequester = remember { FocusRequester() }
            RequestOrRestoreFocus(buttonsFocusRequester)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .bringIntoViewRequester(bringIntoViewRequester)
                        .padding(bottom = 32.dp),
            ) {
                // Reusing AlbumHeader: a song's header has the same shape as an album's
                // (art, artist, title, quick details, genres, overview).
                AlbumHeader(
                    album = song,
                    imageUrl = state.imageUrl,
                    overviewOnClick = {},
                    bringIntoViewRequester = bringIntoViewRequester,
                    modifier = Modifier.fillMaxWidth(),
                )
                MusicExpandableButtons(
                    title = song.title ?: "",
                    actions =
                        remember(song) {
                            MusicButtonActions(
                                onClickPlay = { shuffled ->
                                    viewModel.play(song, shuffled = shuffled)
                                },
                                onClickInstantMix = { viewModel.startInstantMix(song.id) },
                                onClickFavorite = {
                                    viewModel.setFavorite(song.id, !song.favorite)
                                },
                                onClickMore = {
                                    showContextMenu =
                                        ContextMenu.ForMusic(
                                            fromLongClick = false,
                                            item = song,
                                            index = 0,
                                            canDelete =
                                                viewModel.canDelete(
                                                    song,
                                                    preferences.appPreferences,
                                                ),
                                            canRemoveFromQueue = false,
                                            actions = moreDialogActions,
                                        )
                                },
                                onConfirmDelete = { viewModel.deleteItem(song) },
                            )
                        },
                    favorite = song.favorite,
                    canDelete = state.canDelete,
                    showShuffle = false,
                    buttonOnFocusChanged = {},
                    modifier = Modifier.focusRequester(buttonsFocusRequester),
                )
            }
        }
    }
    showContextMenu?.let { contextMenu ->
        ContextMenuDialog(
            onDismissRequest = { showContextMenu = null },
            getMediaSource = null,
            contextMenu = contextMenu,
            preferredSubtitleLanguage = null,
        )
    }
    showPlaylistDialog.compose { songId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, songId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, songId)
                showPlaylistDialog.makeAbsent()
            },
            onSearch = playlistViewModel::loadPlaylists,
            elevation = 3.dp,
        )
    }
}
