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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.SelectedLeadingContent
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.ui.skipStringRes
import com.github.damontecres.wholphin.ui.theme.LocalTheme
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.extensions.ticks
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
    playerControls: Player,
    controllerViewState: ControllerViewState,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    moreFocusRequester: FocusRequester,
    captionFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    onSeekProgress: (Long) -> Unit,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBarIntervals: Int,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    currentSegment: MediaSegmentDto?,
    modifier: Modifier = Modifier,
    initialFocusRequester: FocusRequester = remember { FocusRequester() },
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    seekBarFocusRequester: FocusRequester = remember { FocusRequester() },
    shouldFocusSeekBar: Boolean = false,
    onSeekBarFocusConsumed: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var initialButtonFocused by remember { mutableStateOf(false) }

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val onControllerInteraction = {
        scope.launch(ExceptionHandler()) {
            bringIntoViewRequester.bringIntoView()
        }
        controllerViewState.pulseControls()
    }
    val seekBarFocused by seekBarInteractionSource.collectIsFocusedAsState()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(controllerViewState.controlsVisible, shouldFocusSeekBar) {
        if (controllerViewState.controlsVisible && shouldFocusSeekBar) {
            // Force clear any existing focus first to prevent buttons from holding onto it
            focusManager.clearFocus(force = true)

            // Aggressively request seekbar focus
            repeat(100) {
                if (seekBarFocused) {
                    onSeekBarFocusConsumed.invoke()
                    return@LaunchedEffect
                }
                seekBarFocusRequester.requestFocus()
                delay(16L)
            }
            // If focus could not be acquired, give up and re-enable button focus
            onSeekBarFocusConsumed.invoke()
        }
        // Don't explicitly request initial focus - let Compose handle default focus
        // This prevents stealing focus from seekbar after it was just successfully focused
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
            focusRequester = seekBarFocusRequester,
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
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (shouldFocusSeekBar && !seekBarFocused && (isSkipBack(event) || isSkipForward(event))) {
                            seekBarFocusRequester.tryRequestFocus()
                            return@onPreviewKeyEvent true
                        }
                        false
                    },
        ) {
            LeftPlaybackButtons(
                moreFocusRequester = moreFocusRequester,
                seekBarFocusRequester = seekBarFocusRequester,
                onControllerInteraction = onControllerInteraction,
                onClickPlaybackDialogType = onClickPlaybackDialogType,
                buttonsEnabled = !shouldFocusSeekBar,
                modifier = Modifier.align(Alignment.CenterStart),
            )
            PlaybackButtons(
                player = playerControls,
                initialFocusRequester = initialFocusRequester,
                seekBarFocusRequester = seekBarFocusRequester,
                onControllerInteraction = onControllerInteraction,
                onPlaybackActionClick = onPlaybackActionClick,
                showPlay = showPlay,
                previousEnabled = previousEnabled,
                nextEnabled = nextEnabled,
                seekBack = seekBack,
                seekForward = seekForward,
                skipBackOnResume = skipBackOnResume,
                onInitialFocusChanged = { focused ->
                    initialButtonFocused = focused
                },
                buttonsEnabled = !shouldFocusSeekBar,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                currentSegment?.let { segment ->
                    TextButton(
                        stringRes = segment.type.skipStringRes,
                        onClick = {
                            playerControls.seekTo(segment.endTicks.ticks.inWholeMilliseconds)
                        },
                        modifier =
                            Modifier
                                .align(Alignment.CenterVertically)
                                .padding(end = 32.dp)
                                .focusProperties { up = seekBarFocusRequester },
                    )
                }
                RightPlaybackButtons(
                    captionFocusRequester = captionFocusRequester,
                    settingsFocusRequester = settingsFocusRequester,
                    seekBarFocusRequester = seekBarFocusRequester,
                    onControllerInteraction = onControllerInteraction,
                    onClickPlaybackDialogType = onClickPlaybackDialogType,
                    buttonsEnabled = !shouldFocusSeekBar,
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
    focusRequester: FocusRequester? = null,
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
            focusRequester = focusRequester,
            interactionSource = interactionSource,
            enabled = isEnabled,
            durationMs = player.duration,
            seekBack = seekBack,
            seekForward = seekForward,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val remaining = ((player.duration - position) / 1000).seconds
            Text(
                text = (position / 1000).seconds.toString(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .padding(8.dp),
            )
            Text(
                text = "-$remaining",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelLarge,
                modifier =
                    Modifier
                        .padding(8.dp),
            )
        }
    }
}

private val buttonSpacing = 12.dp

@Composable
fun LeftPlaybackButtons(
    moreFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    onControllerInteraction: () -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    buttonsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
    ) {
        // More options
        PlaybackButton(
            iconRes = R.drawable.baseline_more_vert_96,
            onClick = {
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(PlaybackDialogType.MORE)
            },
            enabled = true,
            focusEnabled = buttonsEnabled,
            onControllerInteraction = onControllerInteraction,
            upFocusRequester = seekBarFocusRequester,
            modifier = Modifier.focusRequester(moreFocusRequester),
        )
    }
}

