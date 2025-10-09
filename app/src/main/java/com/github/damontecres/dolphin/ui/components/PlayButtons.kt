package com.github.damontecres.dolphin.ui.components

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.R
import com.github.damontecres.dolphin.ui.PreviewTvSpec
import com.github.damontecres.dolphin.ui.theme.DolphinTheme
import com.github.damontecres.dolphin.ui.tryRequestFocus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Composable
fun PlayButtons(
    resumePosition: Duration,
    playOnClick: (position: Duration) -> Unit,
    moreOnClick: () -> Unit,
    buttonOnFocusChanged: (FocusState) -> Unit,
    focusRequester: FocusRequester,
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
            item {
//                LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
                PlayButton(
                    R.string.resume,
                    resumePosition,
                    Icons.Default.PlayArrow,
                    playOnClick,
                    Modifier
                        .onFocusChanged(buttonOnFocusChanged)
                        .focusRequester(firstFocus)
                        .focusRequester(focusRequester),
                )
            }
            item {
                PlayButton(
                    R.string.restart,
                    Duration.ZERO,
                    Icons.Default.Refresh,
                    playOnClick,
                    Modifier
                        .onFocusChanged(buttonOnFocusChanged),
                    mirrorIcon = true,
                )
            }
        } else {
            item {
//                LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
                PlayButton(
                    R.string.play,
                    Duration.ZERO,
                    Icons.Default.PlayArrow,
                    playOnClick,
                    Modifier
                        .onFocusChanged(buttonOnFocusChanged)
                        .focusRequester(firstFocus)
                        .focusRequester(focusRequester),
                )
            }
        }

        // More button
        item {
            Button(
                onClick = moreOnClick,
                onLongClick = {},
                modifier =
                    Modifier
                        .onFocusChanged(buttonOnFocusChanged),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.more),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
fun ExpandablePlayButtons(
    resumePosition: Duration,
    watched: Boolean,
    playOnClick: (position: Duration) -> Unit,
    watchOnClick: () -> Unit,
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
                .focusProperties {
                    onEnter = {
                        firstFocus.tryRequestFocus()
                    }
                },
    ) {
        if (resumePosition > Duration.ZERO) {
            item {
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
            item {
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
            item {
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
        item {
            ExpandableFaButton(
                title = if (watched) R.string.mark_unwatched else R.string.mark_watched,
                iconStringRes = if (watched) R.string.fa_eye else R.string.fa_eye_slash,
                onClick = watchOnClick,
                modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
            )
        }

        // More button
        item {
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

@PreviewTvSpec
@Composable
private fun ExpandablePlayButtonsPreview() {
    DolphinTheme(true) {
        ExpandablePlayButtons(
            resumePosition = 10.seconds,
            watched = false,
            playOnClick = {},
            watchOnClick = {},
            moreOnClick = {},
            buttonOnFocusChanged = {},
            modifier = Modifier,
        )
    }
}
