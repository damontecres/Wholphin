package com.github.damontecres.wholphin.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.DefaultButtonPadding
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
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
    playOnClick: (position: Duration) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
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
                    R.string.resume,
                    resumePosition,
                    Icons.Default.PlayArrow,
                    playOnClick,
                    Modifier
                        .onFocusChanged(buttonOnFocusChanged)
                        .focusRequester(firstFocus),
                )
            }
            item("restart") {
                ExpandablePlayButton(
                    R.string.restart,
                    Duration.ZERO,
                    Icons.Default.Refresh,
                    playOnClick,
                    Modifier.onFocusChanged(buttonOnFocusChanged),
                    mirrorIcon = true,
                )
            }
        } else {
            item("play") {
                ExpandablePlayButton(
                    R.string.play,
                    Duration.ZERO,
                    Icons.Default.PlayArrow,
                    playOnClick,
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

        // More button
        item("more") {
            ExpandablePlayButton(
                R.string.more,
                Duration.ZERO,
                Icons.Default.MoreVert,
                { moreOnClick.invoke() },
                Modifier.onFocusChanged(buttonOnFocusChanged),
            )
        }
    }
}

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
) {
    val isFocused = interactionSource.collectIsFocusedAsState().value
    Button(
        onClick = { onClick.invoke(resume) },
        modifier = modifier,
        contentPadding = DefaultButtonPadding,
        interactionSource = interactionSource,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.ifElse(mirrorIcon, Modifier.graphicsLayer { scaleX = -1f }),
        )
        AnimatedVisibility(isFocused) {
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
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
) {
    val isFocused = interactionSource.collectIsFocusedAsState().value
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 40.dp),
        contentPadding = DefaultButtonPadding,
        interactionSource = interactionSource,
    ) {
        Text(
            text = stringResource(iconStringRes),
            style = MaterialTheme.typography.titleSmall,
            color = iconColor,
            fontSize = 16.sp,
            fontFamily = FontAwesome,
            textAlign = TextAlign.Center,
            modifier = Modifier,
        )
        AnimatedVisibility(isFocused) {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
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
            modifier = Modifier,
        )
    }
}
