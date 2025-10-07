package com.github.damontecres.dolphin.ui.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.Chapter
import com.github.damontecres.dolphin.ui.AppColors
import com.github.damontecres.dolphin.ui.cards.ChapterCard
import com.github.damontecres.dolphin.ui.ifElse
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.letNotEmpty
import com.github.damontecres.dolphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.TrickplayInfo
import kotlin.time.Duration

private val titleTextSize = 28.sp
private val subtitleTextSize = 18.sp

@Composable
fun PlaybackOverlay(
    title: String?,
    subtitleStreams: List<SubtitleStream>,
    chapters: List<Chapter>,
    playerControls: Player,
    controllerViewState: ControllerViewState,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onSeekBarChange: (Long) -> Unit,
    showDebugInfo: Boolean,
    scale: ContentScale,
    playbackSpeed: Float,
    moreButtonOptions: MoreButtonOptions,
    currentPlayback: CurrentPlayback?,
    audioStreams: List<AudioStream>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trickplayInfo: TrickplayInfo? = null,
    trickplayUrlFor: (Int) -> String? = { null },
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val seekBarFocused by seekBarInteractionSource.collectIsFocusedAsState()
    // Will be used for preview/trick play images
    var seekProgressMs by remember(seekBarFocused) { mutableLongStateOf(playerControls.currentPosition) }
    var seekProgressPercent = (seekProgressMs.toDouble() / playerControls.duration).toFloat()

    val chapterInteractionSources =
        remember(chapters.size) { List(chapters.size) { MutableInteractionSource() } }

    val density = LocalDensity.current

    val titleHeight =
        remember {
            if (title.isNotNullOrBlank()) with(density) { titleTextSize.toDp() } else 0.dp
        }
    val subtitleHeight =
        remember {
            if (subtitle.isNotNullOrBlank()) with(density) { subtitleTextSize.toDp() } else 0.dp
        }

    // This will be calculated after composition
    var controllerHeight by remember { mutableStateOf(0.dp) }
    var state by remember { mutableStateOf(ViewState.CONTROLLER) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            state == ViewState.CONTROLLER,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
        ) {
            Controller(
                title = title,
                subtitleStreams = subtitleStreams,
                chapters = chapters,
                playerControls = playerControls,
                controllerViewState = controllerViewState,
                showPlay = showPlay,
                previousEnabled = previousEnabled,
                nextEnabled = nextEnabled,
                seekEnabled = seekEnabled,
                seekBack = seekBack,
                skipBackOnResume = skipBackOnResume,
                seekForward = seekForward,
                onPlaybackActionClick = onPlaybackActionClick,
                onSeekProgress = {
                    onSeekBarChange(it)
                    seekProgressMs = it
                },
                showDebugInfo = showDebugInfo,
                scale = scale,
                playbackSpeed = playbackSpeed,
                moreButtonOptions = moreButtonOptions,
                currentPlayback = currentPlayback,
                audioStreams = audioStreams,
                subtitle = subtitle,
                seekBarInteractionSource = seekBarInteractionSource,
                modifier =
                    Modifier
                        .onKeyEvent { e ->
                            if (chapters.isNotEmpty() &&
                                e.type == KeyEventType.KeyDown && isDown(e) &&
                                !seekBarFocused
                            ) {
                                state = ViewState.CHAPTERS
                                true
                            }
                            false
                        }.onGloballyPositioned {
                            controllerHeight = with(density) { it.size.height.toDp() }
                        },
            )
        }
        AnimatedVisibility(
            state == ViewState.CHAPTERS,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            if (chapters.isNotEmpty()) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyUp && isUp(e)) {
                                    state = ViewState.CONTROLLER
                                    true
                                }
                                false
                            },
                ) {
                    Text(
                        text = "Chapters",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    LazyRow(
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRestorer(focusRequester)
                                .onFocusChanged {
                                    if (it.hasFocus) {
                                        controllerViewState.pulseControls()
                                    }
                                },
                    ) {
                        itemsIndexed(chapters) { index, chapter ->
                            val interactionSource = chapterInteractionSources[index]
                            val isFocused = interactionSource.collectIsFocusedAsState().value
                            LaunchedEffect(isFocused) {
                                if (isFocused) controllerViewState.pulseControls()
                            }
                            ChapterCard(
                                name = chapter.name,
                                position = chapter.position,
                                imageUrl = chapter.imageUrl,
                                onClick = {
                                    playerControls.seekTo(chapter.position.inWholeMilliseconds)
                                    controllerViewState.hideControls()
                                },
                                interactionSource = interactionSource,
                                modifier =
                                    Modifier.ifElse(
                                        index == 0,
                                        Modifier.focusRequester(focusRequester),
                                    ),
                            )
                        }
                    }
                }
            }
        }
        when (state) {
            ViewState.CONTROLLER -> {}

            ViewState.CHAPTERS -> {}
        }

        if (seekBarInteractionSource.collectIsFocusedAsState().value) {
            LaunchedEffect(Unit) {
                seekProgressPercent =
                    (playerControls.currentPosition.toFloat() / playerControls.duration)
            }
        }
        if (trickplayInfo != null) {
            AnimatedVisibility(
                seekProgressPercent >= 0 && seekBarFocused,
            ) {
                val tilesPerImage = trickplayInfo.tileWidth * trickplayInfo.tileHeight
                val index =
                    (seekProgressMs / trickplayInfo.interval).toInt() / tilesPerImage
                val imageUrl = trickplayUrlFor(index)

                if (imageUrl != null) {
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.Center)
                                .fillMaxWidth(.95f),
                    ) {
                        SeekPreviewImage(
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .offsetByPercent(
                                        xPercentage = seekProgressPercent.coerceIn(0f, 1f),
                                    ).padding(bottom = controllerHeight - titleHeight - subtitleHeight),
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
            AnimatedVisibility(
                showDebugInfo && controllerViewState.controlsVisible,
                modifier =
                    Modifier
                        .align(Alignment.TopStart),
            ) {
                currentPlayback?.tracks?.letNotEmpty {
                    PlaybackTrackInfo(
                        trackSupport = it,
                        modifier =
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(AppColors.TransparentBlack50),
                    )
                }
            }
        }
    }
}

enum class ViewState {
    CONTROLLER,
    CHAPTERS,
}

@Composable
fun Controller(
    title: String?,
    subtitleStreams: List<SubtitleStream>,
    chapters: List<Chapter>,
    playerControls: Player,
    controllerViewState: ControllerViewState,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onSeekProgress: (Long) -> Unit,
    showDebugInfo: Boolean,
    scale: ContentScale,
    playbackSpeed: Float,
    moreButtonOptions: MoreButtonOptions,
    currentPlayback: CurrentPlayback?,
    audioStreams: List<AudioStream>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp),
        ) {
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleLarge,
                    fontSize = titleTextSize,
                )
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    fontSize = subtitleTextSize,
                )
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
                onSeekProgress(it)
            },
            showPlay = showPlay,
            previousEnabled = previousEnabled,
            nextEnabled = nextEnabled,
            seekEnabled = seekEnabled,
            seekBarInteractionSource = seekBarInteractionSource,
            moreButtonOptions = moreButtonOptions,
            subtitleIndex = currentPlayback?.subtitleIndex,
            audioIndex = currentPlayback?.audioIndex,
            audioStreams = audioStreams,
            playbackSpeed = playbackSpeed,
            scale = scale,
            seekBarIntervals = 16,
            seekBack = seekBack,
            seekForward = seekForward,
            skipBackOnResume = skipBackOnResume,
        )
        if (chapters.isNotEmpty()) {
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}
