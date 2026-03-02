package com.github.damontecres.wholphin.ui.detail.music

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.main.settings.MoveDirection
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.PlaybackAction
import com.github.damontecres.wholphin.ui.playback.PlaybackButtons
import com.github.damontecres.wholphin.ui.playback.PlaybackFaButton
import com.github.damontecres.wholphin.ui.playback.SeekBar
import com.github.damontecres.wholphin.ui.preferences.MoveButton
import com.github.damontecres.wholphin.ui.roundSeconds
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.launch
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
    onMoveQueue: (Int, MoveDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    val playPauseState = rememberPlayPauseButtonState(player)
    val previousState = rememberPreviousButtonState(player)
    val nextState = rememberNextButtonState(player)
    val shuffleState = rememberShuffleButtonState(player)
    val repeatState = rememberRepeatButtonState(player)

    var queueHasFocus by remember { mutableStateOf(false) }
    val height by animateFloatAsState(
        if (queueHasFocus) {
            1f
        } else {
            .33f
        },
        animationSpec = tween(durationMillis = 500),
    )
    val listState = rememberLazyListState()
    val hideButtons by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0
        }
    }

    val firstFocusRequester = remember { FocusRequester() }
    BackHandler(hideButtons) {
        scope.launch {
            listState.animateScrollToItem(0)
            firstFocusRequester.tryRequestFocus()
        }
    }

    Column(
        modifier =
            modifier
                .padding(16.dp)
                .fillMaxHeight(height),
    ) {
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
                    .padding(top = 8.dp)
                    .fillMaxWidth(.95f),
        )
        AnimatedVisibility(
            visible = !hideButtons,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally),
        ) {
            Row(
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally),
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
                    seekForward = 30.seconds, // TODO
                )
                PlaybackFaButton(
                    iconRes = R.string.fa_shuffle,
                    onClick = {
                        shuffleState.onClick()
                    },
                    onControllerInteraction = { controllerViewState.pulseControls() },
                    textColor =
                        if (shuffleState.shuffleOn) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            Color.Unspecified
                        },
                )
                PlaybackFaButton(
                    iconRes = R.string.fa_repeat,
                    onClick = {
                        repeatState.onClick()
                    },
                    onControllerInteraction = { controllerViewState.pulseControls() },
                    textColor =
                        when (repeatState.repeatModeState) {
                            Player.REPEAT_MODE_ALL -> MaterialTheme.colorScheme.secondary

                            // TODO
                            Player.REPEAT_MODE_ONE -> MaterialTheme.colorScheme.tertiary

                            else -> Color.Unspecified
                        },
                )
            }
        }
        if (queue.isEmpty()) {
            Text("No items")
        } else {
            Text(
                text = stringResource(R.string.queue),
                style = MaterialTheme.typography.titleMedium,
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onFocusChanged {
                            queueHasFocus = it.hasFocus
                        },
            ) {
                itemsIndexed(queue) { index, song ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
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
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .onFocusChanged {
                                        controllerViewState.pulseControls()
                                    }.ifElse(
                                        index == 0,
                                        Modifier.focusRequester(firstFocusRequester),
                                    ),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.wrapContentWidth(),
                        ) {
                            MoveButton(
                                icon = R.string.fa_caret_up,
                                enabled = index > 0,
                                onClick = { onMoveQueue.invoke(index, MoveDirection.UP) },
                            )
                            MoveButton(
                                icon = R.string.fa_caret_down,
                                enabled = index < queue.lastIndex,
                                onClick = { onMoveQueue.invoke(index, MoveDirection.DOWN) },
                            )
                        }
                    }
                }
            }
        }
    }
}
