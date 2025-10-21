package com.github.damontecres.wholphin.ui.detail.livetv

import android.text.format.DateUtils
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.tv.material3.contentColorFor
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import eu.wewox.programguide.ProgramGuide
import eu.wewox.programguide.ProgramGuideDimensions
import eu.wewox.programguide.ProgramGuideItem
import eu.wewox.programguide.rememberSaveableProgramGuideState
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

@Composable
fun TvGuideGrid(
    requestFocusAfterLoading: Boolean,
    modifier: Modifier = Modifier,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.init()
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)
    val channels by viewModel.channels.observeAsState(listOf())
    val programs by viewModel.programs.observeAsState(listOf())
    val programsByChannel by viewModel.programsByChannel.observeAsState(mapOf())
    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state, modifier)

        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage(modifier)

        LoadingState.Success -> {
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
                TvGuideGrid(
                    channels = channels,
                    programList = programs,
                    programs = programsByChannel,
                    start = viewModel.start,
                    onClick = { index, program ->
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
                                    .makeText(context, "No guide data found!", Toast.LENGTH_SHORT)
                                    .show()
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
            if (showItemDialog != null) {
                val onDismissRequest = { showItemDialog = null }
                ProgramDialog(
                    item = fetchedItem,
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
                                programIndex = showItemDialog!!,
                                programId = it.id,
                                series = series,
                            )
                        }
                        onDismissRequest.invoke()
                    },
                    onCancelRecord = { series ->
                        fetchedItem?.data?.let {
                            viewModel.cancelRecording(
                                programIndex = showItemDialog!!,
                                programId = it.id,
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
fun TvGuideGrid(
    channels: List<TvChannel>,
    programList: List<TvProgram>,
    programs: Map<UUID, List<TvProgram>>,
    start: LocalDateTime,
    onClick: (Int, TvProgram) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val state = rememberSaveableProgramGuideState()
    val scope = rememberCoroutineScope()
    var focusedProgramIndex by rememberInt(0)
    var focusedChannelIndex by rememberInt(0)

    val dimensions =
        ProgramGuideDimensions(
            timelineHourWidth = 240.dp,
            timelineHeight = 32.dp,
            channelWidth = 120.dp,
            channelHeight = 80.dp,
            currentTimeWidth = 2.dp,
        )

    val programsBeforeChannel =
        remember {
            CacheBuilder
                .newBuilder()
                .maximumSize(200)
                .build<Int, Int>(
                    object : CacheLoader<Int, Int>() {
                        override fun load(key: Int): Int =
                            channels
                                .subList(0, key)
                                .map { it.id }
                                .sumOf { programs[it]?.size ?: 0 }
                    },
                )
        }

    var gridHasFocus by remember { mutableStateOf(false) }

    ProgramGuide(
        state = state,
        dimensions = dimensions,
        modifier =
            modifier
                .onFocusChanged {
                    gridHasFocus = it.hasFocus
                }.focusable()
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyUp) {
                        return@onPreviewKeyEvent false
                    }
                    val newIndex =
                        when (it.key) {
                            Key.Back -> {
                                val pos = programsBeforeChannel.get(focusedChannelIndex)
                                if (focusedProgramIndex - pos > 0) {
                                    // Not at beginning of row, so move to beginning
                                    pos
                                } else {
                                    // At beginning, so allow normal back button behavior
                                    return@onPreviewKeyEvent false
                                }
                            }

                            Key.DirectionRight -> {
                                val nextProgramIndex = focusedProgramIndex + 1
                                val programsBefore = programsBeforeChannel.get(focusedChannelIndex)
                                val relativePosition = nextProgramIndex - programsBefore
                                val channelPrograms =
                                    programs[channels[focusedChannelIndex].id].orEmpty()
                                if (relativePosition >= channelPrograms.size) {
                                    focusManager.moveFocus(FocusDirection.Right)
                                    null
                                } else {
                                    nextProgramIndex
                                }
                            }

                            Key.DirectionLeft -> {
                                val nextProgramIndex = focusedProgramIndex - 1
                                if (nextProgramIndex >= 0) {
                                    val programsBefore =
                                        programsBeforeChannel.get(focusedChannelIndex)
                                    val relativePosition = nextProgramIndex - programsBefore
//                                val channelPrograms =
//                                    programs[channels[focusedChannel].id].orEmpty()
                                    if (relativePosition < 0) {
                                        focusManager.moveFocus(FocusDirection.Left)
                                        null
                                    } else {
                                        nextProgramIndex
                                    }
                                } else {
                                    focusManager.moveFocus(FocusDirection.Left)
                                    null
                                }
                            }

                            Key.DirectionUp -> {
                                val start = programList[focusedProgramIndex].startHours
                                val newFocusedChannelIndex = (focusedChannelIndex - 1)
                                if (newFocusedChannelIndex < 0) {
                                    focusManager.moveFocus(FocusDirection.Up)
                                    null
                                } else {
                                    focusedChannelIndex = newFocusedChannelIndex.coerceAtLeast(0)
                                    val channelId = channels[focusedChannelIndex].id
                                    val pro = programs[channelId].orEmpty()
                                    val pIndex =
                                        pro.indexOfFirst { start in (it.startHours..<it.endHours) }
                                    if (pIndex >= 0) {
                                        programsBeforeChannel.get(focusedChannelIndex) + pIndex
                                    } else {
                                        val pIndex = pro.indexOfFirst { it.startHours >= start }
                                        if (pIndex >= 0) {
                                            programsBeforeChannel.get(focusedChannelIndex) + pIndex
                                        } else {
                                            programsBeforeChannel.get(focusedChannelIndex) + programs[channelId]!!.size
                                        }
                                    }
                                }
                            }

                            Key.DirectionDown -> {
                                // When the currently focused program starts
                                val start = programList[focusedProgramIndex].startHours
                                // Move channel focus down
                                val newFocusedChannelIndex = (focusedChannelIndex + 1)
                                if (newFocusedChannelIndex >= channels.size) {
                                    // If trying to move below the final channel, then move focus out of the grid
                                    focusManager.moveFocus(FocusDirection.Down)
                                    null
                                } else {
                                    // Otherwise, moving to a new row
                                    focusedChannelIndex =
                                        newFocusedChannelIndex.coerceAtMost(channels.size - 1)
                                    // Get the new row/channel's programs
                                    val channelId = channels[focusedChannelIndex].id
                                    val pro = programs[channelId].orEmpty()
                                    // Find the first program where the start time (of the previously focused program) is in the middle of a program
                                    val pIndex =
                                        pro.indexOfFirst { start in (it.startHours..<it.endHours) }
                                    if (pIndex >= 0) {
                                        // Found one, so focus on it
                                        // Get the sum of all of the previous channels' program sizes, plus the index found to convert a relative program index into absolute
                                        programsBeforeChannel.get(focusedChannelIndex) + pIndex
                                    } else {
                                        // Didn't find one, probably due to missing data
                                        // So now first the first one that starts after the previously focused program
                                        val pIndex = pro.indexOfFirst { it.startHours >= start }
                                        if (pIndex >= 0) {
                                            // Found one, so focus on it
                                            programsBeforeChannel.get(focusedChannelIndex) + pIndex
                                        } else {
                                            // Did not find one, so focus on the final program in the list
                                            programsBeforeChannel.get(focusedChannelIndex) + programs[channelId]!!.size
                                        }
                                    }
                                }
                            }

                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                val program = programList[focusedProgramIndex]
                                Timber.v("Clicked on %s", program)
                                onClick.invoke(focusedProgramIndex, program)
                                null
                            }

                            else -> {
                                null
                            }
                        }
                    if (newIndex != null) {
                        val before = programsBeforeChannel.get(focusedChannelIndex)
                        val max =
                            before + channels[focusedChannelIndex].let { programs[it.id]!!.size } - 1
                        val index = newIndex.coerceIn(before, max)
                        scope.launch(ExceptionHandler()) {
                            focusedProgramIndex = index
                            state.animateToProgram(index, Alignment.Center)
                        }
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
                colors = SurfaceDefaults.colors(MaterialTheme.colorScheme.tertiary.copy(alpha = .25f)),
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
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
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
                    modifier = Modifier.background(MaterialTheme.colorScheme.background),
                )
            }
        }

        programs(
            count = programList.size,
            layoutInfo = { programIndex ->
                val program = programList[programIndex]
                val channelIndex = channels.indexOfFirst { it.id == program.channelId }
                ProgramGuideItem.Program(channelIndex, program.startHours, program.endHours)
            },
        ) { programIndex ->
            val program = programList[programIndex]
            val focused = gridHasFocus && programIndex == focusedProgramIndex
            val background =
                if (focused) {
                    MaterialTheme.colorScheme.inverseSurface
                } else {
                    MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                }
            val textColor = MaterialTheme.colorScheme.contentColorFor(background)
            Box(
                modifier =
                    Modifier
//                        .scale(if (focused) 1.1f else 1f)
                        .padding(2.dp)
                        .fillMaxSize()
                        .background(
                            background,
                            shape = RoundedCornerShape(4.dp),
                        ),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                ) {
                    Text(
                        text = program.name ?: program.id.toString(),
                        color = textColor,
                        maxLines = 1,
                        modifier = Modifier.enableMarquee(focused),
                    )
                    listOfNotNull(
                        program.seasonEpisode?.let { "S${it.season} E${it.episode}" },
                        program.subtitle,
                    ).joinToString(" - ")
                        .ifBlank { null }
                        ?.let {
                            Text(
                                text = it,
                                color = textColor,
                                modifier = Modifier,
                            )
                        }
                }
                RecordingMarker(
                    isRecording = program.isRecording,
                    isSeriesRecording = program.isSeriesRecording,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        channels(
            count = channels.size,
            layoutInfo = { channelIndex ->
                ProgramGuideItem.Channel(channelIndex)
            },
        ) { channelIndex ->
            val channel = channels[channelIndex]
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(4.dp),
                        ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .padding(4.dp)
                            .fillMaxSize(),
                ) {
                    Text(
                        text = channel.number ?: channel.name ?: channelIndex.toString(),
                        modifier = Modifier,
                    )
                    AsyncImage(
                        model = channel.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxHeight(.66f),
                    )
                }
            }
        }
    }
}
