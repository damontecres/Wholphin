package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import kotlin.time.Duration

@Composable
fun ExpandableDiscoverButtons(
    availability: SeerrAvailability,
    requestOnClick: () -> Unit,
    cancelOnClick: () -> Unit,
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
        if (availability == SeerrAvailability.UNKNOWN) {
            item("first") {
                ExpandableFaButton(
                    title = R.string.request,
                    iconStringRes = R.string.fa_download,
                    onClick = requestOnClick,
                    modifier =
                        Modifier
                            .focusRequester(firstFocus)
                            .onFocusChanged(buttonOnFocusChanged),
                )
            }
        } else if (availability == SeerrAvailability.PENDING || availability == SeerrAvailability.PROCESSING) {
            item("first") {
                ExpandableFaButton(
                    title = R.string.pending,
                    iconStringRes = R.string.fa_clock,
                    onClick = {
                        // TODO show request dialog?
                    },
                    modifier =
                        Modifier
                            .focusRequester(firstFocus)
                            .onFocusChanged(buttonOnFocusChanged),
                )
            }
            item("cancel") {
                ExpandablePlayButton(
                    title = R.string.cancel,
                    icon = Icons.Default.Delete,
                    onClick = { cancelOnClick.invoke() },
                    resume = Duration.ZERO,
                    modifier =
                        Modifier
                            .onFocusChanged(buttonOnFocusChanged),
                )
            }
        } else if (availability == SeerrAvailability.PARTIALLY_AVAILABLE || availability == SeerrAvailability.AVAILABLE) {
            item("first") {
                ExpandablePlayButton(
                    title = R.string.go_to,
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        // TODO go to the item
                    },
                    resume = Duration.ZERO,
                    modifier =
                        Modifier
                            .focusRequester(firstFocus)
                            .onFocusChanged(buttonOnFocusChanged),
                )
            }
        }

        // More button
        item("more") {
            ExpandablePlayButton(
                R.string.more,
                Duration.ZERO,
                Icons.Default.MoreVert,
                { moreOnClick.invoke() },
                Modifier
                    .onFocusChanged(buttonOnFocusChanged),
            )
        }
    }
}
