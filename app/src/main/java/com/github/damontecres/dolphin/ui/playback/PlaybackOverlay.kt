package com.github.damontecres.dolphin.ui.playback

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.tv.material3.Text

@Composable
fun PlaybackOverlay(
    title: String?,
    subtitleStreams: List<SubtitleStream>,
    playerControls: Player,
    controllerViewState: ControllerViewState,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onSeekBarChange: (Float) -> Unit,
    showDebugInfo: Boolean,
    scale: ContentScale,
    playbackSpeed: Float,
    moreButtonOptions: MoreButtonOptions,
    subtitleIndex: Int?,
    audioIndex: Int?,
    audioStreams: List<AudioStream>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    // Will be used for preview/trick play images
    var seekProgress by remember { mutableFloatStateOf(-1f) }
    val seekBarFocused by seekBarInteractionSource.collectIsFocusedAsState()
    var seekBarDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column {
            title?.let {
                Text(
                    text = it,
                )
            }
            subtitle?.let {
                Text(
                    text = it,
                )
            }
            PlaybackControls(
                modifier = Modifier.fillMaxWidth(),
                subtitleStreams = subtitleStreams,
                playerControls = playerControls,
                onPlaybackActionClick = onPlaybackActionClick,
                controllerViewState = controllerViewState,
                showDebugInfo = showDebugInfo,
                onSeekProgress = {
                    seekProgress = it
                    onSeekBarChange(it)
                },
                showPlay = showPlay,
                previousEnabled = previousEnabled,
                nextEnabled = nextEnabled,
                seekEnabled = seekEnabled,
                seekBarInteractionSource = seekBarInteractionSource,
                moreButtonOptions = moreButtonOptions,
                subtitleIndex = subtitleIndex,
                audioIndex = audioIndex,
                audioStreams = audioStreams,
                playbackSpeed = playbackSpeed,
                scale = scale,
                seekBarIntervals = 16,
            )
        }
    }
}
