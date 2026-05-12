package com.github.damontecres.wholphin.ui.detail.music

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.main.settings.MoveDirection
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.overlay.SeekBar
import com.github.damontecres.wholphin.ui.preferences.MoveButton
import com.github.damontecres.wholphin.ui.roundSeconds
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.launch
import kotlin.time.Duration

@OptIn(UnstableApi::class)
@Composable
fun NowPlayingOverlay(
    state: NowPlayingState,
    player: Player,
    current: AudioItem?,
    queue: List<AudioItem>,
    controllerViewState: ControllerViewState,
    onClickSong: (Int, AudioItem) -> Unit,
    onLongClickSong: (Int, AudioItem) -> Unit,
    onClickMore: () -> Unit,
    onMoveQueue: (Int, MoveDirection) -> Unit,
    onClickMoreItem: (Int, AudioItem) -> Unit,
    onClickStop: () -> Unit,
    lyricsFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

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
    var showButtons by remember { mutableStateOf(true) }

    val firstFocusRequester = remember { FocusRequester() }
    BackHandler(!showButtons) {
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
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .fillMaxWidth(.95f),
        )
        AnimatedVisibility(
            visible = showButtons,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally),
        ) {
            NowPlayingButtons(
                player = player,
                controllerViewState = controllerViewState,
                initialFocusRequester = focusRequester,
                onClickMore = onClickMore,
                onClickStop = onClickStop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterHorizontally),
            )
        }
        if (queue.isEmpty()) {
            Text("No items")
        } else {
            Text(
                text = stringResource(R.string.queue),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 16.dp, start = 16.dp, end = 16.dp),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .onFocusChanged {
                            queueHasFocus = it.hasFocus
                        }.focusProperties {
                            onExit = {
                                if (requestedFocusDirection == FocusDirection.Up) focusRequester.requestFocus()
                            }
                        },
            ) {
                itemsIndexed(queue, key = { _, song -> song.key }) { index, song ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = .5f),
                                    shape = RoundedCornerShape(8.dp),
                                ).padding(end = 8.dp)
                                .onFocusChanged {
                                    if (it.hasFocus) showButtons = index < 3
                                    controllerViewState.pulseControls()
                                }.animateItem(),
                    ) {
                        SongListItem(
                            title = song.title,
                            artist = song.artistNames,
                            indexNumber = index + 1,
                            runtime = song.runtime?.roundSeconds,
                            showArtist = true,
                            isPlaying = current?.id == song.id,
                            onClick = { onClickSong.invoke(index, song) },
                            onLongClick = { onLongClickSong.invoke(index, song) },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .ifElse(
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
                            Button(
                                onClick = { onClickMoreItem.invoke(index, song) },
                                enabled = true,
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