@Composable
fun RightPlaybackButtons(
    captionFocusRequester: FocusRequester,
    settingsFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    onControllerInteraction: () -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    buttonsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
    ) {
        // Captions
        PlaybackButton(
            enabled = true,
            focusEnabled = buttonsEnabled,
            iconRes = R.drawable.captions_svgrepo_com,
            onClick = {
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(PlaybackDialogType.CAPTIONS)
            },
            onControllerInteraction = onControllerInteraction,
            upFocusRequester = seekBarFocusRequester,
            modifier = Modifier.focusRequester(captionFocusRequester),
        )
        // Playback speed, etc
        PlaybackButton(
            iconRes = R.drawable.vector_settings,
            onClick = {
                onControllerInteraction.invoke()
                onClickPlaybackDialogType.invoke(PlaybackDialogType.SETTINGS)
            },
            enabled = true,
            focusEnabled = buttonsEnabled,
            onControllerInteraction = onControllerInteraction,
            upFocusRequester = seekBarFocusRequester,
            modifier = Modifier.focusRequester(settingsFocusRequester),
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlaybackButtons(
    player: Player,
    initialFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    onControllerInteraction: () -> Unit,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    onInitialFocusChanged: (Boolean) -> Unit,
    buttonsEnabled: Boolean = true,
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
            focusEnabled = buttonsEnabled,
            onControllerInteraction = onControllerInteraction,
            upFocusRequester = seekBarFocusRequester,
        )
        PlaybackButton(
            iconRes = R.drawable.baseline_fast_rewind_24,
            onClick = {
                onControllerInteraction.invoke()
                player.seekBack(seekBack)
            },
            focusEnabled = buttonsEnabled,
            onControllerInteraction = onControllerInteraction,
            upFocusRequester = seekBarFocusRequester,
        )
        PlaybackButton(
            modifier =
                Modifier
                    .focusRequester(initialFocusRequester)
                    .onFocusChanged { onInitialFocusChanged(it.isFocused) },
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
            focusEnabled = buttonsEnabled,
            onControllerInteraction = onControllerInteraction,
            upFocusRequester = seekBarFocusRequester,
        )
        PlaybackButton(
            iconRes = R.drawable.baseline_fast_forward_24,
            onClick = {
                onControllerInteraction.invoke()
                player.seekForward(seekForward)
            },
            focusEnabled = buttonsEnabled,
            onControllerInteraction = onControllerInteraction,
            upFocusRequester = seekBarFocusRequester,
        )
        PlaybackButton(
            iconRes = R.drawable.baseline_skip_next_24,
            onClick = {
                onControllerInteraction.invoke()
                onPlaybackActionClick.invoke(PlaybackAction.Next)
            },
            enabled = nextEnabled,
            focusEnabled = buttonsEnabled,
            onControllerInteraction = onControllerInteraction,
            upFocusRequester = seekBarFocusRequester,
        )
    }
}

@Composable
fun PlaybackButton(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    upFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusEnabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
) {
    val selectedColor = MaterialTheme.colorScheme.border
    Button(
        enabled = enabled,
        onClick = onClick,
//        shape = ButtonDefaults.shape(CircleShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = AppColors.TransparentBlack25,
                focusedContainerColor = selectedColor,
            ),
        contentPadding = PaddingValues(4.dp),
        interactionSource = interactionSource,
        modifier =
            modifier
                .focusProperties {
                    upFocusRequester?.let { up = it }
                    canFocus = focusEnabled
                }
                .size(36.dp, 36.dp)
                .onFocusChanged { onControllerInteraction.invoke() },
    ) {
        Icon(
            modifier = Modifier.fillMaxSize(),
            painter = painterResource(iconRes),
            contentDescription = "",
            tint =
                if (LocalTheme.current == AppThemeColors.OLED_BLACK) {
                    LocalContentColor.current
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}

@Composable
fun <T> BottomDialog(
    choices: List<BottomDialogItem<T>>,
    onDismissRequest: () -> Unit,
    onSelectChoice: (Int, BottomDialogItem<T>) -> Unit,
    gravity: Int,
    currentChoice: BottomDialogItem<T>? = null,
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
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        shape = RoundedCornerShape(8.dp),
                    ),
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
                    ListItem(
                        selected = choice == currentChoice,
                        onClick = {
                            onDismissRequest()
                            onSelectChoice(index, choice)
                        },
                        leadingContent = {
                            SelectedLeadingContent(choice == currentChoice)
                        },
                        headlineContent = {
                            Text(
                                text = choice.headline,
                            )
                        },
                        supportingContent = {
                            choice.supporting?.let {
                                Text(
                                    text = it,
                                )
                            }
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

data class BottomDialogItem<T>(
    val data: T,
    val headline: String,
    val supporting: String?,
)

@PreviewTvSpec
@Composable
private fun ButtonPreview() {
    WholphinTheme {
        Row {
            PlaybackButton(
                iconRes = R.drawable.baseline_play_arrow_24,
                onClick = {},
                onControllerInteraction = {},
            )
        }
    }
}
