@file:kotlin.OptIn(ExperimentalMaterial3Api::class)

package com.github.damontecres.wholphin.ui.playback

import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

sealed interface PlaybackAction {
    data object ShowDebug : PlaybackAction

    data object ShowPlaylist : PlaybackAction

    data object ShowVideoFilterDialog : PlaybackAction

    data object SearchCaptions : PlaybackAction

    data class ToggleCaptions(
        val index: Int,
    ) : PlaybackAction

    data class ToggleAudio(
        val index: Int,
    ) : PlaybackAction

    data class PlaybackSpeed(
        val value: Float,
    ) : PlaybackAction

    data class Scale(
        val scale: ContentScale,
    ) : PlaybackAction

    data object Previous : PlaybackAction

    data object Next : PlaybackAction
}

@OptIn(UnstableApi::class)
@Composable
fun PlaybackControls(
    subtitleStreams: List<SubtitleStream>,
    playerControls: Player,
    controllerViewState: ControllerViewState,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    showDebugInfo: Boolean,
    onSeekProgress: (Long) -> Unit,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    moreButtonOptions: MoreButtonOptions,
    subtitleIndex: Int?,
    audioIndex: Int?,
    audioStreams: List<AudioStream>,
    playbackSpeed: Float,
    scale: ContentScale,
    seekBarIntervals: Int,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    currentSegment: MediaSegmentDto?,
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester = remember { FocusRequester() },
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val scope = rememberCoroutineScope()

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val onControllerInteraction = {
        scope.launch(ExceptionHandler()) {
            bringIntoViewRequester.bringIntoView()
        }
        controllerViewState.pulseControls()
    }
    val onControllerInteractionForDialog = {
        scope.launch(ExceptionHandler()) {
            bringIntoViewRequester.bringIntoView()
        }
        controllerViewState.pulseControls(Long.MAX_VALUE)
    }
    LaunchedEffect(controllerViewState.controlsVisible) {
        if (controllerViewState.controlsVisible) {
            initialFocusRequester.tryRequestFocus()
        }
    }
    Column(
        modifier = modifier.bringIntoViewRequester(bringIntoViewRequester),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        SeekBar(
            player = playerControls,
            controllerViewState = controllerViewState,
            onSeekProgress = onSeekProgress,
            interactionSource = seekBarInteractionSource,
            isEnabled = seekEnabled,
            intervals = seekBarIntervals,
            seekBack = seekBack,
            seekForward = seekForward,
            modifier =
                Modifier
                    .padding(vertical = 0.dp)
                    .fillMaxWidth(.95f),
        )
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth(),
        ) {
            LeftPlaybackButtons(
                onControllerInteraction = onControllerInteraction,
                onPlaybackActionClick = onPlaybackActionClick,
                showDebugInfo = showDebugInfo,
                moreButtonOptions = moreButtonOptions,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            PlaybackButtons(
                player = playerControls,
                initialFocusRequester = initialFocusRequester,
                onControllerInteraction = onControllerInteraction,
                onPlaybackActionClick = onPlaybackActionClick,
                showPlay = showPlay,
                previousEnabled = previousEnabled,
                nextEnabled = nextEnabled,
                seekBack = seekBack,
                seekForward = seekForward,
                skipBackOnResume = skipBackOnResume,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                currentSegment?.let { segment ->
                    Button(
                        onClick = {
                            playerControls.seekTo(segment.endTicks.ticks.inWholeMilliseconds)
                        },
                        modifier =
                            Modifier
                                .align(Alignment.CenterVertically)
                                .padding(end = 32.dp),
                    ) {
                        Text(
                            text = "Skip ${segment.type.serialName}",
                        )
                    }
                }
                RightPlaybackButtons(
                    subtitleStreams = subtitleStreams,
                    onControllerInteraction = onControllerInteraction,
                    onControllerInteractionForDialog = onControllerInteractionForDialog,
                    onPlaybackActionClick = onPlaybackActionClick,
                    subtitleIndex = subtitleIndex,
                    audioStreams = audioStreams,
                    audioIndex = audioIndex,
                    playbackSpeed = playbackSpeed,
                    scale = scale,
                    modifier = Modifier,
                )
            }
        }
    }
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SeekBar(
    player: Player,
    isEnabled: Boolean,
    intervals: Int,
    controllerViewState: ControllerViewState,
    onSeekProgress: (Long) -> Unit,
    seekBack: Duration,
    seekForward: Duration,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var bufferedProgress by remember(player) { mutableFloatStateOf(player.bufferedPosition.toFloat() / player.duration) }
    var position by remember(player) { mutableLongStateOf(player.currentPosition) }
    var progress by remember(player) { mutableFloatStateOf(player.currentPosition.toFloat() / player.duration) }
    LaunchedEffect(player) {
        while (isActive) {
            bufferedProgress = player.bufferedPosition.toFloat() / player.duration
            position = player.currentPosition
            progress = player.currentPosition.toFloat() / player.duration
            delay(250L)
        }
    }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IntervalSeekBarImpl(
            progress = progress,
            bufferedProgress = bufferedProgress,
            onSeek = {
                onSeekProgress(it)
            },
            controllerViewState = controllerViewState,
//            intervals = intervals,
            modifier = Modifier.fillMaxWidth(),
            interactionSource = interactionSource,
            enabled = isEnabled,
            durationMs = player.contentDuration,
            seekBack = seekBack,
            seekForward = seekForward,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = (position / 1000).seconds.toString(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .padding(8.dp),
            )
            Text(
                text = "-" + ((player.duration - position) / 1000).seconds.toString(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .padding(8.dp),
            )
        }
    }
}

private val buttonSpacing = 4.dp

@Composable
fun LeftPlaybackButtons(
    onControllerInteraction: () -> Unit,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    showDebugInfo: Boolean,
    moreButtonOptions: MoreButtonOptions,
    modifier: Modifier = Modifier,
) {
    var showMoreOptions by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
    ) {
        // More options
        PlaybackButton(
            iconRes = R.drawable.baseline_more_vert_96,
            onClick = {
                onControllerInteraction.invoke()
                showMoreOptions = true
            },
            enabled = true,
            onControllerInteraction = onControllerInteraction,
            modifier = Modifier.focusRequester(focusRequester),
        )
    }
    if (showMoreOptions) {
        val options =
            buildList {
                addAll(moreButtonOptions.options.keys)
                add(if (showDebugInfo) "Hide debug info" else "Show debug info")
            }
        BottomDialog(
            choices = options,
            onDismissRequest = {
                showMoreOptions = false
                focusRequester.tryRequestFocus()
            },
            onSelectChoice = { index, choice ->
                val action = moreButtonOptions.options[choice] ?: PlaybackAction.ShowDebug
                onPlaybackActionClick.invoke(action)
            },
            gravity = Gravity.START,
        )
    }
}

