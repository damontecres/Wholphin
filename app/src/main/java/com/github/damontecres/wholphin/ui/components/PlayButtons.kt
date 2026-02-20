package com.github.damontecres.wholphin.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.theme.PreviewInteractionSource
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Standard row of [ExpandablePlayButton] including Play (or Resume & Restart), Mark played, & More
 */
@Composable
fun ExpandablePlayButtons(
    resumePosition: Duration,
    watched: Boolean,
    favorite: Boolean,
    trailers: List<Trailer>?,
    playOnClick: (position: Duration) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    buttonOnFocusChanged: (FocusState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus = remember { FocusRequester() }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(8.dp),
        modifier =
            modifier
                .focusGroup()
                .focusRestorer(firstFocus),
    ) {
        if (resumePosition > Duration.ZERO) {
            item("play") {
                ExpandablePlayButton(
                    title = R.string.resume,
                    resume = resumePosition,
                    icon = Icons.Default.PlayArrow,
                    onClick = playOnClick,
                    modifier =
                        Modifier
                            .onFocusChanged(buttonOnFocusChanged)
                            .focusRequester(firstFocus),
                )
            }
            item("restart") {
                ExpandablePlayButton(
                    title = R.string.restart,
                    resume = Duration.ZERO,
                    icon = Icons.Default.Refresh,
                    onClick = playOnClick,
                    modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                    mirrorIcon = true,
                )
            }
        } else {
            item("play") {
                ExpandablePlayButton(
                    title = R.string.play,
                    resume = Duration.ZERO,
                    icon = Icons.Default.PlayArrow,
                    onClick = playOnClick,
                    modifier =
                        Modifier
                            .onFocusChanged(buttonOnFocusChanged)
                            .focusRequester(firstFocus),
                )
            }
        }

        // Watched button
        item("watched") {
            ExpandableFaButton(
                title = if (watched) R.string.mark_unwatched else R.string.mark_watched,
                iconStringRes = if (watched) R.string.fa_eye else R.string.fa_eye_slash,
                onClick = watchOnClick,
                modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
            )
        }

        // Favorite button
        item("favorite") {
            ExpandableFaButton(
                title = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                iconStringRes = R.string.fa_heart,
                onClick = favoriteOnClick,
                iconColor = if (favorite) Color.Red else Color.Unspecified,
                modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
            )
        }

        if (trailers != null) {
            item("trailers") {
                TrailerButton(
                    trailers = trailers,
                    trailerOnClick = trailerOnClick,
                    modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                )
            }
        }

        // More button
        item("more") {
            ExpandablePlayButton(
                title = R.string.more,
                resume = Duration.ZERO,
                icon = Icons.Default.MoreVert,
                onClick = { moreOnClick.invoke() },
                modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
            )
        }
    }
}

val MinButtonSize = 40.dp

/**
 * An icon button typically used in a row for playing media
 *
 * Only shows the icon until focused when it expands to show the title
 */
@Composable
fun ExpandablePlayButton(
    @StringRes title: Int,
    resume: Duration,
    icon: ImageVector,
    onClick: (position: Duration) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    mirrorIcon: Boolean = false,
    enabled: Boolean = true,
) = ExpandablePlayButton(
    title = title,
    resume = resume,
    icon = {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier =
                Modifier
                    .size(28.dp)
                    .ifElse(mirrorIcon, Modifier.graphicsLayer { scaleX = -1f }),
        )
    },
    onClick = onClick,
    modifier = modifier,
    interactionSource = interactionSource,
    enabled = enabled,
)

@Composable
fun ExpandablePlayButton(
    @StringRes title: Int,
    resume: Duration,
    icon: Painter,
    onClick: (position: Duration) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    mirrorIcon: Boolean = false,
    enabled: Boolean = true,
) = ExpandablePlayButton(
    title = title,
    resume = resume,
    icon = {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier =
                Modifier
                    .size(28.dp)
                    .ifElse(mirrorIcon, Modifier.graphicsLayer { scaleX = -1f }),
        )
    },
    onClick = onClick,
    modifier = modifier,
    interactionSource = interactionSource,
    enabled = enabled,
)

