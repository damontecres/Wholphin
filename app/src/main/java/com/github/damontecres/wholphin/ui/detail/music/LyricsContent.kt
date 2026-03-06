package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.api.LyricLine

@Composable
fun LyricsContent(
    lyricsHaveFocus: Boolean,
    lyrics: LyricDto?,
    currentLyricPosition: Int?,
    onClick: (LyricLine) -> Unit,
    onFocusLyrics: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequesters =
        remember(lyrics) { List(lyrics?.lyrics.orEmpty().size) { FocusRequester() } }
    val listState = rememberLazyListState(currentLyricPosition ?: 0)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    if (!lyricsHaveFocus) {
        LaunchedEffect(currentLyricPosition) {
            if (currentLyricPosition != null) {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let {
                    if (currentLyricPosition !in listState.firstVisibleItemIndex..it) {
                        listState.animateScrollToItem(currentLyricPosition)
                    }
                }
                bringIntoViewRequester.bringIntoView()
            }
        }
    }
    Column(modifier) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusProperties {
                        onEnter = {
                            if (currentLyricPosition != null) {
                                currentLyricPosition.let {
                                    focusRequesters
                                        .getOrNull(currentLyricPosition)
                                        ?.tryRequestFocus()
                                }
                            } else {
                                focusRequesters.getOrNull(0)?.tryRequestFocus()
                            }
                        }
                    }.onFocusChanged {
                        onFocusLyrics.invoke(it.hasFocus)
                    },
        ) {
            if (lyrics?.lyrics?.isNotEmpty() == true) {
                itemsIndexed(lyrics.lyrics) { index, lyric ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val focused by interactionSource.collectIsFocusedAsState()
                    val color by animateColorAsState(
                        if (index == currentLyricPosition || currentLyricPosition == null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
                        },
                        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
                    )
                    Surface(
                        onClick = { onClick.invoke(lyric) },
                        interactionSource = interactionSource,
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.border.copy(alpha = .33f),
                            ),
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                        scale =
                            ClickableSurfaceDefaults.scale(
                                focusedScale = 1f,
                                pressedScale = .9f,
                            ),
                        modifier =
                            Modifier
                                .focusRequester(focusRequesters[index]),
                    ) {
                        Text(
                            text = lyric.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (focused) MaterialTheme.colorScheme.onSurface else color,
                            modifier =
                                Modifier
                                    .padding(8.dp)
                                    .ifElse(
                                        index == currentLyricPosition,
                                        Modifier.bringIntoViewRequester(bringIntoViewRequester),
                                    ),
                        )
                    }
                }
            }
        }
    }
}
