package com.github.damontecres.wholphin.ui.detail.livetv

import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.LiveTvPreferences
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import eu.wewox.programguide.ProgramGuide
import eu.wewox.programguide.ProgramGuideDimensions
import eu.wewox.programguide.ProgramGuideItem
import eu.wewox.programguide.rememberSaveableProgramGuideState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.abs

@Composable
fun TvGuideGrid(
    onRowPosition: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val preferences by viewModel.preferences.data
        .collectAsState(AppPreferences.getDefaultInstance())
    val tvPrefs = preferences.interfacePreferences.liveTvPreferences
    var showViewOptions by remember { mutableStateOf(false) }
    var showChannelDialog by remember { mutableStateOf<Pair<Int, TvChannel>?>(null) }

    when (val st = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Loading,
        LoadingState.Success,
        -> {
            val context = LocalContext.current
            val programDialogState =
                viewModel.programDialogState
                    .collectAsState()
                    .value.loading

            var showItemDialog by remember { mutableStateOf<Int?>(null) }
            val focusRequester = remember { FocusRequester() }
            val buttonFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.tryRequestFocus()
            }
            var focusedPosition by rememberPosition(0, 0)
            val focusedProgram =
                remember(focusedPosition) {
                    focusedPosition.let {
                        val channelId = state.channels.getOrNull(it.row)?.id
                        state.programs.programsByChannel[channelId]?.getOrNull(it.column)
                    }
                }
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = modifier,
            ) {
                if (state.channels.isEmpty()) {
                    ErrorMessage("Live TV is enabled, but no channels were found.", null)
                } else {
                    AnimatedVisibility(
                        visible = tvPrefs.showHeader,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        TvGuideHeader(
                            program = focusedProgram,
                            modifier =
                                Modifier
                                    .padding(
                                        top = HeaderUtils.topPadding,
                                        bottom = 0.dp,
                                        start = HeaderUtils.startPadding,
                                    ).fillMaxHeight(.30f),
                        )
                    }
                    AnimatedVisibility(
                        focusedPosition.row < 1,
                        enter = expandVertically(),
                        exit = shrinkVertically(),
                    ) {
                        ExpandableFaButton(
                            title = R.string.view_options,
                            iconStringRes = R.string.fa_sliders,
                            onClick = { showViewOptions = true },
                            modifier =
                                Modifier
                                    .padding(start = tvGuideDimensions.channelWidth)
                                    .focusRequester(buttonFocusRequester),
                        )
                    }
                    TvGuideGridContent(
                        preferences = tvPrefs,
                        refreshing = state.refreshing,
                        channels = state.channels,
                        programs = state.programs,
                        channelProgramCount = state.channelProgramCount,
                        guideTimes = state.guideTimes,
                        onClickChannel = { index, channel ->
                            showChannelDialog = index to channel
                        },
                        onFocus = {
                            focusedPosition = it
                            onRowPosition.invoke(it.row)
                            viewModel.onFocusChannel(it)
                        },
                        onClickProgram = { index, program ->
                            if (program.isFake) {
                                val now = LocalDateTime.now()
                                if (now.isAfter(program.start) && now.isBefore(program.end)) {
                                    viewModel.navigationManager.navigateTo(
                                        Destination.Playback(
                                            itemId = program.channelId,
                                            positionMs = 0L,
                                        ),
                                    )
                                } else {
                                    Toast
                                        .makeText(
                                            context,
                                            "No guide data found!",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            } else {
                                viewModel.fetchProgramForDialog(program.id)
                                showItemDialog = index
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surface)
                                .focusRequester(focusRequester),
                    )
                }
            }
            if (showItemDialog != null) {
                val onDismissRequest = { showItemDialog = null }
                ProgramDialog(
                    state = programDialogState,
                    canRecord = true,
                    onDismissRequest = onDismissRequest,
                    onWatch = {
                        onDismissRequest.invoke()
                        it.data.channelId?.let {
                            viewModel.navigationManager.navigateTo(
                                Destination.Playback(
                                    itemId = it,
                                    positionMs = 0L,
                                ),
                            )
                        }
                    },
                    onRecord = { program, series ->
                        viewModel.record(
                            programId = program.id,
                            series = series,
                        )
                        onDismissRequest.invoke()
                    },
                    onCancelRecord = { program, series ->
                        viewModel.cancelRecording(
                            series = series,
                            timerId = if (series) program.data.seriesTimerId else program.data.timerId,
                        )
                        onDismissRequest.invoke()
                    },
                )
            }
        }
    }
    if (showViewOptions) {
        LiveTvViewOptionsDialog(
            preferences = preferences,
            onDismissRequest = { showViewOptions = false },
            onViewOptionsChange = { newPrefs ->
                scope.launchIO {
                    viewModel.preferences.updateData {
                        newPrefs
                    }
                }
            },
        )
    }
    showChannelDialog?.let { (index, channel) ->
        val watchLiveStr = stringResource(R.string.watch_live)
        val dialogItems =
            remember {
                listOf(
                    DialogItem(
                        watchLiveStr,
                        Icons.Default.PlayArrow,
                        iconColor = Color.Green.copy(alpha = .8f),
                        dismissOnClick = true,
                    ) {
                        viewModel.navigationManager.navigateTo(
                            Destination.Playback(
                                itemId = channel.id,
                                positionMs = 0L,
                            ),
                        )
                    },
                    DialogItem(
                        text = if (channel.favorite) R.string.remove_favorite else R.string.add_favorite,
                        iconStringRes = R.string.fa_heart,
                        iconColor = if (channel.favorite) Color.Red else Color.Unspecified,
                        dismissOnClick = true,
                    ) {
                        viewModel.toggleFavorite(index, channel)
                    },
                )
            }
        DialogPopup(
            onDismissRequest = { showChannelDialog = null },
            params = DialogParams(false, channel.name ?: "", dialogItems),
            elevation = 3.dp,
        )
    }
}

val tvGuideDimensions =
    ProgramGuideDimensions(
        timelineHourWidth = 240.dp,
        timelineHeight = 32.dp,
        channelWidth = 120.dp,
        channelHeight = 64.dp,
        currentTimeWidth = 2.dp,
    )

@Composable
fun TvGuideGridContent(
    preferences: LiveTvPreferences,
    refreshing: Boolean,
    channels: List<TvChannel>,
    programs: FetchedPrograms,
    channelProgramCount: Map<UUID, Int>,
    guideTimes: List<LocalDateTime>,
    onClickChannel: (Int, TvChannel) -> Unit,
    onClickProgram: (Int, TvProgram) -> Unit,
    onFocus: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state = rememberSaveableProgramGuideState()
    val scope = rememberCoroutineScope()
    val guideStart = remember(guideTimes) { guideTimes.first() }

    var focusedItem by rememberPosition(RowColumn(0, 0))
    var focusedProgramIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(onFocus, focusedProgramIndex) {
        withContext(Dispatchers.Default) {
            val program = programs.programs.getOrNull(focusedProgramIndex)
            if (program != null) {
                val channelIndex = channels.indexOfFirst { it.id == program.channelId }
                val programsBefore =
                    (0..<channelIndex)
                        .mapNotNull {
                            val channel = channels[it]
                            channelProgramCount[channel.id]
                        }.sum()
                val programIndex = focusedProgramIndex - programsBefore
                focusedItem = RowColumn(channelIndex, programIndex)
                onFocus.invoke(focusedItem)
            }
        }
    }
    val programFocusRequester = remember { FocusRequester() }

    BackHandler(focusedItem.row > 0) {
        scope.launch {
            focusedProgramIndex = 0
            state.animateToProgram(0, Alignment.Center)
            programFocusRequester.tryRequestFocus()
        }
    }

    BackHandler(focusedItem.column > 0) {
        scope.launch {
            val programIndex =
                (0..<focusedItem.row)
                    .mapNotNull {
                        val channel = channels[it]
                        channelProgramCount[channel.id]
                    }.sum()
            focusedProgramIndex = programIndex
            state.animateToProgram(programIndex, Alignment.Center)
            programFocusRequester.tryRequestFocus()
        }
    }

    Box(modifier = modifier) {
        ProgramGuide(
            state = state,
            dimensions = tvGuideDimensions,
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusProperties {
                        onEnter = { programFocusRequester.tryRequestFocus() }
                    },
        ) {
            guideStartHour = 0f
            currentTime(
                layoutInfo = {
                    ProgramGuideItem.CurrentTime(
                        hoursBetween(guideStart, LocalDateTime.now()),
                    )
                },
            ) {
                Surface(
                    colors =
                        SurfaceDefaults.colors(
                            MaterialTheme.colorScheme.tertiary.copy(
                                alpha = .25f,
                            ),
                        ),
                    modifier = Modifier,
                ) {
                    // Empty
                }
            }
            timeline(
                count = guideTimes.size,
                layoutInfo = { index ->
                    val start = guideTimes[index]
                    val end =
                        if (index < guideTimes.lastIndex) {
                            guideTimes[index + 1]
                        } else {
                            start.plusHours(1)
                        }
                    ProgramGuideItem.Timeline(
                        startHour = hoursBetween(guideStart, start),
                        endHour = hoursBetween(guideStart, end),
                    )
                },
            ) { index ->
                Box(
                    modifier =
                        Modifier
                            // Intentionally set background twice
                            // The second is padded so there are gaps between times
                            // The first covers those gaps
                            .background(MaterialTheme.colorScheme.background)
                            .padding(horizontal = 2.dp)
                            .fillMaxSize()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(4.dp),
                            ),
                ) {
                    val guideTime = guideTimes[index]
                    val differentDay = guideTime.toLocalDate() != guideStart.toLocalDate()
                    val time =
                        DateUtils.formatDateTime(
                            context,
                            guideTime
                                .toInstant(OffsetDateTime.now().offset)
                                .epochSecond * 1000,
                            DateUtils.FORMAT_SHOW_TIME or if (differentDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0,
                        )
                    Text(
                        text = time.toString(),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(2.dp),
                    )
                }
            }
            programs(
                count = programs.programs.size,
                layoutInfo = { programIndex ->
                    val program = programs.programs[programIndex]
                    val channelIndex = channels.indexOfFirst { it.id == program.channelId }
                    // Using the duration for endHour accounts for daylight savings switch
                    // Eg a 1:30am-1:00am show
                    val duration = abs(program.endHours - program.startHours)
                    ProgramGuideItem.Program(
                        channelIndex,
                        program.startHours,
                        program.startHours + duration,
                    )
                },
            ) { programIndex ->
                val program = programs.programs[programIndex]
                Program(
                    guideStart = guideStart,
                    program = program,
                    colorCode = preferences.colorCodePrograms,
                    onClick = {
                        onClickProgram.invoke(programIndex, program)
                    },
                    onLongClick = {},
                    modifier =
                        Modifier
                            .ifElse(
                                programIndex == focusedProgramIndex,
                                Modifier.focusRequester(programFocusRequester),
                            ).onFocusChanged {
                                if (it.isFocused) {
                                    focusedProgramIndex = programIndex
                                    scope.launch {
                                        try {
                                            state.animateToProgram(
                                                programIndex,
                                                Alignment.Center,
                                            )
                                        } catch (ex: Exception) {
                                            Timber.e(ex, "Couldn't scroll to $programIndex")
                                        }
                                    }
                                }
                            },
                )
            }

            channels(
                count = channels.size,
                layoutInfo = { channelIndex ->
                    ProgramGuideItem.Channel(channelIndex)
                },
            ) { channelIndex ->
                val channel = channels[channelIndex]
                Channel(
                    channel = channel,
                    channelIndex = channelIndex,
                    onClick = { onClickChannel.invoke(channelIndex, channel) },
                    onLongClick = {},
                    modifier =
                        Modifier
                            .onFocusChanged {
                                if (it.isFocused) {
                                    scope.launch {
                                        try {
                                            state.animateToChannel(
                                                channelIndex,
                                                Alignment.CenterVertically,
                                            )
                                        } catch (ex: Exception) {
                                            Timber.e(ex, "Couldn't scroll to $channelIndex")
                                        }
                                    }
                                    focusedItem = RowColumn(channelIndex, 0)
                                    onFocus.invoke(focusedItem)
                                }
                            },
                )
            }

            topCorner {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                )
            }
        }
        if (refreshing) {
            CircularProgress(
                Modifier
                    .background(
                        MaterialTheme.colorScheme.background.copy(alpha = .5f),
                        shape = CircleShape,
                    ).size(64.dp)
                    .padding(16.dp)
                    .align(Alignment.BottomEnd),
            )
        }
    }
}
