package com.github.damontecres.wholphin.ui.detail.livetv

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import eu.wewox.programguide.ProgramGuide
import eu.wewox.programguide.ProgramGuideDimensions
import eu.wewox.programguide.ProgramGuideItem
import eu.wewox.programguide.rememberSaveableProgramGuideState
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.abs

@Composable
fun TvGuideGrid(
    requestFocusAfterLoading: Boolean,
    onRowPosition: (Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    var firstLoad by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        viewModel.init(firstLoad)
        firstLoad = false
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)
    val channels by viewModel.channels.observeAsState(listOf())
    val programs by viewModel.programs.observeAsState(FetchedPrograms(0..0, listOf(), mapOf()))
//    val programsByChannel by viewModel.programsByChannel.observeAsState(mapOf())
//    val fetchedRange by viewModel.fetchedRange.observeAsState(0..0)
    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state, modifier)
        LoadingState.Pending,
        -> LoadingPage(modifier)

        LoadingState.Loading,
        LoadingState.Success,
        -> {
            val context = LocalContext.current
            val fetchedItem by viewModel.fetchedItem.observeAsState(null)
            val loadingItem by viewModel.fetchingItem.observeAsState(LoadingState.Pending)
            var showItemDialog by remember { mutableStateOf<Int?>(null) }
            val focusRequester = remember { FocusRequester() }
            if (requestFocusAfterLoading) {
                LaunchedEffect(Unit) {
                    focusRequester.tryRequestFocus()
                }
            }
            Column(modifier = modifier) {
                if (channels.isEmpty()) {
                    ErrorMessage("Live TV is enabled, but no channels were found.", null)
                } else {
                    TvGuideGridContent(
                        loading = state is LoadingState.Loading,
                        channels = channels,
                        programs = programs,
                        channelProgramCount = viewModel.channelProgramCount,
                        start = viewModel.guideStart,
                        onClickChannel = { index, channel ->
                            viewModel.navigationManager.navigateTo(
                                Destination.Playback(
                                    itemId = channel.id,
                                    positionMs = 0L,
                                ),
                            )
                        },
                        onFocus = {
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
                                viewModel.getItem(program.id)
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
                    item = fetchedItem,
                    canRecord = true,
                    loading = loadingItem,
                    onDismissRequest = onDismissRequest,
                    onWatch = {
                        onDismissRequest.invoke()
                        fetchedItem?.data?.channelId?.let {
                            viewModel.navigationManager.navigateTo(
                                Destination.Playback(
                                    itemId = it,
                                    positionMs = 0L,
                                ),
                            )
                        }
                    },
                    onRecord = { series ->
                        fetchedItem?.let {
                            viewModel.record(
                                programId = it.id,
                                series = series,
                            )
                        }
                        onDismissRequest.invoke()
                    },
                    onCancelRecord = { series ->
                        fetchedItem?.data?.let {
                            viewModel.cancelRecording(
                                series = series,
                                timerId = if (series) it.seriesTimerId else it.timerId,
                            )
                        }
                        onDismissRequest.invoke()
                    },
                )
            }
        }
    }
}

