package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.ifElse
import org.jellyfin.sdk.model.api.LyricDto
import org.jellyfin.sdk.model.api.LyricLine

@Composable
fun LyricsContent(
    lyrics: LyricDto?,
    currentLyricPosition: Int?,
    onClick: (LyricLine) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(currentLyricPosition ?: 0)
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(currentLyricPosition) {
        if (currentLyricPosition != null) {
            bringIntoViewRequester.bringIntoView()
        }
    }
    Column(modifier) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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

                    Text(
                        text = lyric.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = color,
                        modifier =
                            Modifier.ifElse(
                                index == currentLyricPosition,
                                Modifier.bringIntoViewRequester(bringIntoViewRequester),
                            ),
                    )
                }
            }
        }
    }
}
