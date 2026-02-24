package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.media3.common.util.UnstableApi
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.skipBackOnResume
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.rememberQueue
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.PlaybackKeyHandler
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.api.client.ApiClient
import kotlin.time.Duration.Companion.milliseconds

@HiltViewModel(assistedFactory = NowPlayingViewModel.Factory::class)
class NowPlayingViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @param:ApplicationContext private val context: Context,
        private val navigationManager: NavigationManager,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val backdropService: BackdropService,
        private val imageUrlService: ImageUrlService,
        private val musicService: MusicService,
        val userPreferencesService: UserPreferencesService,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(): NowPlayingViewModel
        }

        val controllerViewState =
            ControllerViewState(
                AppPreference.ControllerTimeout.defaultValue,
                true,
            )

        val state get() = musicService.state
        val player get() = musicService.player

        init {
        }

        fun reportInteraction() {
            controllerViewState.pulseControls()
        }
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
    val queue = rememberQueue(player, state.queueSize)
    val current = queue.getOrNull(state.currentIndex)

    val controllerViewState = viewModel.controllerViewState
    val preferences by viewModel.userPreferencesService.flow.collectAsState(AppPreferences.getDefaultInstance())

    val keyHandler =
        remember(preferences) {
            PlaybackKeyHandler(
                player = player,
                controlsEnabled = true,
                skipWithLeftRight = true,
                seekForward = preferences.playbackPreferences.skipForwardMs.milliseconds,
                seekBack = preferences.playbackPreferences.skipBackMs.milliseconds,
                controllerViewState = controllerViewState,
                updateSkipIndicator = {},
                skipBackOnResume = preferences.playbackPreferences.skipBackOnResume,
                onInteraction = viewModel::reportInteraction,
                oneClickPause = preferences.playbackPreferences.oneClickPause,
                onStop = {
                    player.stop()
//                    viewModel.navigationManager.goBack()
                },
                onPlaybackDialogTypeClick = { },
            )
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

    Box(
        modifier =
            modifier
                .onPreviewKeyEvent(keyHandler::onKeyEvent)
                .focusRequester(focusRequester)
                .focusable(),
    ) {
        AsyncImage(
            contentDescription = null,
            model = current?.imageUrl,
            modifier =
                Modifier
                    .padding(80.dp)
                    .fillMaxSize(),
        )

        AnimatedVisibility(controllerViewState.controlsVisible) {
            NowPlayingOverlay(
                state = state,
                player = player,
                current = current,
                queue = queue,
                controllerViewState = controllerViewState,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
