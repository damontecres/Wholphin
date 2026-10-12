package com.github.damontecres.wholphin.ui.data

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.detail.music.addToQueue
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

/**
 * A supplementary [ViewModel] for adding items to a server playlist
 * @see com.github.damontecres.wholphin.ui.detail.PlaylistDialog
 */
@HiltViewModel
class AddPlaylistViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val playlistCreator: PlaylistCreator,
        private val musicService: MusicService,
    ) : ViewModel() {
        val playlistState = MutableStateFlow<PlaylistLoadingState>(PlaylistLoadingState.Pending)

        fun loadPlaylists(query: String = "") {
            viewModelScope.launchIO {
                this@AddPlaylistViewModel.playlistState.value = PlaylistLoadingState.Loading
                try {
                    val playlists = playlistCreator.getServerPlaylists(query, null)
                    this@AddPlaylistViewModel.playlistState.value =
                        PlaylistLoadingState.Success(playlists, query)
                } catch (ex: Exception) {
                    playlistState.value = PlaylistLoadingState.Error(ex)
                }
            }
        }

        fun addToPlaylist(
            playlistId: UUID,
            itemId: UUID,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                try {
                    playlistCreator.addToServerPlaylist(playlistId, itemId)
                    showToast(context, context.getString(R.string.success), Toast.LENGTH_SHORT)
                } catch (ex: Exception) {
                    Timber.e(ex, "Error adding %s to playlist %s", itemId, playlistId)
                    showToast(context, "Error: ${ex.localizedMessage}", Toast.LENGTH_SHORT)
                }
            }
        }

        fun createPlaylistAndAddItem(
            playlistName: String,
            itemId: UUID,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                val playlistId = playlistCreator.createServerPlaylist(playlistName, listOf(itemId))
                if (playlistId == null) {
                    showToast(context, "Error creating playlist", Toast.LENGTH_LONG)
                } else {
                    showToast(context, context.getString(R.string.success), Toast.LENGTH_SHORT)
                }
            }
        }

        fun addToQueue(item: BaseItem) {
            viewModelScope.launchDefault {
                addToQueue(api, musicService, item, -1)
            }
        }
    }
