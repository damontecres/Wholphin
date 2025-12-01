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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.data.model.aspectRatioFloat
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.TimeFormatter
import com.github.damontecres.wholphin.ui.cards.ChapterCard
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.TimeDisplay
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.TrickplayInfo
import java.time.LocalTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val titleTextSize = 28.sp
private val subtitleTextSize = 18.sp

/**
 * The overlay during playback showing controls, seek preview image, debug info, etc
 */
@Composable
fun PlaybackOverlay(
    item: BaseItem?,
    chapters: List<Chapter>,
    playerControls: Player,
    controllerViewState: ControllerViewState,
    showPlay: Boolean,
    showClock: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekBarChange: (Long) -> Unit,
    showDebugInfo: Boolean,
    currentPlayback: CurrentPlayback?,
    currentSegment: MediaSegmentDto?,
    modifier: Modifier = Modifier,
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
            if (item?.title.isNotNullOrBlank()) with(density) { titleTextSize.toDp() } else 0.dp
        }
    val subtitleHeight =
        remember {
            if (item?.subtitleLong.isNotNullOrBlank()) with(density) { subtitleTextSize.toDp() } else 0.dp
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
                title = item?.title,
                subtitle = item?.subtitleLong,
                playerControls = playerControls,
                controllerViewState = controllerViewState,
                showPlay = showPlay,
                showClock = showClock,
                previousEnabled = previousEnabled,
                nextEnabled = nextEnabled,
                seekEnabled = seekEnabled,
                seekBack = seekBack,
                skipBackOnResume = skipBackOnResume,
                seekForward = seekForward,
                onPlaybackActionClick = onPlaybackActionClick,
                onClickPlaybackDialogType = onClickPlaybackDialogType,
                onSeekProgress = {
                    onSeekBarChange(it)
                    seekProgressMs = it
                },
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
                                aspectRatio = item?.data?.aspectRatioFloat ?: AspectRatios.WIDE,
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
                                } else if (isDown(e)) {
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

        AnimatedVisibility(
            seekProgressPercent >= 0 && seekBarFocused,
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth(.95f),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .offsetByPercent(
                                xPercentage = seekProgressPercent.coerceIn(0f, 1f),
                            ).padding(bottom = controllerHeight - titleHeight - subtitleHeight),
                ) {
                    if (trickplayInfo != null) {
                        val tilesPerImage = trickplayInfo.tileWidth * trickplayInfo.tileHeight
                        val index =
                            (seekProgressMs / trickplayInfo.interval).toInt() / tilesPerImage
                        val imageUrl = trickplayUrlFor(index)

                        if (imageUrl != null) {
                            SeekPreviewImage(
                                modifier =
                                Modifier,
                                previewImageUrl = imageUrl,
                                duration = playerControls.duration,
                                seekProgressMs = seekProgressMs,
                                videoWidth = trickplayInfo.width,
                                videoHeight = trickplayInfo.height,
                                trickPlayInfo = trickplayInfo,
                            )
                        }
                    }
                    Text(
                        text = (seekProgressMs / 1000L).seconds.toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        AnimatedVisibility(
            !showDebugInfo && item?.logoImageUrl.isNotNullOrBlank() && controllerViewState.controlsVisible,
            modifier =
                Modifier
                    .align(Alignment.TopStart),
        ) {
            AsyncImage(
                model = item?.logoImageUrl,
                contentDescription = "Logo",
                modifier =
                    Modifier
                        .sizeIn(maxWidth = 180.dp, maxHeight = 100.dp)
                        .padding(16.dp),
            )
        }
        AnimatedVisibility(
            !showDebugInfo && showClock && controllerViewState.controlsVisible,
            modifier =
                Modifier
                    .align(Alignment.TopEnd),
        ) {
            TimeDisplay()
        }
        AnimatedVisibility(
            showDebugInfo && controllerViewState.controlsVisible,
            modifier =
                Modifier
                    .align(Alignment.TopStart),
        ) {
            PlaybackDebugOverlay(
                currentPlayback = currentPlayback,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(AppColors.TransparentBlack50),
            )
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
    playerControls: Player,
    controllerViewState: ControllerViewState,
    showClock: Boolean,
    showPlay: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    seekEnabled: Boolean,
    seekBack: Duration,
    skipBackOnResume: Duration?,
    seekForward: Duration,
    onPlaybackActionClick: (PlaybackAction) -> Unit,
    onClickPlaybackDialogType: (PlaybackDialogType) -> Unit,
    onSeekProgress: (Long) -> Unit,
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
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .align(Alignment.End),
            ) {
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        fontSize = subtitleTextSize,
                    )
                }
                if (showClock) {
                    var remaining by remember { mutableStateOf((playerControls.duration - playerControls.currentPosition).milliseconds) }
                    LaunchedEffect(playerControls) {
                        while (isActive) {
                            remaining =
                                (playerControls.duration - playerControls.currentPosition).milliseconds
                            delay(1.seconds)
                        }
                    }
                    val endTime = LocalTime.now().plusSeconds(remaining.inWholeSeconds)
                    val endTimeStr = TimeFormatter.format(endTime)
                    Text(
                        text = "Ends $endTimeStr",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.labelLarge,
                        modifier =
                            Modifier
                                .padding(end = 32.dp),
                    )
                }
            }
        }
        // TODO need to move these up a level?
        val moreFocusRequester = remember { FocusRequester() }
        val captionFocusRequester = remember { FocusRequester() }
        val settingsFocusRequester = remember { FocusRequester() }
        PlaybackControls(
            modifier = Modifier.fillMaxWidth(),
            playerControls = playerControls,
            onPlaybackActionClick = onPlaybackActionClick,
            controllerViewState = controllerViewState,
            onSeekProgress = {
                onSeekProgress(it)
            },
            showPlay = showPlay,
            previousEnabled = previousEnabled,
            nextEnabled = nextEnabled,
            seekEnabled = seekEnabled,
            seekBarInteractionSource = seekBarInteractionSource,
            seekBarIntervals = 16,
            seekBack = seekBack,
            seekForward = seekForward,
            skipBackOnResume = skipBackOnResume,
            currentSegment = currentSegment,
            onClickPlaybackDialogType = onClickPlaybackDialogType,
            moreFocusRequester = moreFocusRequester,
            captionFocusRequester = captionFocusRequester,
            settingsFocusRequester = settingsFocusRequester,
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
