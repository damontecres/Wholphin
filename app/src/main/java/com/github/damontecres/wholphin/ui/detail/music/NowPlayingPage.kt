package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.playback.PlaybackButtons
import com.github.damontecres.wholphin.ui.roundSeconds
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.api.client.ApiClient
import kotlin.time.Duration.Companion.seconds

@HiltViewModel(assistedFactory = NowPlayingViewModel.Factory::class)
class NowPlayingViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val navigationManager: NavigationManager,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        private val imageUrlService: ImageUrlService,
        private val musicService: MusicService,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(): NowPlayingViewModel
        }

        val state get() = musicService.state
        val player get() = musicService.player
    }

@OptIn(UnstableApi::class)
@Composable
fun NowPlayingPage(
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel =
        hiltViewModel<NowPlayingViewModel, NowPlayingViewModel.Factory>(
            creationCallback = { it.create() },
        ),
) {
    val state by viewModel.state.collectAsState()
    val player = viewModel.player
    val current = state.queue.getOrNull(state.currentIndex)

    val playPauseState = rememberPlayPauseButtonState(player)
    val previousState = rememberPreviousButtonState(player)
    val nextState = rememberNextButtonState(player)

    Column(modifier = modifier.padding(16.dp)) {
        Text(
            text = current?.title ?: "",
        )
        Row {
            PlaybackButtons(
                player = player,
                initialFocusRequester = remember { FocusRequester() },
                onControllerInteraction = {},
                onPlaybackActionClick = {},
                showPlay = playPauseState.showPlay,
                previousEnabled = previousState.isEnabled,
                nextEnabled = nextState.isEnabled,
                seekBack = 10.seconds,
                skipBackOnResume = null,
                seekForward = 30.seconds,
            )
        }
        if (state.queue.isEmpty()) {
            Text("No items")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(state.queue) { index, song ->
                    SongListItem(
                        title = song.title,
                        artist = song.artistNames,
                        indexNumber = index + 1,
                        runtime = song.runtime?.roundSeconds,
                        showArtist = true,
                        isPlaying = state.currentIndex == index,
                        onClick = {
                            player.seekTo(index, 0L)
                        },
                        onLongClick = {},
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}
