package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.util.LoadingState
import eu.wewox.programguide.ProgramGuide
import eu.wewox.programguide.ProgramGuideItem
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

private val hourWidth = 200.dp

data class Program(
    val channel: Int,
    val start: Float,
    val end: Float,
    val title: String,
)

/**
 * Formats time.
 */
fun formatTime(
    from: Float,
    to: Float? = null,
): String {
    fun Float.hour(): String = toInt().formatWithPrefix(2)

    fun Float.minutes(): String = ((this % 1) * 60).toInt().formatWithPrefix(2)

    val fromFormatted = "${from.hour()}:${from.minutes()}"
    return if (to != null) {
        val toFormatted = "${to.hour()}:${to.minutes()}"
        "$fromFormatted - $toFormatted"
    } else {
        fromFormatted
    }
}

/**
 * Creates a list of programs to view in program guide.
 */
fun createPrograms(
    channels: Int = CHANNELS_COUNT,
    timeline: IntRange = 0..HOURS_COUNT,
): List<Program> {
    var channel = 0
    var hour = timeline.first + HOURS.random()
    return buildList {
        while (channel < channels) {
            while (true) {
                val end = hour + HOURS.random()
                if (end > timeline.last) {
                    break
                }

                add(Program(channel, hour, end, "Program #$size"))
                hour = end
            }
            hour = timeline.first + HOURS.random()
            channel += 1
        }
    }
}

private fun Int.formatWithPrefix(
    length: Int,
    prefix: Char = '0',
): String {
    val number = toString()
    val prefixLength = (length - number.length).coerceAtLeast(0)
    val prefixFull = List(prefixLength) { prefix }.joinToString(separator = "")
    return "$prefixFull$number"
}

private val HOURS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f, 2.25f, 2.5f)

private const val CHANNELS_COUNT = 30
private const val HOURS_COUNT = 24

