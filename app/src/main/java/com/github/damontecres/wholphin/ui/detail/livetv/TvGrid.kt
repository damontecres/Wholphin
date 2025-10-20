package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
import java.util.UUID

@Composable
fun TvGrid(
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
        is LoadingState.Error -> ErrorMessage(state)

        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()

        LoadingState.Success ->
            Column(modifier = modifier) {
                TvGrid(
                    channels = channels,
                    programList = programs,
                    programs = programsByChannel,
                    start = viewModel.start,
                    onClick = { program ->
                        viewModel.navigationManager.navigateTo(
                            Destination.Playback(
                                itemId = program.channelId,
                                positionMs = 0L,
                            ),
                        )
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface),
                )
            }
    }
}

@Composable
fun TvGrid(
    channels: List<TvChannel>,
    programList: List<TvProgram>,
    programs: Map<UUID, List<TvProgram>>,
    start: LocalDateTime,
    onClick: (TvProgram) -> Unit,
    modifier: Modifier = Modifier,
) {
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

//    fun programsBeforeChannel(channelIndex: Int): Int = channels.subList(0, channelIndex).map { it.id }.sumOf { programs[it]?.size ?: 0 }

    ProgramGuide(
        state = state,
        dimensions = dimensions,
        modifier =
            modifier
                .focusable()
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyUp) {
                        return@onPreviewKeyEvent false
                    }
                    val newIndex =
                        when (it.key) {
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
                                focusedChannelIndex = (focusedChannelIndex - 1)
                                if (focusedChannelIndex < 0) {
                                    focusManager.moveFocus(FocusDirection.Up)
                                    null
                                } else {
                                    val channelId = channels[focusedChannelIndex].id
                                    val pIndex =
                                        programs[channelId]?.indexOfFirst { start in (it.startHours..<it.endHours) }
                                            ?: -1
                                    if (pIndex >= 0) {
                                        programsBeforeChannel.get(focusedChannelIndex) + pIndex
                                    } else {
                                        programsBeforeChannel.get(focusedChannelIndex) + programs[channelId]!!.size
                                    }
                                }
                            }

                            Key.DirectionDown -> {
                                val start = programList[focusedProgramIndex].startHours
                                focusedChannelIndex =
                                    (focusedChannelIndex + 1).coerceAtMost(channels.size - 1)
                                val channelId = channels[focusedChannelIndex].id
                                val pro = programs[channelId].orEmpty()
                                val pIndex =
                                    pro.indexOfFirst { start in (it.startHours..<it.endHours) }
                                if (pIndex >= 0) {
                                    programsBeforeChannel.get(focusedChannelIndex) + pIndex
                                } else {
                                    programsBeforeChannel.get(focusedChannelIndex) + programs[channelId]!!.size
                                }
                            }

                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                                val program = programList[focusedProgramIndex]
                                Timber.v("Clicked on %s", program)
                                onClick.invoke(program)
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
                colors = SurfaceDefaults.colors(MaterialTheme.colorScheme.tertiary),
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
            Box(modifier = Modifier.fillMaxSize()) {
                val time = start.plusHours(index.toLong()).toLocalTime()
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
            val focused = programIndex == focusedProgramIndex
            val background =
                if (focused) {
                    MaterialTheme.colorScheme.inverseSurface
                } else {
                    MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
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
                if (program.isSeriesRecording) {
                    Box(
                        modifier =
                            Modifier
                                .padding(4.dp)
                                .size(16.dp)
                                .background(Color.Red.copy(alpha = .5f), shape = CircleShape)
                                .align(Alignment.BottomEnd),
                    )
                    Box(
                        modifier =
                            Modifier
                                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp, end = 10.dp)
                                .size(16.dp)
                                .background(Color.Red, shape = CircleShape)
                                .align(Alignment.BottomEnd),
                    )
                } else if (program.isRecording) {
                    Box(
                        modifier =
                            Modifier
                                .padding(4.dp)
                                .size(16.dp)
                                .background(Color.Red, shape = CircleShape)
                                .align(Alignment.BottomEnd),
                    )
                }
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
                            MaterialTheme.colorScheme.background,
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
