package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
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
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.TrailerButton
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlin.time.Duration

@Composable
fun ExpandableDiscoverButtons(
    canRequest: Boolean,
    canCancel: Boolean,
    availability: SeerrAvailability,
    trailers: List<Trailer>?,
    requestOnClick: () -> Unit,
    cancelOnClick: () -> Unit,
    goToOnClick: () -> Unit,
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
        val text =
            when (availability) {
                SeerrAvailability.UNKNOWN -> R.string.request

                SeerrAvailability.PENDING,
                SeerrAvailability.PROCESSING,
                -> R.string.pending

                SeerrAvailability.PARTIALLY_AVAILABLE,
                SeerrAvailability.AVAILABLE,
                -> R.string.go_to

                SeerrAvailability.DELETED -> R.string.delete // TODO
            }
        val icon =
            when (availability) {
                SeerrAvailability.UNKNOWN -> R.string.fa_download

                SeerrAvailability.PENDING,
                SeerrAvailability.PROCESSING,
                -> R.string.fa_clock

                SeerrAvailability.PARTIALLY_AVAILABLE,
                SeerrAvailability.AVAILABLE,
                -> R.string.fa_play

                SeerrAvailability.DELETED -> R.string.fa_video // TODO
            }
        item("first") {
            ExpandableFaButton(
                title = text,
                iconStringRes = icon,
                enabled = if (availability == SeerrAvailability.UNKNOWN) canRequest else true,
                onClick = {
                    when (availability) {
                        SeerrAvailability.UNKNOWN -> {
                            requestOnClick.invoke()
                        }

                        SeerrAvailability.PENDING,
                        SeerrAvailability.PROCESSING,
                        -> {
                            // TODO?
                        }

                        SeerrAvailability.PARTIALLY_AVAILABLE,
                        SeerrAvailability.AVAILABLE,
                        -> {
                            goToOnClick.invoke()
                        }

                        SeerrAvailability.DELETED -> {
                            // TODO
                        }
                    }
                },
                modifier =
                    Modifier
                        .focusRequester(firstFocus)
                        .onFocusChanged(buttonOnFocusChanged),
            )
        }

        if (canCancel) {
            item("cancel") {
                ExpandablePlayButton(
                    title = R.string.cancel,
                    icon = Icons.Default.Delete,
                    onClick = {
                        firstFocus.tryRequestFocus()
                        cancelOnClick.invoke()
                    },
                    resume = Duration.ZERO,
                    enabled = canCancel,
                    modifier =
                        Modifier
                            .onFocusChanged(buttonOnFocusChanged),
                )
            }
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
