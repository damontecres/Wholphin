package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
fun SongListDisplay(
    songs: List<BaseItem?>,
    showArtist: Boolean,
    onClick: (Int, BaseItem) -> Unit,
    onLongClick: (Int, BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        itemsIndexed(songs) { index, song ->
            SongListItem(
                song = song,
                onClick = { song?.let { onClick.invoke(index, song) } },
                onLongClick = { song?.let { onLongClick.invoke(index, song) } },
                showArtist = showArtist,
                modifier = Modifier,
            )
        }
    }
}

@Composable
fun SongListItem(
    song: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArtist: Boolean = false,
    isPlaying: Boolean = false,
) = SongListItem(
    title = song?.title,
    artist = if (showArtist) song?.data?.albumArtist else null,
    indexNumber = song?.data?.indexNumber,
    runtime =
        song
            ?.data
            ?.runTimeTicks
            ?.ticks
            ?.roundMinutes,
    onClick = onClick,
    onLongClick = onLongClick,
    modifier = modifier,
    showArtist = showArtist,
    isPlaying = isPlaying,
)

@Composable
fun SongListItem(
    title: String?,
    artist: String?,
    indexNumber: Int?,
    runtime: Duration?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showArtist: Boolean = false,
    isPlaying: Boolean = false,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        val focused by interactionSource.collectIsFocusedAsState()
        val leadingContent: @Composable (BoxScope.() -> Unit) = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = indexNumber?.toString() ?: "",
                )
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                    )
                }
            }
        }
        val headlineContent = @Composable {
            Text(
                text = title ?: "",
                maxLines = 1,
                modifier = Modifier.enableMarquee(focused),
            )
        }
        val trailingContent = @Composable {
            Text(
                text = runtime.toString(),
            )
        }

        if (showArtist) {
            // TODO use dense?
            ListItem(
                selected = isPlaying,
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = interactionSource,
                leadingContent = leadingContent,
                headlineContent = headlineContent,
                supportingContent = {
                    Text(
                        text = artist ?: "",
                    )
                },
                trailingContent = trailingContent,
                scale = ListItemDefaults.scale(1f, 1f, .95f),
                modifier = Modifier,
            )
        } else {
            ListItem(
                selected = isPlaying,
                onClick = onClick,
                onLongClick = onLongClick,
                interactionSource = interactionSource,
                leadingContent = leadingContent,
                headlineContent = headlineContent,
                supportingContent = null,
                trailingContent = trailingContent,
                scale = ListItemDefaults.scale(1f, 1f, .95f),
                modifier = Modifier,
            )
        }
    }
}

val BaseItem.artistsString: String? get() = data.artists?.letNotEmpty { it.joinToString(", ") }

@PreviewTvSpec
@Composable
fun SongListItemPreview() {
    WholphinTheme {
        Column {
            SongListItem(
                title = "Song title",
                artist = "Artists",
                indexNumber = 1,
                runtime = 2.minutes + 30.seconds,
                onClick = {},
                onLongClick = { },
                modifier = Modifier,
                showArtist = false,
            )
            SongListItem(
                title = "Song title",
                artist = "Artists",
                indexNumber = 2,
                runtime = 2.minutes + 30.seconds,
                onClick = {},
                onLongClick = { },
                modifier = Modifier,
                showArtist = true,
                isPlaying = true,
            )
        }
    }
}
