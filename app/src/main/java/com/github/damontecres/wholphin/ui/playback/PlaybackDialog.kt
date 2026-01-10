package com.github.damontecres.wholphin.ui.playback

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.components.SelectedLeadingContent
import kotlin.time.Duration

enum class PlaybackDialogType {
    MORE,
    CAPTIONS,
    SETTINGS,
    AUDIO,
    PLAYBACK_SPEED,
    VIDEO_SCALE,
    SUBTITLE_DELAY,
}

data class PlaybackSettings(
    val showDebugInfo: Boolean,
    val audioIndex: Int?,
    val audioStreams: List<SimpleMediaStream>,
    val subtitleIndex: Int?,
    val subtitleStreams: List<SimpleMediaStream>,
    val playbackSpeed: Float,
    val contentScale: ContentScale,
    val subtitleDelay: Duration,
)

@Composable
fun PlaybackDialog(
    enableSubtitleDelay: Boolean,
    enableVideoScale: Boolean,
    type: PlaybackDialogType,
    settings: PlaybackSettings,
    onDismissRequest: () -> Unit,
    onControllerInteraction: () -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onChangeSubtitleDelay: (Duration) -> Unit,
) {
    when (type) {
        PlaybackDialogType.MORE -> {
            val options =
                buildList {
                    add(stringResource(if (settings.showDebugInfo) R.string.hide_debug_info else R.string.show_debug_info))
                }
            BottomDialog(
                choices = options,
                onDismissRequest = {
                    onDismissRequest.invoke()
//                    focusRequester.tryRequestFocus()
                },
                onSelectChoice = { index, choice ->
                    onPlaybackActionClick.invoke(PlaybackAction.ShowDebug)
                },
                gravity = Gravity.START,
            )
        }

        PlaybackDialogType.CAPTIONS -> {
            SubtitleChoiceBottomDialog(
                choices = settings.subtitleStreams,
                currentChoice = settings.subtitleIndex,
                onDismissRequest = {
                    onControllerInteraction.invoke()
                    onDismissRequest.invoke()
                },
                onSelectChoice = { subtitleIndex ->
                    onDismissRequest.invoke()
                    if (subtitleIndex >= 0) {
                        onPlaybackActionClick.invoke(PlaybackAction.ToggleCaptions(subtitleIndex))
                    } else if (subtitleIndex == TrackIndex.DISABLED) {
                        onPlaybackActionClick.invoke(PlaybackAction.ToggleCaptions(TrackIndex.DISABLED))
                    }
                },
                onSelectSearch = {
                    onDismissRequest.invoke()
                    onPlaybackActionClick.invoke(PlaybackAction.SearchCaptions)
                },
                gravity = Gravity.END,
            )
        }

        PlaybackDialogType.SETTINGS -> {
            val options =
                buildList {
                    add(stringResource(R.string.audio))
                    add(stringResource(R.string.playback_speed))
                    if (enableVideoScale) {
                        add(stringResource(R.string.video_scale))
                    }
                    if (enableSubtitleDelay) {
                        add(stringResource(R.string.subtitle_delay))
                    }
                }
            BottomDialog(
                choices = options,
                currentChoice = null,
                onDismissRequest = onDismissRequest,
                onSelectChoice = { index, _ ->
                    when (index) {
                        0 -> onClickPlaybackDialogType(PlaybackDialogType.AUDIO)
                        1 -> onClickPlaybackDialogType(PlaybackDialogType.PLAYBACK_SPEED)
                        2 -> onClickPlaybackDialogType(PlaybackDialogType.VIDEO_SCALE)
                        3 -> onClickPlaybackDialogType(PlaybackDialogType.SUBTITLE_DELAY)
                    }
                },
                gravity = Gravity.END,
            )
        }

        PlaybackDialogType.AUDIO -> {
            StreamChoiceBottomDialog(
                choices = settings.audioStreams,
                currentChoice = settings.audioIndex,
                onDismissRequest = {
                    onControllerInteraction.invoke()
                    onDismissRequest.invoke()
//                    scope.launch {
//                        delay(250L)
//                        settingsFocusRequester.tryRequestFocus()
//                    }
                },
                onSelectChoice = { _, choice ->
                    onPlaybackActionClick.invoke(PlaybackAction.ToggleCaptions(choice.index))
                },
                gravity = Gravity.END,
            )
        }

        PlaybackDialogType.PLAYBACK_SPEED -> {
            BottomDialog(
                choices = playbackSpeedOptions,
                currentChoice = playbackSpeedOptions.indexOf(settings.playbackSpeed.toString()),
                onDismissRequest = {
                    onControllerInteraction.invoke()
                    onDismissRequest.invoke()
//                scope.launch {
//                    delay(250L)
//                    settingsFocusRequester.tryRequestFocus()
//                }
                },
                onSelectChoice = { _, value ->
                    onPlaybackActionClick.invoke(PlaybackAction.PlaybackSpeed(value.toFloat()))
                },
                gravity = Gravity.END,
            )
        }

        PlaybackDialogType.VIDEO_SCALE -> {
            BottomDialog(
                choices = playbackScaleOptions.values.toList(),
                currentChoice = playbackScaleOptions.keys.toList().indexOf(settings.contentScale),
                onDismissRequest = {
                    onControllerInteraction.invoke()
                    onDismissRequest.invoke()
//                scope.launch {
//                    delay(250L)
//                    settingsFocusRequester.tryRequestFocus()
//                }
                },
                onSelectChoice = { index, _ ->
                    onPlaybackActionClick.invoke(PlaybackAction.Scale(playbackScaleOptions.keys.toList()[index]))
                },
                gravity = Gravity.END,
            )
        }

        PlaybackDialogType.SUBTITLE_DELAY -> {
            Dialog(
                onDismissRequest = onDismissRequest,
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
                dialogWindowProvider?.window?.setDimAmount(0f)

                Box(
                    modifier =
                        Modifier
                            .wrapContentSize()
                            .background(
                                AppColors.TransparentBlack50,
                                shape = RoundedCornerShape(16.dp),
                            ),
                ) {
                    SubtitleDelay(
                        delay = settings.subtitleDelay,
                        onChangeDelay = onChangeSubtitleDelay,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SubtitleChoiceBottomDialog(
    choices: List<SimpleMediaStream>,
    onDismissRequest: () -> Unit,
    onSelectChoice: (Int) -> Unit,
    onSelectSearch: () -> Unit,
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
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
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
                item {
                    ListItem(
                        selected = currentChoice == TrackIndex.DISABLED,
                        onClick = {
                            onSelectChoice(TrackIndex.DISABLED)
                        },
                        leadingContent = {
                            SelectedLeadingContent(currentChoice == TrackIndex.DISABLED)
                        },
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.none),
                            )
                        },
                        supportingContent = {},
                    )
                }
                itemsIndexed(choices) { index, choice ->
                    val interactionSource = remember { MutableInteractionSource() }
                    ListItem(
                        selected = choice.index == currentChoice,
                        onClick = {
                            onSelectChoice(choice.index)
                        },
                        leadingContent = {
                            SelectedLeadingContent(choice.index == currentChoice)
                        },
                        headlineContent = {
                            Text(
                                text = choice.streamTitle ?: choice.displayTitle,
                            )
                        },
                        supportingContent = {
                            if (choice.streamTitle != null) Text(choice.displayTitle)
                        },
                        interactionSource = interactionSource,
                    )
                }
                item {
                    HorizontalDivider()
                    ListItem(
                        selected = false,
                        onClick = onSelectSearch,
                        leadingContent = {},
                        headlineContent = {
                            Text(
                                text = stringResource(R.string.search_and_download),
                            )
                        },
                        supportingContent = {},
                    )
                }
            }
        }
    }
}

@Composable
fun StreamChoiceBottomDialog(
    choices: List<SimpleMediaStream>,
    onDismissRequest: () -> Unit,
    onSelectChoice: (Int, SimpleMediaStream) -> Unit,
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
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
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
                        selected = choice.index == currentChoice,
                        onClick = {
                            onDismissRequest()
                            onSelectChoice(index, choice)
                        },
                        leadingContent = {
                            SelectedLeadingContent(choice.index == currentChoice)
                        },
                        headlineContent = {
                            Text(
                                text = choice.streamTitle ?: choice.displayTitle,
                            )
                        },
                        supportingContent = {
                            if (choice.streamTitle != null) Text(choice.displayTitle)
                        },
                        interactionSource = interactionSource,
                    )
                }
            }
        }
    }
}