@Composable
fun ExpandablePlayButton(
    @StringRes title: Int,
    resume: Duration,
    icon: @Composable () -> Unit,
    onClick: (position: Duration) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
) {
    val isFocused = interactionSource.collectIsFocusedAsState().value
    Button(
        onClick = { onClick.invoke(resume) },
        enabled = enabled,
        modifier =
            modifier.requiredSizeIn(
                minWidth = MinButtonSize,
                minHeight = MinButtonSize,
                maxHeight = MinButtonSize,
            ),
        contentPadding = DefaultButtonPadding,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(start = 2.dp, top = 2.dp)
                    .height(MinButtonSize),
        ) {
            icon.invoke()
        }
        AnimatedVisibility(isFocused) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

/**
 * Similar to [ExpandablePlayButton], but uses a [FontAwesome] string instead of an Icon
 */
@Composable
fun ExpandableFaButton(
    @StringRes title: Int,
    @StringRes iconStringRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    iconColor: Color = Color.Unspecified,
    enabled: Boolean = true,
) {
    val isFocused = interactionSource.collectIsFocusedAsState().value
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier.requiredSizeIn(
                minWidth = MinButtonSize,
                minHeight = MinButtonSize,
                maxHeight = MinButtonSize,
            ),
        contentPadding = DefaultButtonPadding,
        interactionSource = interactionSource,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp),
        ) {
            Text(
                text = stringResource(iconStringRes),
                style = MaterialTheme.typography.titleSmall,
                color = iconColor,
                fontSize = 16.sp,
                fontFamily = FontAwesome,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        AnimatedVisibility(isFocused) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
    }
}

@Composable
fun TrailerButton(
    trailers: List<Trailer>,
    trailerOnClick: (Trailer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }
    ExpandableFaButton(
        title =
            if (trailers.isEmpty()) {
                R.string.no_trailers
            } else if (trailers.size == 1) {
                R.string.play_trailer
            } else {
                R.string.trailers
            },
        iconStringRes = R.string.fa_film,
        enabled = trailers.isNotEmpty(),
        onClick = {
            if (trailers.size == 1) {
                trailerOnClick.invoke(trailers.first())
            } else {
                showDialog = true
            }
        },
        modifier = modifier,
    )
    if (showDialog) {
        TrailerDialog(
            onDismissRequest = { showDialog = false },
            trailers = trailers,
            onClick = trailerOnClick,
        )
    }
}

@PreviewTvSpec
@Composable
private fun ExpandablePlayButtonsPreview() {
    WholphinTheme(true) {
        ExpandablePlayButtons(
            resumePosition = 10.seconds,
            watched = false,
            favorite = false,
            playOnClick = {},
            watchOnClick = {},
            favoriteOnClick = {},
            moreOnClick = {},
            buttonOnFocusChanged = {},
            trailers = listOf(),
            trailerOnClick = {},
            modifier = Modifier,
        )
    }
}

@PreviewTvSpec
@Composable
private fun ViewOptionsPreview() {
    val source = remember { PreviewInteractionSource() }
    WholphinTheme {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            ExpandablePlayButton(
                title = R.string.play,
                resume = Duration.ZERO,
                icon = Icons.Default.PlayArrow,
                onClick = {},
                interactionSource = source,
            )
            ExpandablePlayButton(
                title = R.string.play,
                resume = Duration.ZERO,
                icon = painterResource(R.drawable.baseline_pause_24),
                onClick = {},
                interactionSource = source,
            )
            ExpandableFaButton(
                title = R.string.play,
                iconStringRes = R.string.fa_eye,
                onClick = {},
                modifier = Modifier,
                interactionSource = source,
            )

            Row {
                ExpandableFaButton(
                    title = R.string.mark_unwatched,
                    iconStringRes = R.string.fa_eye,
                    onClick = {},
                    modifier = Modifier,
                )
                SortByButton(
                    sortOptions = listOf(),
                    current = SortAndDirection(ItemSortBy.DEFAULT, SortOrder.ASCENDING),
                    onSortChange = {},
                )
            }
        }
    }
}
