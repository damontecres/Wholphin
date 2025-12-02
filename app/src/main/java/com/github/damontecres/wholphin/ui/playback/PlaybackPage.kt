package com.github.damontecres.wholphin.ui.playback

import androidx.activity.compose.BackHandler
import androidx.annotation.Dimension
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPlayPauseButtonState
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.skipBackOnResume
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings.applyToMpv
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings.toSubtitleStyle
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.skipStringRes
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID
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
    viewModel: PlaybackViewModel = hiltViewModel(),
) {
    LifecycleStartEffect(destination) {
        onStopOrDispose {
            viewModel.release()
        }
    }
    LaunchedEffect(destination) {
        viewModel.init(destination, preferences)
    }

    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    when (val st = loading) {
        is LoadingState.Error -> ErrorMessage(st, modifier)
        LoadingState.Pending,
        LoadingState.Loading,
        -> LoadingPage(modifier.background(Color.Black))

        LoadingState.Success -> {
            val prefs = preferences.appPreferences.playbackPreferences
            val scope = rememberCoroutineScope()
            val configuration = LocalConfiguration.current
            val density = LocalDensity.current

            val player = viewModel.player
            val mediaInfo by viewModel.currentMediaInfo.observeAsState()

            val currentPlayback by viewModel.currentPlayback.collectAsState()
            val currentItemPlayback by viewModel.currentItemPlayback.observeAsState(
                ItemPlayback(
                    userId = -1,
                    itemId = UUID.randomUUID(),
                ),
            )
            val currentSegment by viewModel.currentSegment.observeAsState(null)
            var segmentCancelled by remember(currentSegment?.id) { mutableStateOf(false) }

            val cues by viewModel.subtitleCues.observeAsState(listOf())
            var showDebugInfo by remember { mutableStateOf(prefs.showDebugInfo) }

            val nextUp by viewModel.nextUp.observeAsState(null)
            val playlist by viewModel.playlist.observeAsState(Playlist(listOf()))

            val subtitleSearch by viewModel.subtitleSearch.observeAsState(null)
            val subtitleSearchLanguage by viewModel.subtitleSearchLanguage.observeAsState(Locale.current.language)

            var playbackDialog by remember { mutableStateOf<PlaybackDialogType?>(null) }
            OneTimeLaunchedEffect {
                if (prefs.playerBackend == PlayerBackend.MPV) {
                    scope.launch(Dispatchers.Main + ExceptionHandler()) {
                        preferences.appPreferences.interfacePreferences.subtitlesPreferences.applyToMpv(
                            configuration,
                            density,
                        )
                    }
                }
            }
            AmbientPlayerListener(player)
            var contentScale by remember { mutableStateOf(prefs.globalContentScale.scale) }
            var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
            LaunchedEffect(playbackSpeed) { player.setPlaybackSpeed(playbackSpeed) }

            val presentationState = rememberPresentationState(player, false)
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
                if (skipIndicatorDuration > 0 && delta < 0 || skipIndicatorDuration < 0 && delta > 0) {
                    skipIndicatorDuration = 0
                }
                skipIndicatorDuration += delta
                skipPosition = player.currentPosition
            }
            val keyHandler =
                PlaybackKeyHandler(
                    player = player,
                    controlsEnabled = nextUp == null,
                    skipWithLeftRight = true,
                    seekForward = preferences.appPreferences.playbackPreferences.skipForwardMs.milliseconds,
                    seekBack = preferences.appPreferences.playbackPreferences.skipBackMs.milliseconds,
                    controllerViewState = controllerViewState,
                    updateSkipIndicator = updateSkipIndicator,
                    skipBackOnResume = preferences.appPreferences.playbackPreferences.skipBackOnResume,
                    onInteraction = viewModel::reportInteraction,
                    oneClickPause = preferences.appPreferences.playbackPreferences.oneClickPause,
                    onStop = {
                        player.stop()
                        viewModel.navigationManager.goBack()
                    },
                    onPlaybackDialogTypeClick = { playbackDialog = it },
                )

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

                    PlaybackAction.ShowPlaylist -> TODO()
                    PlaybackAction.ShowVideoFilterDialog -> TODO()
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
                        if (pos < player.maxSeekToPreviousPosition && playlist.hasPrevious()) {
                            viewModel.playPrevious()
                        } else {
                            player.seekToPrevious()
                        }
                    }
                }
            }

            val showSegment =
                !segmentCancelled && currentSegment != null &&
                    nextUp == null && !controllerViewState.controlsVisible && skipIndicatorDuration == 0L
            BackHandler(showSegment) {
                segmentCancelled = true
            }

            Box(
                modifier
                    .background(if (nextUp == null) Color.Black else MaterialTheme.colorScheme.background),
            ) {
                val playerSize by animateFloatAsState(if (nextUp == null) 1f else .6f)
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize(playerSize)
                            .align(Alignment.TopCenter)
                            .onKeyEvent(keyHandler::onKeyEvent)
                            .focusRequester(focusRequester)
                            .focusable(),
                ) {
                    PlayerSurface(
                        player = player,
                        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
                        modifier = scaledModifier,
                    )
                    if (presentationState.coverSurface) {
                        val isLoading by rememberPlayerLoadingState(player)
                        Box(
                            Modifier
                                .matchParentSize()
                                .background(Color.Black),
                        ) {
                            if (isLoading) {
                                LoadingPage(focusEnabled = false)
                            }
                        }
                    }

                    // If D-pad skipping, show the amount skipped in an animation
                    if (!controllerViewState.controlsVisible && skipIndicatorDuration != 0L) {
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

                    // Subtitles
                    if (skipIndicatorDuration == 0L && currentItemPlayback.subtitleIndexEnabled) {
                        val maxSize by animateFloatAsState(if (controllerViewState.controlsVisible) .7f else 1f)
                        AndroidView(
                            factory = { context ->
                                SubtitleView(context).apply {
                                    preferences.appPreferences.interfacePreferences.subtitlesPreferences.let {
                                        setStyle(it.toSubtitleStyle())
                                        setFixedTextSize(Dimension.SP, it.fontSize.toFloat())
                                        setBottomPaddingFraction(it.margin.toFloat() / 100f)
                                    }
                                }
                            },
                            update = {
                                it.setCues(cues)
                            },
                            onReset = {
                                it.setCues(null)
                            },
                            modifier =
                                Modifier
                                    .fillMaxSize(maxSize)
                                    .align(Alignment.TopCenter)
                                    .background(Color.Transparent),
                        )
                    }

                    // The playback controls
                    AnimatedVisibility(
                        controllerViewState.controlsVisible,
                        Modifier,
                        slideInVertically { it },
                        slideOutVertically { it },
                    ) {
                        PlaybackOverlay(
                            modifier =
                                Modifier
                                    .padding(WindowInsets.systemBars.asPaddingValues())
                                    .fillMaxSize()
                                    .background(Color.Transparent),
                            item = currentPlayback?.item,
                            playerControls = player,
                            controllerViewState = controllerViewState,
                            showPlay = playPauseState.showPlay,
                            previousEnabled = true,
                            nextEnabled = playlist.hasNext(),
                            seekEnabled = true,
                            seekForward = preferences.appPreferences.playbackPreferences.skipForwardMs.milliseconds,
                            seekBack = preferences.appPreferences.playbackPreferences.skipBackMs.milliseconds,
                            skipBackOnResume = preferences.appPreferences.playbackPreferences.skipBackOnResume,
                            onPlaybackActionClick = onPlaybackActionClick,
                            onClickPlaybackDialogType = { playbackDialog = it },
                            onSeekBarChange = seekBarState::onValueChange,
                            showDebugInfo = showDebugInfo,
                            currentPlayback = currentPlayback,
                            chapters = mediaInfo?.chapters ?: listOf(),
                            trickplayInfo = mediaInfo?.trickPlayInfo,
                            trickplayUrlFor = viewModel::getTrickplayUrl,
                            playlist = playlist,
                            onClickPlaylist = {
                                viewModel.playItemInPlaylist(it)
                            },
                            currentSegment = currentSegment,
                            showClock = preferences.appPreferences.interfacePreferences.showClock,
                        )
                    }
                }

                // Ask to skip intros, etc button
                AnimatedVisibility(
                    showSegment,
                    modifier =
                        Modifier
                            .padding(40.dp)
                            .align(Alignment.BottomEnd),
                ) {
                    currentSegment?.let { segment ->
                        val focusRequester = remember { FocusRequester() }
                        LaunchedEffect(Unit) {
                            focusRequester.tryRequestFocus()
                            delay(10.seconds)
                            segmentCancelled = true
                        }
                        Button(
                            onClick = {
                                segmentCancelled = true
                                player.seekTo(segment.endTicks.ticks.inWholeMilliseconds)
                            },
                            modifier = Modifier.focusRequester(focusRequester),
                        ) {
                            Text(
                                text = stringResource(segment.type.skipStringRes),
                            )
                        }
                    }
                }

                // Next up episode
                BackHandler(nextUp != null) {
                    if (player.isPlaying) {
                        scope.launch(ExceptionHandler()) {
                            viewModel.cancelUpNextEpisode()
                        }
                    } else {
                        viewModel.navigationManager.goBack()
                    }
                }
                AnimatedVisibility(
                    nextUp != null,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter),
                ) {
                    nextUp?.let {
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
                            imageUrl = it.imageUrl,
                            aspectRatio =
                                it.data.primaryImageAspectRatio?.toFloat()
                                    ?: AspectRatios.WIDE,
                            onClick = {
                                viewModel.reportInteraction()
                                viewModel.playNextUp()
                            },
                            timeLeft = if (autoPlayEnabled) timeLeft.seconds else null,
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

            subtitleSearch?.let { state ->
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
                Dialog(
                    onDismissRequest = onDismissRequest,
                    properties =
                        DialogProperties(
                            usePlatformDefaultWidth = false,
                        ),
                ) {
                    DownloadSubtitlesContent(
                        state = state,
                        language = subtitleSearchLanguage,
                        onSearch = { lang ->
                            viewModel.searchForSubtitles(lang)
                        },
                        onClickDownload = {
                            viewModel.downloadAndSwitchSubtitles(it.id, wasPlaying)
                        },
                        onDismissRequest = onDismissRequest,
                        modifier =
                            Modifier
                                .widthIn(max = 640.dp)
                                .heightIn(max = 400.dp),
                    )
                }
            }

            playbackDialog?.let { type ->
                PlaybackDialog(
                    type = type,
                    settings =
                        PlaybackSettings(
                            showDebugInfo = showDebugInfo,
                            audioIndex = currentItemPlayback?.audioIndex,
                            audioStreams = mediaInfo?.audioStreams.orEmpty(),
                            subtitleIndex = currentItemPlayback?.subtitleIndex,
                            subtitleStreams = mediaInfo?.subtitleStreams.orEmpty(),
                            playbackSpeed = playbackSpeed,
                            contentScale = contentScale,
                        ),
                    onDismissRequest = {
                        playbackDialog = null
                        if (controllerViewState.controlsVisible) {
                            controllerViewState.pulseControls()
                        }
                    },
                    onControllerInteraction = {
                        controllerViewState.pulseControls(Long.MAX_VALUE)
                    },
                    onClickPlaybackDialogType = { playbackDialog = it },
                    onPlaybackActionClick = onPlaybackActionClick,
                )
            }
        }
    }
}
