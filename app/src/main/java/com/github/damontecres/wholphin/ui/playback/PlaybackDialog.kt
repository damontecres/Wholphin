package com.github.damontecres.wholphin.ui.playback

import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import timber.log.Timber
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
    val audioStreams: List<AudioStream>,
    val subtitleIndex: Int?,
    val subtitleStreams: List<SubtitleStream>,
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
            val subtitleStreams = settings.subtitleStreams
            val options = remember(subtitleStreams) { subtitleStreams.map { it.displayName } }
            Timber.v("subtitleIndex=${settings.subtitleIndex}, options=$options")
            val currentChoice =
                remember(settings.subtitleStreams, settings.subtitleIndex) {
                    subtitleStreams.indexOfFirstOrNull { it.index == settings.subtitleIndex }
                        ?: (
                            subtitleStreams.size +
                                when (settings.subtitleIndex) {
                                    TrackIndex.ONLY_FORCED -> 0
                                    TrackIndex.DISABLED -> 1
                                    else -> 0 //
                                }
                        )
                }

            BottomDialog(
                choices =
                    options +
                        listOf(
                            stringResource(R.string.only_forced_subtitles),
                            stringResource(R.string.none),
                            stringResource(R.string.search_and_download),
                        ),
                currentChoice = currentChoice,
                onDismissRequest = {
                    onControllerInteraction.invoke()
                    onDismissRequest.invoke()
//                    scope.launch {
//                        // TODO this is hacky, but playback changes force refocus and this is a workaround
//                        delay(250L)
//                        captionFocusRequester.tryRequestFocus()
//                    }
                },
                onSelectChoice = { index, _ ->
                    if (index in subtitleStreams.indices) {
                        onPlaybackActionClick.invoke(PlaybackAction.ToggleCaptions(subtitleStreams[index].index))
                    } else {
                        when (index - subtitleStreams.size) {
                            0 -> onPlaybackActionClick.invoke(PlaybackAction.ToggleCaptions(TrackIndex.ONLY_FORCED))
                            1 -> onPlaybackActionClick.invoke(PlaybackAction.ToggleCaptions(TrackIndex.DISABLED))
                            else -> onPlaybackActionClick.invoke(PlaybackAction.SearchCaptions)
                        }
                    }
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
            BottomDialog(
                choices = settings.audioStreams.map { it.displayName },
                currentChoice = settings.audioStreams.indexOfFirstOrNull { it.index == settings.audioIndex },
                onDismissRequest = {
                    onControllerInteraction.invoke()
                    onDismissRequest.invoke()
//                    scope.launch {
//                        delay(250L)
//                        settingsFocusRequester.tryRequestFocus()
//                    }
                },
                onSelectChoice = { index, _ ->
                    onPlaybackActionClick.invoke(PlaybackAction.ToggleAudio(settings.audioStreams[index].index))
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
