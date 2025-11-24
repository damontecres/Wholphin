package com.github.damontecres.wholphin.ui.data

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.model.api.MediaType
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class AddPlaylistViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val playlistCreator: PlaylistCreator,
    ) : ViewModel() {
        val playlistState = MutableLiveData<PlaylistLoadingState>(PlaylistLoadingState.Pending)

        fun loadPlaylists(mediaType: MediaType?) {
            viewModelScope.launchIO {
                this@AddPlaylistViewModel.playlistState.setValueOnMain(PlaylistLoadingState.Loading)
                try {
                    val playlists = playlistCreator.getServerPlaylists(mediaType, viewModelScope)
                    this@AddPlaylistViewModel.playlistState.setValueOnMain(PlaylistLoadingState.Success(playlists))
                } catch (ex: Exception) {
                    playlistState.setValueOnMain(PlaylistLoadingState.Error(ex))
                }
            }
        }

        fun addToPlaylist(
            playlistId: UUID,
            itemId: UUID,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                playlistCreator.addToServerPlaylist(playlistId, itemId)
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
                }
            }
        }
    }
