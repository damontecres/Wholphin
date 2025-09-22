package com.github.damontecres.dolphin.ui.playback

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.tryRequestFocus
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager

@OptIn(UnstableApi::class)
@Composable
fun PlaybackContent(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    destination: Destination.Playback,
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel = hiltViewModel(),
) {
    LaunchedEffect(destination.itemId) {
        viewModel.init(destination.itemId)
    }
    val player = viewModel.player
    val stream by viewModel.stream.observeAsState(null)
    if (stream == null) {
        // TODO loading
    } else {
        stream?.let {
            var contentScale by remember { mutableStateOf(ContentScale.Fit) }
            var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
            LaunchedEffect(playbackSpeed) { player.setPlaybackSpeed(playbackSpeed) }

            val presentationState = rememberPresentationState(player)
            val scaledModifier =
                Modifier.resizeWithContentScale(contentScale, presentationState.videoSizeDp)
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.tryRequestFocus()
            }
            Box(
                modifier
                    .background(Color.Black)
                    .onKeyEvent {
                        // TODO handle key events for playback controls
                        false
                    }.focusRequester(focusRequester)
                    .focusable(),
            ) {
                PlayerSurface(
                    player = player,
                    surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                    modifier = scaledModifier,
                )
                if (presentationState.coverSurface) {
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Color.Black),
                    )
                }
            }
        }
    }
}
