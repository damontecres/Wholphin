package com.github.damontecres.wholphin.ui.detail.music

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.state.rememberNextButtonState
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPreviousButtonState
import androidx.media3.ui.compose.state.rememberRepeatButtonState
import androidx.media3.ui.compose.state.rememberShuffleButtonState
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackAction
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackButton
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackButtons
import com.github.damontecres.wholphin.ui.playback.overlay.buttonSpacing
import com.github.damontecres.wholphin.ui.theme.PreviewInteractionSource
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import kotlin.time.Duration.Companion.seconds

@OptIn(UnstableApi::class)
@Composable
fun NowPlayingButtons(
    player: Player,
    controllerViewState: ControllerViewState,
    initialFocusRequester: FocusRequester,
    onClickMore: () -> Unit,
    onClickStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playPauseState = rememberPlayPauseButtonState(player)
    val previousState = rememberPreviousButtonState(player)
    val nextState = rememberNextButtonState(player)
    val shuffleState = rememberShuffleButtonState(player)
    val repeatState = rememberRepeatButtonState(player)

    val onControllerInteraction = remember { { controllerViewState.pulseControls() } }
    Box(
        modifier = modifier.focusGroup(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            PlaybackButton(
                iconRes = R.drawable.baseline_more_vert_96,
                onClick = {
                    onControllerInteraction.invoke()
                    onClickMore.invoke()
                },
                enabled = true,
                onControllerInteraction = onControllerInteraction,
                modifier = Modifier,
            )
            PlaybackButton(
                iconRes = R.drawable.baseline_stop_24,
                onClick = {
                    onClickStop.invoke()
                },
                enabled = true,
                onControllerInteraction = onControllerInteraction,
                modifier = Modifier,
            )
        }
        PlaybackButtons(
            player = player,
            initialFocusRequester = initialFocusRequester,
            onControllerInteraction = onControllerInteraction,
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
            modifier = Modifier.align(Alignment.Center),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            ShuffleButton(
                active = shuffleState.shuffleOn,
                enabled = shuffleState.isEnabled,
                onClick = {
                    shuffleState.onClick()
                },
                onControllerInteraction = onControllerInteraction,
            )
            RepeatButton(
                repeatMode = repeatState.repeatModeState,
                enabled = repeatState.isEnabled,
                onClick = {
                    repeatState.onClick()
                },
                onControllerInteraction = onControllerInteraction,
            )
        }
    }
}

@Composable
fun ShuffleButton(
    active: Boolean,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val selectedColor = MaterialTheme.colorScheme.border
    val focused by interactionSource.collectIsFocusedAsState()
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
                .size(36.dp, 36.dp)
                .onFocusChanged { onControllerInteraction.invoke() },
    ) {
        Text(
            text = stringResource(R.string.fa_shuffle),
            fontSize = 18.sp,
            fontFamily = FontAwesome,
            textAlign = TextAlign.Center,
            color =
                when {
                    focused && active -> MaterialTheme.colorScheme.onSurface
                    focused && !active -> MaterialTheme.colorScheme.surface
                    !focused && active -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
                },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically),
        )
    }
}

@Composable
fun RepeatButton(
    repeatMode: Int,
    onClick: () -> Unit,
    onControllerInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val selectedColor = MaterialTheme.colorScheme.border
    val focused by interactionSource.collectIsFocusedAsState()
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
                .size(36.dp, 36.dp)
                .onFocusChanged { onControllerInteraction.invoke() },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .align(Alignment.CenterVertically),
        ) {
            Text(
                text = stringResource(R.string.fa_repeat),
                fontSize = 18.sp,
                fontFamily = FontAwesome,
                textAlign = TextAlign.Center,
                color =
                    if (focused) {
                        when (repeatMode) {
                            Player.REPEAT_MODE_ALL -> MaterialTheme.colorScheme.onSurface
                            Player.REPEAT_MODE_ONE -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.surface
                        }
                    } else {
                        when (repeatMode) {
                            Player.REPEAT_MODE_ALL -> MaterialTheme.colorScheme.onSurface
                            Player.REPEAT_MODE_ONE -> MaterialTheme.colorScheme.onSurface
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
                        }
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
            )
            if (repeatMode == Player.REPEAT_MODE_ONE) {
                Text(
                    text = "1",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.surface,
                    modifier =
                        Modifier
                            .offset(x = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = RoundedCornerShape(1.dp),
                            ).align(Alignment.BottomStart),
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
fun ShuffleButtonPreview() {
    val source = remember { PreviewInteractionSource() }
    WholphinTheme {
        Column {
            Row {
                RepeatButton(
                    repeatMode = Player.REPEAT_MODE_OFF,
                    onClick = {},
                    onControllerInteraction = {},
                )
                RepeatButton(
                    repeatMode = Player.REPEAT_MODE_ALL,
                    onClick = {},
                    onControllerInteraction = {},
                )
                RepeatButton(
                    repeatMode = Player.REPEAT_MODE_ONE,
                    onClick = {},
                    onControllerInteraction = {},
                )
            }
            Row {
                RepeatButton(
                    repeatMode = Player.REPEAT_MODE_OFF,
                    onClick = {},
                    onControllerInteraction = {},
                    interactionSource = source,
                )
                RepeatButton(
                    repeatMode = Player.REPEAT_MODE_ALL,
                    onClick = {},
                    onControllerInteraction = {},
                    interactionSource = source,
                )
                RepeatButton(
                    repeatMode = Player.REPEAT_MODE_ONE,
                    onClick = {},
                    onControllerInteraction = {},
                    interactionSource = source,
                )
            }
        }
    }
}