@Composable
fun TvGuideGridContent(
    loading: Boolean,
    channels: List<TvChannel>,
    programs: FetchedPrograms,
    channelProgramCount: Map<UUID, Int>,
    start: LocalDateTime,
    onClickChannel: (Int, TvChannel) -> Unit,
    onClickProgram: (Int, TvProgram) -> Unit,
    onFocus: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val state = rememberSaveableProgramGuideState()
    val scope = rememberCoroutineScope()

    var focusedItem by rememberPosition(RowColumn(0, 0))
    val focusedChannelIndex = focusedItem.row
    val focusedProgramIndex =
        remember(programs.range, focusedItem) {
            focusedItem.let { focus ->
                (programs.range.first..<focus.row).sumOf {
                    val channelId = channels[it].id
                    channelProgramCount[channelId] ?: 0
                } + focus.column
            }
        }

    LaunchedEffect(focusedProgramIndex) {
//        Timber.v("Focusing on $focusedItem, programIndex=$focusedProgramIndex")
        scope.launch(ExceptionHandler()) {
            state.animateToProgram(focusedProgramIndex, Alignment.Center)
        }
    }
    val dimensions =
        ProgramGuideDimensions(
            timelineHourWidth = 240.dp,
            timelineHeight = 32.dp,
            channelWidth = 120.dp,
            channelHeight = 64.dp,
            currentTimeWidth = 2.dp,
        )
    var gridHasFocus by rememberSaveable { mutableStateOf(false) }
    var channelColumnFocused by rememberSaveable { mutableStateOf(false) }
    Box(modifier = modifier) {
        ProgramGuide(
            state = state,
            dimensions = dimensions,
            modifier =
                Modifier
                    .fillMaxSize()
                    .onFocusChanged {
                        gridHasFocus = it.hasFocus
                    }.focusable()
                    .onPreviewKeyEvent {
                        if (it.type == KeyEventType.KeyUp) {
                            return@onPreviewKeyEvent false
                        }
                        val item = focusedItem
                        val newFocusedItem =
                            when (it.key) {
                                Key.Back -> {
                                    if (item.column > 0) {
                                        // Not at beginning of row, so move to beginning
                                        item.copy(column = 0)
                                    } else if (item.row > 0) {
                                        item.copy(row = 0)
                                    } else {
                                        // At beginning, so allow normal back button behavior
                                        return@onPreviewKeyEvent false
                                    }
                                }

                                Key.DirectionRight -> {
                                    if (channelColumnFocused) {
                                        channelColumnFocused = false
                                        item.copy(column = 0)
                                    } else {
                                        item.copy(column = item.column + 1)
                                    }
                                }

                                Key.DirectionLeft -> {
                                    if (channelColumnFocused) {
                                        focusManager.moveFocus(FocusDirection.Left)
                                        null
                                    } else if (item.column == 0) {
                                        channelColumnFocused = true
                                        item
                                    } else {
                                        item.copy(column = item.column - 1)
                                    }
                                }

                                Key.DirectionUp -> {
                                    if (item.row <= 0) {
                                        focusManager.moveFocus(FocusDirection.Up)
                                        null
                                    } else {
                                        val newChannelIndex = item.row - 1
                                        if (channelColumnFocused) {
                                            RowColumn(newChannelIndex, 0)
                                        } else {
                                            val currentChannel = channels[item.row].id
                                            val currentProgram =
                                                programs.programsByChannel[currentChannel]?.get(item.column)
                                            val newChannelId = channels[newChannelIndex].id
                                            val newChannelPrograms =
                                                programs.programsByChannel[newChannelId]
                                            if (newChannelPrograms == null) {
                                                return@onPreviewKeyEvent true
                                            }
                                            if (currentProgram == null) {
                                                item
                                            } else {
                                                val start = currentProgram.startHours
                                                val pIndex =
                                                    newChannelPrograms.indexOfFirst { start in (it.startHours..<it.endHours) }
                                                if (pIndex >= 0) {
                                                    RowColumn(newChannelIndex, pIndex)
                                                } else {
                                                    val pIndex =
                                                        newChannelPrograms.indexOfFirst { it.startHours >= start }
                                                    if (pIndex >= 0) {
                                                        RowColumn(newChannelIndex, pIndex)
                                                    } else {
                                                        RowColumn(newChannelIndex, 0)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Key.DirectionDown -> {
                                    // Move channel focus down
                                    val newChannelIndex = item.row + 1
                                    if (newChannelIndex >= channels.size) {
                                        // If trying to move below the final channel, then move focus out of the grid
                                        focusManager.moveFocus(FocusDirection.Down)
                                        null
                                    } else {
                                        // Otherwise, moving to a new row
                                        // Get the new row/channel's programs
                                        val newChannelId = channels[newChannelIndex].id
                                        val newChannelPrograms =
                                            programs.programsByChannel[newChannelId]
                                        if (newChannelPrograms == null) {
                                            // This means there is no data for the new channel and it is loading in the background
                                            return@onPreviewKeyEvent true
                                        }
                                        if (channelColumnFocused) {
                                            // If focused on the channel column, move down a channel
                                            RowColumn(newChannelIndex, 0)
                                        } else {
                                            // Get current program & its start time
                                            val currentChannel = channels[item.row].id
                                            val currentProgram =
                                                programs.programsByChannel[currentChannel]?.get(item.column)
                                            if (currentProgram == null) {
                                                // Data is loading in the background
                                                item
                                            } else {
                                                val start = currentProgram.startHours
                                                // Find the first program where the start time (of the previously focused program) is in the middle of a program
                                                val pIndex =
                                                    newChannelPrograms.indexOfFirst { start in (it.startHours..<it.endHours) }
                                                if (pIndex >= 0) {
                                                    // Found one, so focus on it
                                                    RowColumn(newChannelIndex, pIndex)
                                                } else {
                                                    // Didn't find one, probably due to missing data
                                                    // So now first the first one that starts after the previously focused program
                                                    val pIndex =
                                                        newChannelPrograms.indexOfFirst { it.startHours >= start }
                                                    if (pIndex >= 0) {
                                                        // Found one, so focus on it
                                                        RowColumn(newChannelIndex, pIndex)
                                                    } else {
                                                        // Did not find one, so focus on the final program in the list
                                                        RowColumn(
                                                            newChannelIndex,
                                                            newChannelPrograms.size - 1,
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                    if (channelColumnFocused) {
                                        val channel = channels[focusedChannelIndex]
                                        Timber.v("Clicked on %s", channel)
                                        onClickChannel.invoke(focusedChannelIndex, channel)
                                    } else {
                                        val currentChannel = channels[item.row].id
                                        val currentProgram =
                                            programs.programsByChannel[currentChannel]?.get(item.column)
                                        if (currentProgram == null) {
                                            // Data is loading in the background
                                            return@onPreviewKeyEvent true
                                        }
                                        Timber.v("Clicked on %s", currentProgram)
                                        onClickProgram.invoke(
                                            focusedProgramIndex,
                                            currentProgram,
                                        )
                                    }
                                    null
                                }

                                else -> {
                                    null
                                }
                            }

                        if (newFocusedItem != null) {
                            val channel = channels[newFocusedItem.row]
                            val programs = programs.programsByChannel[channel.id].orEmpty()
                            // Ensure it isn't going out of range
                            val toFocus =
                                newFocusedItem
                                    .copy(
                                        row = newFocusedItem.row.coerceIn(0, channels.size - 1),
                                        column =
                                            newFocusedItem.column.coerceIn(
                                                0,
                                                (programs.size - 1).coerceAtLeast(0),
                                            ),
                                    )
                            focusedItem = toFocus
                            onFocus(toFocus)
                            return@onPreviewKeyEvent true
                        }
                        return@onPreviewKeyEvent false
                    },
        ) {
            guideStartHour = 0f
            currentTime(
                layoutInfo = {
                    ProgramGuideItem.CurrentTime(
                        hoursBetween(start, LocalDateTime.now()),
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
                count = MAX_HOURS.toInt(),
                layoutInfo = { index ->
                    ProgramGuideItem.Timeline(
                        startHour = index.toFloat(),
                        endHour = index + 1f,
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
                    val differentDay =
                        start.toLocalDate() !=
                            start
                                .plusHours(index.toLong())
                                .toLocalDate()
                    val time =
                        DateUtils.formatDateTime(
                            context,
                            start
                                .plusHours(index.toLong())
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
                val focused =
                    gridHasFocus && !channelColumnFocused && programIndex == focusedProgramIndex
                Program(program, focused, Modifier)
            }

            channels(
                count = channels.size,
                layoutInfo = { channelIndex ->
                    ProgramGuideItem.Channel(channelIndex)
                },
            ) { channelIndex ->
                val channel = channels[channelIndex]
                val focused =
                    gridHasFocus && channelColumnFocused && focusedChannelIndex == channelIndex
                Channel(
                    channel = channel,
                    channelIndex = channelIndex,
                    focused = focused,
                    modifier = Modifier,
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
        if (loading) {
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
