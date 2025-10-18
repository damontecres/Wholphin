package com.github.damontecres.wholphin.ui.detail.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.ui.playback.isDown
import com.github.damontecres.wholphin.ui.playback.isUp
import com.github.damontecres.wholphin.ui.tryRequestFocus
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

@Composable
fun TvGrid(modifier: Modifier = Modifier) = TvGridBox(channels, programs, modifier)

@Composable
fun TvGridBox(
    channels: List<TvChannel>,
    programs: Map<UUID, List<TvProgram>>,
    modifier: Modifier = Modifier,
) {
    val startHour = 18f
    var hour by remember { mutableFloatStateOf(startHour) }
    Timber.v("hour=$hour")
    val hoursToShow = 3f
    val widthPerHour = 200.dp

    Column(modifier = modifier) {
        // Header
        Text(
            text = hour.toString(),
        )
        LazyColumn(
            modifier =
            Modifier,
        ) {
            itemsIndexed(channels) { channelIndex, channel ->
                val programs = programs[channel.id].orEmpty()
                var rowHasFocus by remember { mutableStateOf(false) }
                Row(
                    modifier =
                        Modifier
                            .height(120.dp)
                            .fillMaxWidth()
                            .background(
                                if (rowHasFocus) {
                                    MaterialTheme.colorScheme.surfaceColorAtElevation(
                                        3.dp,
                                    )
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                            ),
                ) {
                    val channelFocusRequester = remember { FocusRequester() }
                    // Channel info
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
                                modifier =
                                    Modifier
                                        .width(64.dp)
                                        .fillMaxHeight(),
                            )
                        },
                        modifier =
                            Modifier
                                .width(220.dp)
                                .fillMaxHeight()
                                .focusRequester(channelFocusRequester),
                    )
                    val focusRequester = remember { FocusRequester() }
                    var capturedFocus by remember { mutableStateOf(false) }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onKeyEvent {
                                    Timber.v("onKeyEvent=$it")
                                    if (it.type == KeyEventType.KeyUp) {
                                        if (rowHasFocus) {
                                            if (it.type == KeyEventType.KeyUp && it.key == Key.DirectionLeft && hour <= startHour) {
                                                focusRequester.freeFocus()
                                                channelFocusRequester.tryRequestFocus()
                                                capturedFocus = false
                                                return@onKeyEvent false
                                            }
                                            if (isUp(it) || isDown(it)) {
                                                focusRequester.freeFocus()
                                                capturedFocus = false
                                                return@onKeyEvent false
                                            }
                                            if (it.type == KeyEventType.KeyUp && it.key == Key.DirectionLeft && hour > startHour) {
                                                hour -= .5f
                                                return@onKeyEvent true
                                            } else if (it.type == KeyEventType.KeyUp && it.key == Key.DirectionRight) {
                                                hour += .5f
                                                return@onKeyEvent true
                                            }
                                        }
                                    }
                                    return@onKeyEvent false
                                }.onFocusChanged {
                                    Timber.v("onFocusChanged=$it")
                                    rowHasFocus = it.hasFocus
                                    if (it.isFocused && !capturedFocus) {
                                        focusRequester.captureFocus()
                                        capturedFocus = true
                                    }
                                }.focusProperties {
                                    onEnter = {
                                        Timber.v("onEnter")
                                        focusRequester.captureFocus()
                                    }
                                    onExit = {
                                        Timber.v("onExit")
                                        capturedFocus = false
                                    }
                                }.focusable(),
                    ) {
                        val filtered =
                            programs
                                .filter {
                                    it.start.hours <= hour && it.end.hours > hour ||
                                        (it.start.hours > hour && it.start.hours < (hour + hoursToShow))
                                }.sortedBy { it.start }
                        LaunchedEffect(filtered) {
                            Timber.v("Got ${filtered.size} for ${channel.number}")
                        }
                        filtered
                            .forEach { p ->
                                Timber.v("Showing program: $p")
                                val width = p.duration.inWholeMinutes / 60f * widthPerHour
                                val backgroundColor =
                                    if (rowHasFocus && p.start.hours <= hour && p.end.hours > (hour)) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        Color.Unspecified
                                    }
                                Box(
                                    modifier =
                                        Modifier
                                            .width(width)
                                            .fillMaxHeight()
                                            .background(backgroundColor)
                                            .border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.inverseSurface,
                                            ),
                                ) {
                                    Text(
                                        text = p.name ?: "Unknown",
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                    }
                }
            }
        }
    }
}

val LocalDateTime.hours get() = hour + minute / 60f

val channel1Id = UUID.randomUUID()
val channel2Id = UUID.randomUUID()
val channel3Id = UUID.randomUUID()

val channels =
    listOf(
        TvChannel(
            id = channel1Id,
            number = "2.1",
            name = "WJTV",
            imageUrl = "",
        ),
        TvChannel(
            id = channel2Id,
            number = "3.1",
            name = "JYTV",
            imageUrl = "",
        ),
        TvChannel(
            id = channel3Id,
            number = "4.1",
            name = "AQTV",
            imageUrl = "",
        ),
    )

