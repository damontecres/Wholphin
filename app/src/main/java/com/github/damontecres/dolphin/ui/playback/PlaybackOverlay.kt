package com.github.damontecres.dolphin.ui.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.tv.material3.Text
import org.jellyfin.sdk.model.api.TrickplayInfo
import kotlin.time.Duration

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
    seekBack: Duration,
    seekForward: Duration,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onSeekBarChange: (Long) -> Unit,
    showDebugInfo: Boolean,
    scale: ContentScale,
    playbackSpeed: Float,
    moreButtonOptions: MoreButtonOptions,
    subtitleIndex: Int?,
    audioIndex: Int?,
    audioStreams: List<AudioStream>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trickplayInfo: TrickplayInfo? = null,
    trickplayUrlFor: (Int) -> String? = { null },
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    // Will be used for preview/trick play images
    var seekProgressMs by remember { mutableLongStateOf(-1L) }
    var seekProgressPercent = (seekProgressMs.toDouble() / playerControls.duration).toFloat()
    val seekBarFocused by seekBarInteractionSource.collectIsFocusedAsState()

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
            if (seekBarInteractionSource.collectIsFocusedAsState().value) {
                LaunchedEffect(Unit) {
                    seekProgressPercent =
                        (playerControls.currentPosition.toFloat() / playerControls.duration)
                }
            }
            if (trickplayInfo != null) {
                AnimatedVisibility(seekProgressPercent >= 0 && seekBarFocused) {
                    val tilesPerImage = trickplayInfo.tileWidth * trickplayInfo.tileHeight
                    val index =
                        (seekProgressMs / trickplayInfo.interval).toInt() / tilesPerImage
                    val imageUrl = trickplayUrlFor(index)

                    if (imageUrl != null) {
                        SeekPreviewImage(
                            modifier =
                                Modifier
//                                    .align(Alignment.TopStart)
                                    .offsetByPercent(
                                        xPercentage = seekProgressPercent.coerceIn(0f, 1f),
//                                        yOffset = heightPx,
//                                yPercentage = 1 - controlHeight,
                                    ),
                            previewImageUrl = imageUrl,
                            duration = playerControls.duration,
                            seekProgressMs = seekProgressMs,
                            videoWidth = trickplayInfo.width,
                            videoHeight = trickplayInfo.height,
                            trickPlayInfo = trickplayInfo,
                        )
                    }
                }
            }

            PlaybackControls(
                modifier = Modifier.fillMaxWidth(),
                subtitleStreams = subtitleStreams,
                playerControls = playerControls,
                onPlaybackActionClick = onPlaybackActionClick,
                controllerViewState = controllerViewState,
                showDebugInfo = showDebugInfo,
                onSeekProgress = {
                    seekProgressMs = it
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
                seekBack = seekBack,
                seekForward = seekForward,
            )
        }
    }
}
