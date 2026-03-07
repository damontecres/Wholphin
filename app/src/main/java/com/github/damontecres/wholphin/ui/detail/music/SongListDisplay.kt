package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.roundSeconds
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
fun SongListItem(
    song: BaseItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onClickMore: () -> Unit,
    modifier: Modifier = Modifier,
    showArtist: Boolean = false,
    isPlaying: Boolean = false,
    isQueued: Boolean = false,
) = SongListItem(
    title = song?.title,
    artist = if (showArtist) song?.data?.albumArtist else null,
    indexNumber = song?.data?.indexNumber,
    runtime =
        remember(song) {
            song
                ?.data
                ?.runTimeTicks
                ?.ticks
                ?.roundSeconds
        },
    onClick = onClick,
    onLongClick = onLongClick,
    modifier = modifier,
    showArtist = showArtist,
    isPlaying = isPlaying,
    isQueued = isQueued,
    showMoreButton = true,
    onClickMore = onClickMore,
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
    isQueued: Boolean = false,
    showMoreButton: Boolean = false,
    onClickMore: () -> Unit = {},
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
                MusicQueueMarker(
                    isPlaying = isPlaying,
                    isQueued = isQueued,
                )
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
                modifier = Modifier.weight(1f),
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
                modifier = Modifier.weight(1f),
            )
        }
        if (showMoreButton) {
            Button(
                onClick = onClickMore,
                enabled = true,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more),
                )
            }
        }
    }
}

val BaseItem.artistsString: String? get() = data.artists?.letNotEmpty { it.joinToString(", ") }

/**
 * Add an indicator for if the item is currently playing or queued
 */
@Composable
fun MusicQueueMarker(
    isPlaying: Boolean,
    isQueued: Boolean,
    modifier: Modifier = Modifier,
) {
    if (isPlaying) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = modifier,
        )
    } else if (isQueued) {
        Box(
            modifier =
                modifier
                    .padding(horizontal = 8.dp)
                    .clip(CircleShape)
                    .background(LocalContentColor.current)
                    .size(8.dp),
        )
    }
}

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
                indexNumber = 1,
                runtime = 2.minutes + 30.seconds,
                onClick = {},
                onLongClick = { },
                modifier = Modifier,
                showArtist = false,
                isQueued = true,
                showMoreButton = true,
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
