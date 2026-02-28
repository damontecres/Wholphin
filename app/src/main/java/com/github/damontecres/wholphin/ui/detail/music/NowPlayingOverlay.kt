package com.github.damontecres.wholphin.ui.detail.music

import androidx.annotation.OptIn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.PlaybackAction
import com.github.damontecres.wholphin.ui.playback.PlaybackButtons
import com.github.damontecres.wholphin.ui.playback.SeekBar
import com.github.damontecres.wholphin.ui.roundSeconds
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(UnstableApi::class)
@Composable
fun NowPlayingOverlay(
    state: NowPlayingState,
    player: Player,
    current: AudioItem?,
    queue: List<AudioItem>,
    controllerViewState: ControllerViewState,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val playPauseState = rememberPlayPauseButtonState(player)
    val previousState = rememberPreviousButtonState(player)
    val nextState = rememberNextButtonState(player)
    Column(modifier = modifier.padding(16.dp)) {
        current?.title?.let {
            Text(it)
        }
        current?.albumTitle?.let {
            Text(it)
        }
        current?.artistNames?.let {
            Text(it)
        }
        SeekBar(
            player = player,
            controllerViewState = controllerViewState,
            onSeekProgress = {},
            interactionSource = remember { MutableInteractionSource() },
            isEnabled = false,
            intervals = 0,
            seekBack = Duration.ZERO,
            seekForward = Duration.ZERO,
            modifier =
                Modifier
                    .padding(vertical = 8.dp)
                    .fillMaxWidth(.95f),
        )
        Row(
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            PlaybackButtons(
                player = player,
                initialFocusRequester = focusRequester,
                onControllerInteraction = { controllerViewState.pulseControls() },
                onPlaybackActionClick = {
                    when (it) {
                        PlaybackAction.Next -> {
                            nextState.onClick()
                        }

                        PlaybackAction.Previous -> {
                            previousState.onClick()
                        }

                        is PlaybackAction.ToggleCaptions -> {
                            TODO()
                        }

                        else -> {}
                    }
                },
                showPlay = playPauseState.showPlay,
                previousEnabled = previousState.isEnabled,
                nextEnabled = nextState.isEnabled,
                seekBack = 10.seconds,
                skipBackOnResume = null,
                seekForward = 30.seconds,
            )
        }
        if (queue.isEmpty()) {
            Text("No items")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(queue) { index, song ->
                    SongListItem(
                        title = song.title,
                        artist = song.artistNames,
                        indexNumber = index + 1,
                        runtime = song.runtime?.roundSeconds,
                        showArtist = true,
                        isPlaying = current?.id == song.id,
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
