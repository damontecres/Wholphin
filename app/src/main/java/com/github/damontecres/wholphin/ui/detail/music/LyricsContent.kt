package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.ifElse
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.api.LyricLine

@Composable
fun LyricsContent(
    synced: Boolean,
    lyrics: LyricDto?,
    currentLyricPosition: Int?,
    onClick: (LyricLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(currentLyricPosition ?: 0)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    if (synced) {
        LaunchedEffect(currentLyricPosition) {
            if (currentLyricPosition != null) {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index?.let {
                    if (currentLyricPosition !in 0..it) {
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (lyrics?.lyrics?.isNotEmpty() == true) {
                itemsIndexed(lyrics.lyrics) { index, lyric ->
                    val color by animateColorAsState(
                        if (index == currentLyricPosition || currentLyricPosition == null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = .5f)
                        },
                        animationSpec = tween(durationMillis = 500, easing = LinearEasing),
                    )
                    val interactionSource = remember { MutableInteractionSource() }
                    val focused by interactionSource.collectIsFocusedAsState()
                    Box(
                        modifier =
                            Modifier
                                .background(
                                    color = if (focused) MaterialTheme.colorScheme.border.copy(alpha = .66f) else Color.Unspecified,
                                    shape = RoundedCornerShape(8.dp),
                                ),
                    ) {
                        Text(
                            text = lyric.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = color,
                            modifier =
                                Modifier
                                    .padding(4.dp)
                                    .clickable(
                                        enabled = !synced,
                                        onClick = { onClick.invoke(lyric) },
                                        interactionSource = interactionSource,
                                    ).ifElse(
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