val programs =
    mapOf(
        channel1Id to
            listOf(
                TvProgram(
                    id = UUID.randomUUID(),
                    channelId = channel1Id,
                    start = LocalDateTime.of(2025, 10, 16, 18, 0, 0),
                    end = LocalDateTime.of(2025, 10, 16, 19, 0, 0),
                    duration = 60.minutes,
                    name = "Program #1",
                    subtitle = null,
                    seasonEpisode = null,
                ),
                TvProgram(
                    id = UUID.randomUUID(),
                    channelId = channel1Id,
                    start = LocalDateTime.of(2025, 10, 16, 19, 0, 0),
                    end = LocalDateTime.of(2025, 10, 16, 19, 30, 0),
                    duration = 30.minutes,
                    name = "Program #2",
                    subtitle = null,
                    seasonEpisode = null,
                ),
                TvProgram(
                    id = UUID.randomUUID(),
                    channelId = channel1Id,
                    start = LocalDateTime.of(2025, 10, 16, 19, 30, 0),
                    end = LocalDateTime.of(2025, 10, 16, 20, 0, 0),
                    duration = 30.minutes,
                    name = "Program #3",
                    subtitle = null,
                    seasonEpisode = null,
                ),
                TvProgram(
                    id = UUID.randomUUID(),
                    channelId = channel1Id,
                    start = LocalDateTime.of(2025, 10, 16, 20, 0, 0, 0),
                    end = LocalDateTime.of(2025, 10, 16, 21, 0, 0),
                    duration = 60.minutes,
                    name = "Program #3",
                    subtitle = null,
                    seasonEpisode = null,
                ),
                TvProgram(
                    id = UUID.randomUUID(),
                    channelId = channel1Id,
                    start = LocalDateTime.of(2025, 10, 16, 21, 0, 0, 0),
                    end = LocalDateTime.of(2025, 10, 16, 22, 0, 0),
                    duration = 60.minutes,
                    name = "Program #3",
                    subtitle = null,
                    seasonEpisode = null,
                ),
                TvProgram(
                    id = UUID.randomUUID(),
                    channelId = channel1Id,
                    start = LocalDateTime.of(2025, 10, 16, 22, 0, 0, 0),
                    end = LocalDateTime.of(2025, 10, 16, 23, 0, 0),
                    duration = 60.minutes,
                    name = "Program #3",
                    subtitle = null,
                    seasonEpisode = null,
                ),
            ),
        channel2Id to
            listOf(
                TvProgram(
                    id = UUID.randomUUID(),
                    channelId = channel2Id,
                    start = LocalDateTime.of(2025, 10, 16, 18, 0, 0),
                    end = LocalDateTime.of(2025, 10, 16, 18, 30, 0),
                    duration = 30.minutes,
                    name = "Program #1",
                    subtitle = null,
                    seasonEpisode = null,
                ),
                TvProgram(
                    id = UUID.randomUUID(),
                    channelId = channel2Id,
                    start = LocalDateTime.of(2025, 10, 16, 18, 30, 0),
                    end = LocalDateTime.of(2025, 10, 16, 19, 30, 0),
                    duration = 60.minutes,
                    name = "Program #2",
                    subtitle = null,
                    seasonEpisode = null,
                ),
                TvProgram(
                    id = UUID.randomUUID(),
                    channelId = channel2Id,
                    start = LocalDateTime.of(2025, 10, 16, 19, 30, 0),
                    end = LocalDateTime.of(2025, 10, 16, 20, 0, 0),
                    duration = 30.minutes,
                    name = "Program #3",
                    subtitle = null,
                    seasonEpisode = null,
                ),
            ),
        channel3Id to
            listOf(
                TvProgram(
                    id = UUID.randomUUID(),
                    channelId = channel3Id,
                    start = LocalDateTime.of(2025, 10, 16, 18, 0, 0),
                    end = LocalDateTime.of(2025, 10, 16, 19, 0, 0),
                    duration = 60.minutes,
                    name = "Program #1",
                    subtitle = null,
                    seasonEpisode = null,
                ),
                TvProgram(
                    id = UUID.randomUUID(),
                    channelId = channel3Id,
                    start = LocalDateTime.of(2025, 10, 16, 19, 0, 0),
                    end = LocalDateTime.of(2025, 10, 16, 19, 30, 0),
                    duration = 30.minutes,
                    name = "Program #2",
                    subtitle = null,
                    seasonEpisode = null,
                ),
                TvProgram(
                    id = UUID.randomUUID(),
                    channelId = channel3Id,
                    start = LocalDateTime.of(2025, 10, 16, 19, 30, 0),
                    end = LocalDateTime.of(2025, 10, 16, 20, 0, 0),
                    duration = 30.minutes,
                    name = "Program #3",
                    subtitle = null,
                    seasonEpisode = null,
                ),
            ),
    )