@Composable
fun TvGrid(modifier: Modifier = Modifier) {
    val channels = 20
    val timeline = 8..22
    val programs = remember { createPrograms(channels, timeline) }
    ProgramGuide(
        modifier =
        modifier,
    ) {
        guideStartHour = timeline.first.toFloat()

        programs(
            items = programs,
            layoutInfo = {
                ProgramGuideItem.Program(
                    channelIndex = it.channel,
                    startHour = it.start,
                    endHour = it.end,
                )
            },
            itemContent = {
                ListItem(
                    selected = false,
                    onClick = {},
                    headlineContent = {
                        Text(
                            text = it.title,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier,
                        )
                    },
                    modifier = Modifier,
                )
            },
        )

        channels(
            count = channels,
            layoutInfo = {
                ProgramGuideItem.Channel(
                    index = it,
                )
            },
            itemContent = {
                Text(
                    text = it.toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
        )

        timeline(
            count = timeline.count(),
            layoutInfo = {
                val start = timeline.toList()[it].toFloat()
                ProgramGuideItem.Timeline(
                    startHour = start,
                    endHour = start + 1f,
                )
            },
            itemContent = {
                Text(
                    text = "${timeline.toList()[it].toFloat()} o'clock",
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
        )
    }
}

@Composable
fun TvGrid3(
    modifier: Modifier = Modifier,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    OneTimeLaunchedEffect {
        viewModel.init()
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)

        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()

        LoadingState.Success -> {
            val channels by viewModel.channels.observeAsState(listOf())
            val programs by viewModel.programs.observeAsState(listOf())
            ProgramGuide(
                modifier = modifier,
            ) {
                guideStartHour = 22f
                programs(
                    items = programs,
                    layoutInfo = { p ->
                        if (p != null) {
                            Timber.v("${p.id}: ${p.start.hour + p.start.minute / 60f}")
                            ProgramGuideItem.Program(
                                channelIndex = channels.indexOfFirst { it.id == p.channelId },
                                startHour = p.start.hour + p.start.minute / 60f,
                                endHour = p.start.hour + p.start.minute / 60f,
                            )
                        } else {
                            ProgramGuideItem.Program(0, 0f, 0f)
                        }
                    },
                    itemContent = { program ->
//                        Timber.v("Render $program")
                        Text(
                            text = program?.name ?: "",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.focusable(),
                        )
//                        ListItem(
//                            selected = false,
//                            onClick = {},
//                            headlineContent = {
//                                Text(
//                                    text = program?.name ?: "",
//                                    color = MaterialTheme.colorScheme.onSurface,
//                                    modifier = Modifier,
//                                )
//                            },
//                            supportingContent = {
//                                program?.subtitle?.let {
//                                    Text(
//                                        text = program.subtitle,
//                                        color = MaterialTheme.colorScheme.onSurface,
//                                    )
//                                }
//                            },
//                            modifier = Modifier,
//                        )
                    },
                )
                channels(
                    count = channels.size,
                    layoutInfo = {
                        ProgramGuideItem.Channel(
                            index = it,
                        )
                    },
                    itemContent = {
                        val channel = channels[it]
                        Text(
                            text = channel.number ?: "",
                        )
                    },
                )
                timeline(
                    count = 12,
                    layoutInfo = {
                        val start = 22f + it
                        ProgramGuideItem.Timeline(
                            startHour = start,
                            endHour = start + 1f,
                        )
                    },
                    itemContent = {
                        Text(
                            text = "${22f + it} o'clock",
                        )
                    },
                )
            }
        }
    }
}

@Composable
fun TvGrid2(
    modifier: Modifier = Modifier,
    viewModel: LiveTvViewModel = hiltViewModel(),
) {
    OneTimeLaunchedEffect {
        viewModel.init()
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)

        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()

        LoadingState.Success -> {
            val channels by viewModel.channels.observeAsState(listOf())
            var focusedRowIndex by rememberInt()

            val columnState = rememberLazyListState()
            val rowStates =
                rememberSaveable(
                    saver =
                        listSaver(
                            save = { it.map { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) } },
                            restore = {
                                it.map {
                                    LazyListState(
                                        firstVisibleItemIndex = it[0],
                                        firstVisibleItemScrollOffset = it[1],
                                    )
                                }
                            },
                        ),
                ) { List(channels.size) { LazyListState(0, 0) } }

            val currentRowState = rowStates[focusedRowIndex]
            LaunchedEffect(currentRowState.firstVisibleItemScrollOffset) {
                rowStates.forEachIndexed { index, state ->
                    if (index != focusedRowIndex) {
                        currentRowState.layoutInfo.visibleItemsInfo
                        state.scrollToItem(currentRowState.firstVisibleItemIndex, currentRowState.firstVisibleItemScrollOffset)
                    }
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = columnState,
                modifier = modifier,
            ) {
                itemsIndexed(channels) { channelIndex, channel ->
                    val programs by viewModel.getPrograms(channel.id).observeAsState(listOf())
                    ChannelRow(
                        lazyListState = rowStates[channelIndex],
                        channel = channel,
                        programs = programs,
                        modifier =
                            Modifier
                                .height(56.dp)
                                .fillMaxWidth()
                                .onFocusChanged {
                                    if (it.hasFocus) {
                                        focusedRowIndex = channelIndex
                                    }
                                },
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelRow(
    lazyListState: LazyListState,
    channel: TvChannel,
    programs: List<TvProgram?>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
    ) {
        ListItem(
            selected = false,
            onClick = {
                Timber.v("Clicked channel $channel")
            },
            leadingContent = {
                Text(
                    text = channel.number ?: "",
                    modifier = Modifier.width(40.dp),
                )
            },
            headlineContent = {
                Text(
                    text = channel.name ?: "",
                    modifier = Modifier.width(80.dp),
                )
            },
            trailingContent = {
                AsyncImage(
                    model = channel.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.width(64.dp).fillMaxHeight(),
                )
            },
            modifier = Modifier.width(220.dp).fillMaxHeight(),
        )
        LazyRow(
            state = lazyListState,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxSize(),
        ) {
            itemsIndexed(programs) { programIndex, program ->
                Timber.v("program=$program")
                val duration = (program?.duration ?: 30.minutes).coerceAtLeast(2.minutes)
                val width = duration.inWholeMinutes / 60f * hourWidth
                ListItem(
                    selected = false,
                    onClick = {},
                    headlineContent = {
                        Text(
                            text = program?.name ?: "",
                            modifier = Modifier,
                        )
                    },
                    supportingContent = {
                        program?.subtitle?.let {
                            Text(
                                text = program.subtitle,
                            )
                        }
                    },
                    modifier = Modifier.width(width).fillMaxHeight(),
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun ChannelRowPreview() {
    val channelId = UUID.randomUUID()
    val channel =
        TvChannel(
            id = channelId,
            number = "2.1",
            name = "WJTV",
            imageUrl = "",
        )
    val programs =
        listOf(
            TvProgram(
                id = UUID.randomUUID(),
                channelId = channelId,
                start = LocalDateTime.of(2025, 10, 16, 18, 0, 0),
                end = LocalDateTime.of(2025, 10, 16, 19, 0, 0),
                duration = 60.minutes,
                name = "Program #1",
                subtitle = null,
                seasonEpisode = null,
            ),
            TvProgram(
                id = UUID.randomUUID(),
                channelId = channelId,
                start = LocalDateTime.of(2025, 10, 16, 19, 0, 0),
                end = LocalDateTime.of(2025, 10, 16, 19, 30, 0),
                duration = 30.minutes,
                name = "Program #1",
                subtitle = null,
                seasonEpisode = null,
            ),
            TvProgram(
                id = UUID.randomUUID(),
                channelId = channelId,
                start = LocalDateTime.of(2025, 10, 16, 19, 30, 0),
                end = LocalDateTime.of(2025, 10, 16, 20, 0, 0),
                duration = 30.minutes,
                name = "Program #1",
                subtitle = null,
                seasonEpisode = null,
            ),
        )
    WholphinTheme {
        ChannelRow(
            lazyListState = rememberLazyListState(),
            channel = channel,
            programs = programs,
            modifier = Modifier.height(140.dp),
        )
    }
}

@PreviewTvSpec
@Composable
private fun TvGridPreview() {
}
