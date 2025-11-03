package com.github.damontecres.wholphin.ui.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.cards.ChapterCard
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.TrickplayInfo
import kotlin.time.Duration

private val titleTextSize = 28.sp
private val subtitleTextSize = 18.sp

/**
 * The overlay during playback showing controls, seek preview image, debug info, etc
 */
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
    currentItemPlayback: ItemPlayback,
    audioStreams: List<AudioStream>,
    currentSegment: MediaSegmentDto?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trickplayInfo: TrickplayInfo? = null,
    trickplayUrlFor: (Int) -> String? = { null },
    playlist: Playlist = Playlist(listOf(), 0),
    onClickPlaylist: (BaseItem) -> Unit = {},
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
    var state by remember { mutableStateOf(OverlayViewState.CONTROLLER) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            state == OverlayViewState.CONTROLLER,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
        ) {
            val nextState =
                if (chapters.isNotEmpty()) {
                    OverlayViewState.CHAPTERS
                } else if (playlist.hasNext()) {
                    OverlayViewState.QUEUE
                } else {
                    null
                }
            Controller(
                title = title,
                subtitleStreams = subtitleStreams,
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
                currentItemPlayback = currentItemPlayback,
                audioStreams = audioStreams,
                subtitle = subtitle,
                seekBarInteractionSource = seekBarInteractionSource,
                nextState = nextState,
                onNextStateFocus = {
                    nextState?.let { state = it }
                },
                currentSegment = currentSegment,
                modifier =
                    Modifier
                        // Don't use key events because this control has vertical items so up/down is tough to manage
                        .onGloballyPositioned {
                            controllerHeight = with(density) { it.size.height.toDp() }
                        },
            )
        }
        AnimatedVisibility(
            state == OverlayViewState.CHAPTERS,
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
                                    state = OverlayViewState.CONTROLLER
                                    true
                                } else {
                                    false
                                }
                            },
                ) {
                    Text(
                        text = stringResource(R.string.chapters),
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
                    if (playlist.hasNext()) {
                        Text(
                            text = stringResource(R.string.queue),
                            style = MaterialTheme.typography.titleLarge,
                            modifier =
                                Modifier
                                    .padding(start = 16.dp, top = 16.dp)
                                    .onFocusChanged {
                                        if (it.isFocused) state = OverlayViewState.QUEUE
                                    }.focusable(),
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            state == OverlayViewState.QUEUE,
            enter = slideInVertically { it / 2 } + fadeIn(),
            exit = slideOutVertically { it / 2 } + fadeOut(),
        ) {
            if (playlist.hasNext()) {
                val items = remember { playlist.upcomingItems() }
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
                                    if (chapters.isNotEmpty()) {
                                        state = OverlayViewState.CHAPTERS
                                    } else {
                                        state = OverlayViewState.CONTROLLER
                                    }
                                    true
                                } else {
                                    false
                                }
                            },
                ) {
                    Text(
                        text = stringResource(R.string.queue),
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
                        itemsIndexed(items) { index, item ->
                            val interactionSource = remember { MutableInteractionSource() }
                            val isFocused = interactionSource.collectIsFocusedAsState().value
                            LaunchedEffect(isFocused) {
                                if (isFocused) controllerViewState.pulseControls()
                            }
                            SeasonCard(
                                item = item,
                                onClick = {
                                    onClickPlaylist.invoke(item)
                                    controllerViewState.hideControls()
                                },
                                onLongClick = {},
                                imageHeight = 140.dp,
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
        }
        AnimatedVisibility(
            showDebugInfo && controllerViewState.controlsVisible,
            modifier =
                Modifier
                    .align(Alignment.TopStart),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .background(AppColors.TransparentBlack50),
            ) {
                Text(
                    text = "Play method: ${currentPlayback?.playMethod?.serialName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 8.dp),
                )
                currentPlayback?.tracks?.letNotEmpty {
                    PlaybackTrackInfo(
                        trackSupport = it,
                    )
                }
            }
        }
    }
}

/**
 * The view state of the overlay
 */
enum class OverlayViewState {
    CONTROLLER,
    CHAPTERS,
    QUEUE,
}

/**
 * A wrapper for the playback controls to show title and other information, plus the actual controls
 */
@Composable
fun Controller(
    title: String?,
    subtitleStreams: List<SubtitleStream>,
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
    currentItemPlayback: ItemPlayback,
    audioStreams: List<AudioStream>,
    nextState: OverlayViewState?,
    currentSegment: MediaSegmentDto?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    seekBarInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onNextStateFocus: () -> Unit = {},
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
            subtitleIndex = currentItemPlayback.subtitleIndex,
            audioIndex = currentItemPlayback.audioIndex,
            audioStreams = audioStreams,
            playbackSpeed = playbackSpeed,
            scale = scale,
            seekBarIntervals = 16,
            seekBack = seekBack,
            seekForward = seekForward,
            skipBackOnResume = skipBackOnResume,
            currentSegment = currentSegment,
        )
        when (nextState) {
            OverlayViewState.CHAPTERS ->
                Text(
                    text = stringResource(R.string.chapters),
                    style = MaterialTheme.typography.titleLarge,
                    modifier =
                        Modifier
                            .padding(start = 16.dp, top = 16.dp)
                            .onFocusChanged {
                                if (it.isFocused) onNextStateFocus.invoke()
                            }.focusable(),
                )

            OverlayViewState.QUEUE ->
                Text(
                    text = stringResource(R.string.queue),
                    style = MaterialTheme.typography.titleLarge,
                    modifier =
                        Modifier
                            .padding(start = 16.dp, top = 16.dp)
                            .onFocusChanged {
                                if (it.isFocused) onNextStateFocus.invoke()
                            }.focusable(),
                )

            else -> Spacer(Modifier.height(32.dp))
        }
    }
}