private val speedOptions = listOf(".25", ".5", ".75", "1.0", "1.25", "1.5", "2.0")

@Composable
fun RightPlaybackButtons(
    subtitleStreams: List<SubtitleStream>,
    onControllerInteraction: () -> Unit,
    onControllerInteractionForDialog: () -> Unit,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    subtitleIndex: Int?,
    audioStreams: List<AudioStream>,
    audioIndex: Int?,
    playbackSpeed: Float,
    scale: ContentScale,
    modifier: Modifier = Modifier,
) {
    var showCaptionDialog by remember { mutableStateOf(false) }
    var showOptionsDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showScaleDialog by remember { mutableStateOf(false) }

    val captionFocusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
    ) {
        // Captions
        PlaybackButton(
            enabled = true,
            iconRes = R.drawable.captions_svgrepo_com,
            onClick = {
                onControllerInteractionForDialog.invoke()
                showCaptionDialog = true
            },
            onControllerInteraction = onControllerInteraction,
            modifier = Modifier.focusRequester(captionFocusRequester),
        )
        // Playback speed, etc
        PlaybackButton(
            iconRes = R.drawable.vector_settings,
            onClick = {
                onControllerInteractionForDialog.invoke()
                showOptionsDialog = true
            },
            enabled = true,
            onControllerInteraction = onControllerInteraction,
            modifier = Modifier.focusRequester(settingsFocusRequester),
        )
    }
    val scope = rememberCoroutineScope()
    if (showCaptionDialog) {
        val options = subtitleStreams.map { it.displayName }
        Timber.v("subtitleIndex=$subtitleIndex, options=$options")
        val currentChoice =
            subtitleStreams.indexOfFirstOrNull { it.index == subtitleIndex } ?: subtitleStreams.size
        BottomDialog(
            choices = options + listOf("None", "Search & Download"),
            currentChoice = currentChoice,
            onDismissRequest = {
                onControllerInteraction.invoke()
                showCaptionDialog = false
                scope.launch {
                    // TODO this is hacky, but playback changes force refocus and this is a workaround
                    delay(250L)
                    captionFocusRequester.tryRequestFocus()
                }
            },
            onSelectChoice = { index, _ ->
                if (index in subtitleStreams.indices) {
                    onPlaybackActionClick.invoke(PlaybackAction.ToggleCaptions(subtitleStreams[index].index))
                } else {
                    val idx = index - subtitleStreams.size
                    if (idx == 0) {
                        onPlaybackActionClick.invoke(PlaybackAction.ToggleCaptions(TrackIndex.DISABLED))
                    } else {
                        onPlaybackActionClick.invoke(PlaybackAction.SearchCaptions)
                    }
                }
            },
            gravity = Gravity.END,
        )
    }
    if (showOptionsDialog) {
        val options = listOf("Audio Track", "Playback Speed", "Video Scale")
        BottomDialog(
            choices = options,
            currentChoice = null,
            onDismissRequest = {
                onControllerInteraction.invoke()
                showOptionsDialog = false
            },
            onSelectChoice = { index, _ ->
                when (index) {
                    0 -> showAudioDialog = true
                    1 -> showSpeedDialog = true
                    2 -> showScaleDialog = true
                }
            },
            gravity = Gravity.END,
        )
    }
    if (showAudioDialog) {
        BottomDialog(
            choices = audioStreams.map { it.displayName },
            currentChoice = audioStreams.indexOfFirstOrNull { it.index == audioIndex },
            onDismissRequest = {
                onControllerInteraction.invoke()
                showAudioDialog = false
                scope.launch {
                    delay(250L)
                    settingsFocusRequester.tryRequestFocus()
                }
            },
            onSelectChoice = { index, _ ->
                onPlaybackActionClick.invoke(PlaybackAction.ToggleAudio(audioStreams[index].index))
            },
            gravity = Gravity.END,
        )
    }
    if (showSpeedDialog) {
        BottomDialog(
            choices = speedOptions,
            currentChoice = speedOptions.indexOf(playbackSpeed.toString()),
            onDismissRequest = {
                onControllerInteraction.invoke()
                showSpeedDialog = false
                scope.launch {
                    delay(250L)
                    settingsFocusRequester.tryRequestFocus()
                }
            },
            onSelectChoice = { _, value ->
                onPlaybackActionClick.invoke(PlaybackAction.PlaybackSpeed(value.toFloat()))
            },
            gravity = Gravity.END,
        )
    }
    if (showScaleDialog) {
        BottomDialog(
            choices = playbackScaleOptions.values.toList(),
            currentChoice = playbackScaleOptions.keys.toList().indexOf(scale),
            onDismissRequest = {
                onControllerInteraction.invoke()
                showScaleDialog = false
                scope.launch {
                    delay(250L)
                    settingsFocusRequester.tryRequestFocus()
                }
            },
            onSelectChoice = { index, _ ->
                onPlaybackActionClick.invoke(PlaybackAction.Scale(playbackScaleOptions.keys.toList()[index]))
            },
            gravity = Gravity.END,
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlaybackButtons(
    player: Player,
    initialFocusRequester: FocusRequester,
    onControllerInteraction: () -> Unit,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
    ) {
        PlaybackButton(
            iconRes = R.drawable.baseline_skip_previous_24,
            onClick = {
                onControllerInteraction.invoke()
                onPlaybackActionClick.invoke(PlaybackAction.Previous)
            },
            enabled = previousEnabled,
            onControllerInteraction = onControllerInteraction,
        )
        PlaybackButton(
            iconRes = R.drawable.baseline_fast_rewind_24,
            onClick = {
                onControllerInteraction.invoke()
                player.seekBack(seekBack)
            },
            onControllerInteraction = onControllerInteraction,
        )
        PlaybackButton(
            modifier = Modifier.focusRequester(initialFocusRequester),
            iconRes = if (showPlay) R.drawable.baseline_play_arrow_24 else R.drawable.baseline_pause_24,
            onClick = {
                onControllerInteraction.invoke()
                if (showPlay) {
                    player.play()
                    skipBackOnResume?.let {
                        player.seekBack(it)
                    }
                } else {
                    player.pause()
                }
            },
            onControllerInteraction = onControllerInteraction,
        )
        PlaybackButton(
            iconRes = R.drawable.baseline_fast_forward_24,
            onClick = {
                onControllerInteraction.invoke()
                player.seekForward(seekForward)
            },
            onControllerInteraction = onControllerInteraction,
        )
        PlaybackButton(
            iconRes = R.drawable.baseline_skip_next_24,
            onClick = {
                onControllerInteraction.invoke()
                onPlaybackActionClick.invoke(PlaybackAction.Next)
            },
            enabled = nextEnabled,
            onControllerInteraction = onControllerInteraction,
        )
    }
}

@Composable
fun PlaybackButton(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val selectedColor = MaterialTheme.colorScheme.border
    Button(
        enabled = enabled,
        onClick = onClick,
        shape = ButtonDefaults.shape(CircleShape),
        colors =
            ButtonDefaults.colors(
                containerColor = AppColors.TransparentBlack25,
                focusedContainerColor = selectedColor,
            ),
        contentPadding = PaddingValues(4.dp),
        modifier =
            modifier
                .padding(4.dp)
                .size(44.dp, 44.dp)
                .onFocusChanged { onControllerInteraction.invoke() },
    ) {
        Icon(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(iconRes),
            contentDescription = "",
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun BottomDialog(
    choices: List<String>,
    onDismissRequest: () -> Unit,
    onSelectChoice: (Int, String) -> Unit,
    gravity: Int,
    currentChoice: Int? = null,
) {
    // TODO enforcing a width ends up ignore the gravity
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = true),
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.setGravity(Gravity.BOTTOM or gravity) // Move down, by default dialogs are in the centre
            window.setDimAmount(0f) // Remove dimmed background of ongoing playback
        }

        Box(
            modifier =
                Modifier
                    .wrapContentSize()
                    .padding(8.dp)
                    .background(Color.DarkGray, shape = RoundedCornerShape(16.dp)),
        ) {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
//                        .widthIn(max = 240.dp)
                        .wrapContentWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                itemsIndexed(choices) { index, choice ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val focused = interactionSource.collectIsFocusedAsState().value
                    val color =
                        if (focused) {
                            MaterialTheme.colorScheme.inverseOnSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    ListItem(
                        selected = index == currentChoice,
                        onClick = {
                            onDismissRequest()
                            onSelectChoice(index, choice)
                        },
                        leadingContent = {
                            if (index == currentChoice) {
                                Box(
                                    modifier =
                                        Modifier
                                            .padding(horizontal = 4.dp)
                                            .clip(CircleShape)
                                            .align(Alignment.Center)
                                            .background(color)
                                            .size(8.dp),
                                )
                            }
                        },
                        headlineContent = {
                            Text(
                                text = choice,
                                color = color,
                            )
                        },
                        interactionSource = interactionSource,
                    )
                }
            }
        }
    }
}

data class MoreButtonOptions(
    val options: Map<String, PlaybackAction>,
)
