package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
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
    onClickAddToQueue: (Int, BaseItem) -> Unit,
    onClickAddToPlaylist: (Int, BaseItem) -> Unit,
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
                onClickAddToQueue = { song?.let { onClick.invoke(index, song) } },
                onClickAddToPlaylist = { song?.let { onClick.invoke(index, song) } },
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
    onClickAddToQueue: () -> Unit,
    onClickAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    showArtist: Boolean = false,
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
    onClickAddToQueue = onClickAddToQueue,
    onClickAddToPlaylist = onClickAddToPlaylist,
    modifier = modifier,
    showArtist = showArtist,
)

@Composable
fun SongListItem(
    title: String?,
    artist: String?,
    indexNumber: Int?,
    runtime: Duration?,
    onClick: () -> Unit,
    onClickAddToQueue: () -> Unit,
    onClickAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    showArtist: Boolean = false,
    isPlaying: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        ListItem(
            selected = isPlaying,
            onClick = onClick,
            leadingContent = {
                Text(
                    text = indexNumber?.toString() ?: "",
                )
            },
            headlineContent = {
                Text(
                    text = title ?: "",
                )
            },
            supportingContent =
                if (showArtist && artist.isNotNullOrBlank()) {
                    {
                        Text(
                            text = artist,
                        )
                    }
                } else {
                    null
                },
            trailingContent = {
                Text(
                    text = runtime.toString(),
                )
            },
            scale = ListItemDefaults.scale(1f, 1f, .95f),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onClickAddToQueue,
            shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        ) {
            Text(
                text = "Add to queue",
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
                onClickAddToQueue = { },
                onClickAddToPlaylist = {},
                modifier = Modifier,
                showArtist = false,
            )
            SongListItem(
                title = "Song title",
                artist = "Artists",
                indexNumber = 1,
                runtime = 2.minutes + 30.seconds,
                onClick = {},
                onClickAddToQueue = { },
                onClickAddToPlaylist = {},
                modifier = Modifier,
                showArtist = true,
            )
        }
    }
}
