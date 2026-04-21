package com.github.damontecres.wholphin.ui.playback.overlay

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.ui.cards.ChapterCard
import com.github.damontecres.wholphin.ui.components.HiddenFocusBox
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ChapterRowOverlay(
    player: Player,
    controllerViewState: ControllerViewState,
    chapters: List<Chapter>,
    playlist: Playlist,
    aspectRatio: Float,
    onChangeState: (OverlayViewState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapterInteractionSources =
        remember(chapters.size) { List(chapters.size) { MutableInteractionSource() } }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val chapterIndex =
        remember {
            val position = player.currentPosition.milliseconds
            val index =
                chapters
                    .indexOfFirst { it.position > position }
                    .minus(1)
                    .let {
                        if (it < 0) {
                            // Didn't find a chapter, so it's either the first or last
                            if (position < chapters.first().position) {
                                0
                            } else {
                                chapters.lastIndex
                            }
                        } else {
                            it
                        }
                    }.coerceIn(0, chapters.lastIndex)
            index
        }
    val listState = rememberLazyListState(chapterIndex)
    val focusRequester = remember { FocusRequester() }
    val hiddenFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        bringIntoViewRequester.bringIntoView()
        focusRequester.tryRequestFocus()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        HiddenFocusBox(hiddenFocusRequester) {
            onChangeState.invoke(OverlayViewState.CONTROLLER)
        }
        Text(
            text = stringResource(R.string.chapters),
            style = MaterialTheme.typography.titleLarge,
        )
        LazyRow(
            state = listState,
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
                    }.focusProperties {
                        up = hiddenFocusRequester
                    },
        ) {
            itemsIndexed(chapters) { index, chapter ->
                val interactionSource = chapterInteractionSources[index]
                val isFocused =
                    interactionSource.collectIsFocusedAsState().value
                LaunchedEffect(isFocused) {
                    if (isFocused) controllerViewState.pulseControls()
                }
                ChapterCard(
                    name = chapter.name,
                    position = chapter.position,
                    imageUrl = chapter.imageUrl,
                    aspectRatio = aspectRatio,
                    onClick = {
                        player.seekTo(chapter.position.inWholeMilliseconds)
                        controllerViewState.hideControls()
                    },
                    interactionSource = interactionSource,
                    modifier =
                        Modifier
                            .ifElse(
                                index == chapterIndex,
                                Modifier
                                    .focusRequester(focusRequester)
                                    .bringIntoViewRequester(
                                        bringIntoViewRequester,
                                    ),
                            ).ifElse(
                                index == 0,
                                Modifier.focusProperties {
                                    // Prevent scrolling left on first card to prevent moving down
                                    left = FocusRequester.Cancel
                                },
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
                        .padding(bottom = 8.dp)
                        .onFocusChanged {
                            if (it.isFocused) onChangeState.invoke(OverlayViewState.QUEUE)
                        }.focusable(),
            )
        }
    }
}
