package com.github.damontecres.dolphin.ui.playback

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.Chapter
import com.github.damontecres.dolphin.ui.AppColors
import com.github.damontecres.dolphin.ui.cards.ChapterCard
import com.github.damontecres.dolphin.ui.ifElse
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.letNotEmpty
import org.jellyfin.sdk.model.api.TrickplayInfo
import kotlin.time.Duration

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
//    val chapterRowFocused = chapterInteractionSources.any { it.collectIsFocusedAsState().value }
    var chapterRowFocused by remember { mutableStateOf(false) }

    val titleTextStyle = MaterialTheme.typography.displaySmall
    val subtitleTextStyle = MaterialTheme.typography.headlineMedium
    val density = LocalDensity.current

    val titleHeight =
        if (title.isNotNullOrBlank()) with(density) { titleTextStyle.fontSize.toDp() } else 0.dp
    val subtitleHeight =
        if (subtitle.isNotNullOrBlank()) with(density) { subtitleTextStyle.fontSize.toDp() } else 0.dp

    // Calculate height based on content
    // Base height (with or w/o chapters) + title + subtitle
    // The extra 8dp is for padding between title, subtitle, and playback controls
    // When chapter row is focused, the title/subtitle/playback controls will be hidden, but need extra height for the chapter images
    val height by animateDpAsState(
        (if (chapters.isNotEmpty()) 184.dp else 140.dp) +
            (if (chapterRowFocused) 40.dp else 0.dp) +
            (
                if (!chapterRowFocused && title.isNotNullOrBlank()) {
                    titleHeight + 8.dp
                } else {
                    0.dp
                }
            ) +
            (
                if (!chapterRowFocused && subtitle.isNotNullOrBlank()) {
                    subtitleHeight + 8.dp
                } else {
                    0.dp
                }
            ),
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = if (chapterRowFocused) 32.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .padding(bottom = 16.dp)
                    .height(height)
                    .fillMaxWidth(),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .padding(start = 16.dp)
                                .alpha(if (chapterRowFocused) 0f else 1f),
                    ) {
                        title?.let {
                            Text(
                                text = it,
                                style = titleTextStyle,
                            )
                        }
                        subtitle?.let {
                            Text(
                                text = it,
                                style = subtitleTextStyle,
                            )
                        }
                    }
                    PlaybackControls(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .alpha(if (chapterRowFocused) 0f else 1f),
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
                        subtitleIndex = currentPlayback?.subtitleIndex,
                        audioIndex = currentPlayback?.audioIndex,
                        audioStreams = audioStreams,
                        playbackSpeed = playbackSpeed,
                        scale = scale,
                        seekBarIntervals = 16,
                        seekBack = seekBack,
                        seekForward = seekForward,
                    )
                }
            }

            if (chapters.isNotEmpty()) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
//                                .offset(y = 200.dp)
                                .padding(8.dp),
                    ) {
                        Text(
                            text = "Chapters",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        val focusRequester = remember { FocusRequester() }
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
                                        chapterRowFocused = it.hasFocus || it.isFocused
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
                                    ).padding(bottom = height - titleHeight - subtitleHeight),
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
