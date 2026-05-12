package com.github.damontecres.wholphin.ui.detail.music

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.DeleteButton
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import kotlin.time.Duration

@Composable
fun MusicExpandableButtons(
    title: String,
    actions: MusicButtonActions,
    favorite: Boolean,
    canDelete: Boolean,
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
        item("play") {
            ExpandablePlayButton(
                title = R.string.play,
                resume = Duration.ZERO,
                icon = Icons.Default.PlayArrow,
                onClick = { actions.onClickPlay.invoke(false) },
                modifier =
                    Modifier
                        .focusRequester(firstFocus)
                        .onFocusChanged(buttonOnFocusChanged),
            )
        }
        item("shuffle") {
            ExpandableFaButton(
                title = R.string.shuffle,
                iconStringRes = R.string.fa_shuffle,
                onClick = { actions.onClickPlay.invoke(true) },
                modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
            )
        }
        item("instant_mix") {
            ExpandableFaButton(
                title = R.string.instant_mix,
                iconStringRes = R.string.fa_compass,
                onClick = actions.onClickInstantMix,
                modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
            )
        }
        item("favorite") {
            ExpandableFaButton(
                title = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                iconStringRes = R.string.fa_heart,
                onClick = actions.onClickFavorite,
                iconColor = if (favorite) Color.Red else Color.Unspecified,
                modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
            )
        }
        if (canDelete) {
            item("delete") {
                DeleteButton(
                    title = title,
                    onConfirmDelete = actions.onConfirmDelete,
                    modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
                )
            }
        }
        item("more") {
            ExpandablePlayButton(
                title = R.string.more,
                resume = Duration.ZERO,
                icon = Icons.Default.MoreVert,
                onClick = { actions.onClickMore.invoke() },
                modifier = Modifier.onFocusChanged(buttonOnFocusChanged),
            )
        }
    }
}

data class MusicButtonActions(
    val onClickPlay: (shuffle: Boolean) -> Unit,
    val onClickInstantMix: () -> Unit,
    val onClickFavorite: () -> Unit,
    val onClickMore: () -> Unit,
    val onConfirmDelete: () -> Unit,
)
