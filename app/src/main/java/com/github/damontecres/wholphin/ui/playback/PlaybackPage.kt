package com.github.damontecres.wholphin.ui.playback

import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.children
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.mpv.MpvPlayer
import com.github.damontecres.wholphin.preferences.AssPlaybackMode
import com.github.damontecres.wholphin.preferences.DpadSeekMode
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.skipBackOnResume
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.overlay.DpadSeekOverlay
import com.github.damontecres.wholphin.ui.playback.overlay.PauseIndicator
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackAction
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackOverlay
import com.github.damontecres.wholphin.ui.playback.overlay.SkipIndicator
import com.github.damontecres.wholphin.ui.playback.overlay.SkipSegmentButton
import com.github.damontecres.wholphin.ui.playback.overlay.rememberSeekBarState
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings.applyTo
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings.calculateEdgeSize
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.Media3SubtitleOverride
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The actual playback page which shows media & playback controls
 */
@OptIn(UnstableApi::class)
@Composable
fun PlaybackPage(
    preferences: UserPreferences,
    destination: Destination,
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel =
        hiltViewModel<PlaybackViewModel, PlaybackViewModel.Factory>(
            creationCallback = { it.create(destination) },
        ),
) {
    LifecycleStartEffect(destination) {
        onStopOrDispose {
            viewModel.release()
        }
    }
    val state by viewModel.state.collectAsState()
    when (val st = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        LoadingState.Pending,
        LoadingState.Loading,
        -> {
            LoadingPage(modifier.background(Color.Black))
        }

        LoadingState.Success -> {
            val playerState by viewModel.currentPlayer.collectAsState()
            PlaybackPageContent(
                playerInstance = playerState!!,
                preferences = preferences,
                destination = destination,
                viewModel = viewModel,
                modifier = modifier,
            )
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun PlaybackPageContent(
    playerInstance: PlayerInstance,
    preferences: UserPreferences,
    destination: Destination,
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel,
) {
    val state by viewModel.state.collectAsState()
    val subtitleSearchState by viewModel.subtitleSearchState.collectAsState()
    val player = playerInstance.player
    val playerBackend = playerInstance.backend

    val prefs = preferences.appPreferences.playbackPreferences
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val userDto by viewModel.currentUserDto.collectAsState()

    var showDebugInfo by remember { mutableStateOf(prefs.showDebugInfo) }

    var playbackDialog by remember { mutableStateOf<PlaybackDialogType?>(null) }

    var contentScale by remember(playerBackend) {
        mutableStateOf(
            if (playerBackend == PlayerBackend.MPV) {
                ContentScale.FillBounds
            } else {
                prefs.globalContentScale.scale
            },
        )
    }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    LaunchedEffect(playbackSpeed) { player.setPlaybackSpeed(playbackSpeed) }

    LaunchedEffect(state.currentPlayback?.subtitleDelay) {
        (player as? MpvPlayer)?.subtitleDelay =
            state.currentPlayback?.subtitleDelay ?: Duration.ZERO
    }

    val presentationState = rememberPresentationState(player, false)
    val playbackState by rememberPlayerState(player)
    var showBuffering by remember { mutableStateOf(false) }
    LaunchedEffect(playbackState) {
        if (playbackState == PlayerState.BUFFERING) {
            // Delay before showing the loading indicator
            // So if buffering is quick, the UI won't flash
            delay(250)
            showBuffering = true
        } else {
            showBuffering = false
        }
    }
    val scaledModifier =
        Modifier.resizeWithContentScale(contentScale, presentationState.videoSizeDp)
    val focusRequester = remember { FocusRequester() }
    val playPauseState = rememberPlayPauseButtonState(player)
    val seekBarState = rememberSeekBarState(player, scope)

    LaunchedEffect(Unit) {
        focusRequester.tryRequestFocus()
    }
    val controllerViewState = remember { viewModel.controllerViewState }

    var skipIndicatorDuration by remember { mutableLongStateOf(0L) }
    LaunchedEffect(controllerViewState.controlsVisible) {
        // If controller shows/hides, immediately cancel the skip indicator
        skipIndicatorDuration = 0L
    }
    var skipPosition by remember { mutableLongStateOf(0L) }
    val updateSkipIndicator = { delta: Long ->
        if ((skipIndicatorDuration > 0 && delta < 0) || (skipIndicatorDuration < 0 && delta > 0)) {
            skipIndicatorDuration = 0
        }
        skipIndicatorDuration += delta
        skipPosition = player.currentPosition
    }
    val onDpadSeek: (Long) -> Unit = { deltaMs ->
        if (skipIndicatorDuration == 0L) {
            skipPosition = player.currentPosition
        }
        if ((skipIndicatorDuration > 0 && deltaMs < 0) ||
            (skipIndicatorDuration < 0 && deltaMs > 0)
        ) {
            skipIndicatorDuration = 0L
        }
        skipIndicatorDuration += deltaMs
        skipPosition =
            (skipPosition + deltaMs).coerceIn(
                minimumValue = 0L,
                maximumValue = player.duration.coerceAtLeast(0L),
            )
        seekBarState.onValueChange(skipPosition)
    }
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val keyHandler =
        remember(isLtr, preferences) {
            PlaybackKeyHandler(
                isLtr = isLtr,
                player = player,
                controlsEnabled = state.nextUp == null,
                skipWithLeftRight = true,
                seekForward = preferences.appPreferences.playbackPreferences.skipForwardMs.milliseconds,
                seekBack = preferences.appPreferences.playbackPreferences.skipBackMs.milliseconds,
                getDurationMs = { player.duration.coerceAtLeast(0L) },
                controllerViewState = controllerViewState,
                updateSkipIndicator = updateSkipIndicator,
                clearSkipIndicator = { skipIndicatorDuration = 0 },
                skipBackOnResume = preferences.appPreferences.playbackPreferences.skipBackOnResume,
                onInteraction = viewModel::reportInteraction,
                oneClickPause = preferences.appPreferences.playbackPreferences.oneClickPause,
                onStop = {
                    player.stop()
                    viewModel.navigationManager.goBack()
                },
                onPlaybackDialogTypeClick = { playbackDialog = it },
                isDpadSeekVisible = {
                    prefs.dpadSeekMode == DpadSeekMode.TRICKPLAY && skipIndicatorDuration != 0L
                },
                onDpadSeek = onDpadSeek,
                dpadSeekMode = prefs.dpadSeekMode,
            )
        }

    val onPlaybackActionClick: (PlaybackAction) -> Unit = {
        when (it) {
            is PlaybackAction.PlaybackSpeed -> {
                playbackSpeed = it.value
            }

            is PlaybackAction.Scale -> {
                contentScale = it.scale
            }

            PlaybackAction.ShowDebug -> {
                showDebugInfo = !showDebugInfo
            }

            PlaybackAction.ShowPlaylist -> {
                TODO()
            }

            PlaybackAction.ShowVideoFilterDialog -> {
                TODO()
            }

            is PlaybackAction.ToggleAudio -> {
                viewModel.changeAudioStream(it.index)
            }

            is PlaybackAction.ToggleCaptions -> {
                viewModel.changeSubtitleStream(it.index)
            }

            PlaybackAction.SearchCaptions -> {
                controllerViewState.hideControls()
                viewModel.searchForSubtitles()
            }

            PlaybackAction.Next -> {
                // TODO focus is lost
                viewModel.playNextUp()
            }

            PlaybackAction.Previous -> {
                val pos = player.currentPosition
                if (pos < player.maxSeekToPreviousPosition && state.hasPrevious) {
                    viewModel.playPrevious()
                } else {
                    player.seekToPrevious()
                }
            }
        }
    }

    val showSegment =
        state.currentSegment?.interacted == false &&
            state.nextUp == null && !controllerViewState.controlsVisible && skipIndicatorDuration == 0L
    BackHandler(showSegment) {
        viewModel.updateSegment(state.currentSegment?.segment?.id, true)
    }

    Box(
        modifier
            .background(if (state.nextUp == null) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        val playerSize by animateFloatAsState(if (state.nextUp == null) 1f else .6f)
        Box(
            modifier =
                Modifier
                    .fillMaxSize(playerSize)
                    .align(Alignment.TopCenter)
                    .onKeyEvent(keyHandler::onKeyEvent)
                    .focusRequester(focusRequester)
                    .focusable(),
        ) {
            var playerSurfaceSize by remember { mutableStateOf(IntSize.Zero) }
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                modifier =
                    scaledModifier.onSizeChanged {
                        playerSurfaceSize = it
                    },
            )
            if (presentationState.coverSurface) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black),
                ) {
                    LoadingPage(focusEnabled = false)
                }
            } else {
                AnimatedVisibility(
                    visible = showBuffering,
                    enter = fadeIn(tween(easing = LinearEasing)),
                    exit = fadeOut(tween(easing = LinearEasing)),
                    modifier = Modifier.matchParentSize(),
                ) {
                    LoadingPage(
                        focusEnabled = false,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(AppColors.TransparentBlack25),
                    )
                }
            }

            AnimatedVisibility(
                visible =
                    !controllerViewState.controlsVisible &&
                        skipIndicatorDuration != 0L &&
                        prefs.dpadSeekMode == DpadSeekMode.TRICKPLAY,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter),
            ) {
                DpadSeekOverlay(
                    player = player,
                    seekPositionMs = skipPosition,
                    trickplayInfo = state.currentMediaInfo.trickPlayInfo,
                    trickplayUrlFor = viewModel::getTrickplayUrl,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                            .fillMaxWidth(.95f),
                )
                // Clear the overlay after a delay
                LaunchedEffect(skipIndicatorDuration) {
                    delay(1.5.seconds)
                    skipIndicatorDuration = 0L
                }
            }

            // If D-pad skipping, show the amount skipped in an animation
            if (!controllerViewState.controlsVisible && skipIndicatorDuration != 0L && prefs.dpadSeekMode != DpadSeekMode.TRICKPLAY) {
                // Skip time mode: show seek distance indicator
                SkipIndicator(
                    durationMs = skipIndicatorDuration,
                    onFinish = {
                        skipIndicatorDuration = 0L
                    },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 70.dp),
                )
                // Show a small progress bar along the bottom of the screen
                val showSkipProgress = true // TODO get from preferences
                if (showSkipProgress) {
                    val percent = skipPosition.toFloat() / player.duration.toFloat()
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .background(MaterialTheme.colorScheme.border)
                                .clip(RectangleShape)
                                .height(3.dp)
                                .fillMaxWidth(percent),
                    )
                }
            }

            val controlsVisible =
                remember(controllerViewState.controlsVisible, playbackDialog, subtitleSearchState) {
                    controllerViewState.controlsVisible ||
                        playbackDialog != null ||
                        subtitleSearchState.status != SubtitleSearchStatus.Inactive
                }
            if (!controlsVisible && skipIndicatorDuration == 0L) {
                PauseIndicator(
                    player = player,
                    modifier =
                        Modifier
                            .align(Alignment.Center),
                )
            }

            // The playback controls

            PlaybackOverlay(
                modifier =
                    Modifier
                        .padding(WindowInsets.systemBars.asPaddingValues())
                        .fillMaxSize()
                        .background(Color.Transparent),
                item = state.currentPlayback?.item,
                player = player,
                controllerViewState = controllerViewState,
                showPlay = playPauseState.showPlay,
                previousEnabled = true,
                nextEnabled = state.hasNext,
                seekEnabled = true,
                seekForward = preferences.appPreferences.playbackPreferences.skipForwardMs.milliseconds,
                seekBack = preferences.appPreferences.playbackPreferences.skipBackMs.milliseconds,
                skipBackOnResume = preferences.appPreferences.playbackPreferences.skipBackOnResume,
                onPlaybackActionClick = onPlaybackActionClick,
                onClickPlaybackDialogType = { playbackDialog = it },
                onSeekBarChange = seekBarState::onValueChange,
                showDebugInfo = showDebugInfo,
                currentPlayback = state.currentPlayback,
                chapters = state.currentMediaInfo.chapters,
                trickplayInfo = state.currentMediaInfo.trickPlayInfo,
                trickplayUrlFor = viewModel::getTrickplayUrl,
                queue = remember(state.playlistIndex, state.playlist) { state.upcomingItems },
                onClickPlaylist = viewModel::playItemInPlaylist,
                currentSegment = state.currentSegment?.segment,
                showClock = preferences.appPreferences.interfacePreferences.showClock,
                analyticsState = state.analyticsState,
            )

            var subtitleStyleAppliedForHdr by remember { mutableStateOf(state.currentMediaInfo.videoStream?.hdr == true) }
            val subtitleSettings =
                remember(state.currentMediaInfo) {
                    Timber.v("subtitle choice: ${state.currentMediaInfo.videoStream?.hdr}")
                    val useSeparate =
                        preferences.appPreferences.interfacePreferences.subtitlesPreferences.useSeparateHdr
                    if (useSeparate && state.currentMediaInfo.videoStream?.hdr == true) {
                        preferences.appPreferences.interfacePreferences.hdrSubtitlesPreferences
                    } else {
                        preferences.appPreferences.interfacePreferences.subtitlesPreferences
                    }
                }
            val subtitleImageOpacity =
                remember(subtitleSettings) { subtitleSettings.imageSubtitleOpacity / 100f }

            // Subtitles
            val subtitleMaxSize by animateFloatAsState(if (controllerViewState.controlsVisible) .7f else 1f)
            val isImageSubtitles =
                remember(state.subtitleCues) { state.subtitleCues.firstOrNull()?.bitmap != null }
            var cueCount by remember { mutableIntStateOf(0) }

            val subtitleVisible =
                skipIndicatorDuration == 0L &&
                    state.currentPlayback?.subtitleIndexEnabled == true &&
                    !presentationState.coverSurface

            AndroidView(
                factory = { context ->
                    SubtitleView(context).apply {
                        subtitleSettings.applyTo(this)
                        playerInstance.assHandler?.let { assHandler ->
                            if (prefs.overrides.assPlaybackMode == AssPlaybackMode.ASS_LIBASS) {
                                Timber.v("Adding AssSubtitleView")
                                addView(
                                    AssSubtitleView(context, assHandler).apply {
                                        layoutParams =
                                            FrameLayout
                                                .LayoutParams(
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                ).apply { gravity = Gravity.CENTER }
                                    },
                                )
                            }
                        }
                    }
                },
                update = { subtitleView ->
                    if (subtitleStyleAppliedForHdr != state.currentMediaInfo.videoStream?.hdr) {
                        // If the media has switched between SDR & HDR, reapply subtitle settings
                        Timber.v("Switching SDR/HDR, reapplying subtitle style")
                        subtitleSettings.applyTo(subtitleView)
                    }
                    subtitleView.setCues(state.subtitleCues)
                    if (state.subtitleCues.size > cueCount) {
                        // The output creates a painter for each cue, so need to apply the changes when the number of cues increases
                        Media3SubtitleOverride(subtitleSettings.calculateEdgeSize(density))
                            .apply(subtitleView)
                        cueCount = state.subtitleCues.size
                    }
                    subtitleView.children.firstOrNull { it is AssSubtitleView }?.let {
                        (it as? AssSubtitleView)?.apply {
                            val resized =
                                layoutParams.let { it.width != playerSurfaceSize.width || it.height != playerSurfaceSize.height }

                            if (resized && playerSurfaceSize.width > 0 && playerSurfaceSize.height > 0) {
                                Timber.v("Resizing AssSubtitleView: %s", playerSurfaceSize)
                                layoutParams =
                                    FrameLayout
                                        .LayoutParams(
                                            playerSurfaceSize.width,
                                            playerSurfaceSize.height,
                                        ).apply { gravity = Gravity.CENTER }
                            }
                        }
                    }
                },
                onReset = {
                    it.setCues(null)
                },
                modifier =
                    Modifier
                        .fillMaxSize(subtitleMaxSize)
                        .align(Alignment.TopCenter)
                        .background(Color.Transparent)
                        .graphicsLayer {
                            alpha =
                                if (!subtitleVisible) {
                                    0f
                                } else if (isImageSubtitles) {
                                    subtitleImageOpacity
                                } else {
                                    1f
                                }
                        },
            )
        }

        // Ask to skip intros, etc button
        AnimatedVisibility(
            showSegment,
            modifier =
                Modifier
                    .padding(40.dp)
                    .align(Alignment.BottomEnd),
        ) {
            state.currentSegment?.let { segment ->
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    focusRequester.tryRequestFocus()
                    delay(10.seconds)
                    viewModel.updateSegment(segment.segment.id, true)
                }
                SkipSegmentButton(
                    type = segment.segment.type,
                    onClick = {
                        viewModel.updateSegment(segment.segment.id, false)
                    },
                    modifier = Modifier.focusRequester(focusRequester),
                )
            }
        }

        // Next up episode
        BackHandler(state.nextUp != null) {
            if (player.isPlaying) {
                scope.launch(ExceptionHandler()) {
                    viewModel.cancelUpNextEpisode()
                }
            } else {
                viewModel.navigationManager.goBack()
            }
        }
        AnimatedVisibility(
            state.nextUp != null,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter),
        ) {
            state.nextUp?.let {
                var autoPlayEnabled by remember { mutableStateOf(viewModel.shouldAutoPlayNextUp()) }
                var timeLeft by remember {
                    mutableLongStateOf(
                        preferences.appPreferences.playbackPreferences.autoPlayNextDelaySeconds,
                    )
                }
                BackHandler(timeLeft > 0 && autoPlayEnabled) {
                    timeLeft = -1
                    autoPlayEnabled = false
                }
                if (autoPlayEnabled) {
                    LaunchedEffect(Unit) {
                        if (timeLeft == 0L) {
                            viewModel.playNextUp()
                        } else {
                            while (timeLeft > 0) {
                                delay(1.seconds)
                                timeLeft--
                            }
                            if (timeLeft == 0L && autoPlayEnabled) {
                                viewModel.playNextUp()
                            }
                        }
                    }
                }
                NextUpEpisode(
                    title =
                        listOfNotNull(
                            it.data.seasonEpisode,
                            it.name,
                        ).joinToString(" - "),
                    description = it.data.overview,
                    imageUrl = LocalImageUrlService.current.rememberImageUrl(it),
                    aspectRatio = it.aspectRatio ?: AspectRatios.WIDE,
                    onClick = {
                        viewModel.reportInteraction()
                        controllerViewState.hideControls()
                        viewModel.playNextUp()
                    },
                    timeLeft = if (autoPlayEnabled) timeLeft.seconds else null,
                    runtime = it.data.runTimeTicks?.ticks,
                    modifier =
                        Modifier
                            .padding(8.dp)
//                                    .height(128.dp)
                            .fillMaxHeight(1 - playerSize)
                            .fillMaxWidth(.66f)
                            .align(Alignment.BottomCenter)
                            .background(
                                MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp),
                                shape = RoundedCornerShape(8.dp),
                            ),
                )
            }
        }
    }

    if (subtitleSearchState.status != SubtitleSearchStatus.Inactive) {
        val wasPlaying = remember { player.isPlaying }
        LaunchedEffect(Unit) {
            player.pause()
        }
        val onDismissRequest = {
            if (wasPlaying) {
                player.play()
            }
            viewModel.cancelSubtitleSearch()
        }
        BasicDialog(
            onDismissRequest = onDismissRequest,
            properties =
                DialogProperties(
                    usePlatformDefaultWidth = false,
                ),
        ) {
            Box(modifier = Modifier.padding(24.dp)) {
                DownloadSubtitlesContent(
                    state = subtitleSearchState.status,
                    language = subtitleSearchState.language,
                    onSearch = { lang ->
                        viewModel.searchForSubtitles(lang)
                    },
                    onClickDownload = {
                        viewModel.downloadAndSwitchSubtitles(it.id, wasPlaying)
                    },
                    modifier =
                        Modifier
                            .widthIn(max = 640.dp)
                            .heightIn(max = 400.dp),
                )
            }
        }
    }

    playbackDialog?.let { type ->
        PlaybackDialog(
            type = type,
            settings =
                PlaybackSettings(
                    showDebugInfo = showDebugInfo,
                    audioIndex = state.currentPlayback?.audioIndex,
                    audioStreams = state.currentMediaInfo.audioStreams,
                    subtitleIndex = state.currentPlayback?.subtitleIndex,
                    subtitleStreams = state.currentMediaInfo.subtitleStreams,
                    playbackSpeed = playbackSpeed,
                    contentScale = contentScale,
                    subtitleDelay = state.currentPlayback?.subtitleDelay ?: Duration.ZERO,
                    hasSubtitleDownloadPermission =
                        remember(userDto) { userDto?.policy?.let { it.isAdministrator || it.enableSubtitleManagement } == true },
                    // TODO Passing through audio prevents changing playback speed
                    // See https://github.com/damontecres/Wholphin/issues/164
                    playbackSpeedEnabled = playerBackend == PlayerBackend.MPV || state.currentPlayback?.audioDecoder != null,
                ),
            onDismissRequest = {
                playbackDialog =
                    when (type) {
                        // Go back to settings dialog
                        PlaybackDialogType.PLAYBACK_SPEED,
                        PlaybackDialogType.VIDEO_SCALE,
                        -> PlaybackDialogType.SETTINGS

                        else -> null
                    }
                if (controllerViewState.controlsVisible) {
                    controllerViewState.pulseControls()
                }
            },
            onControllerInteraction = {
                controllerViewState.pulseControls(Long.MAX_VALUE)
            },
            onClickPlaybackDialogType = {
                if (it == PlaybackDialogType.SUBTITLE_DELAY) {
                    // Hide controls so subtitles are fully visible
                    controllerViewState.hideControls()
                }
                playbackDialog = it
            },
            onPlaybackActionClick = onPlaybackActionClick,
            onChangeSubtitleDelay = { viewModel.updateSubtitleDelay(it) },
            enableSubtitleDelay = player is MpvPlayer,
            enableVideoScale = player !is MpvPlayer,
        )
    }
}
