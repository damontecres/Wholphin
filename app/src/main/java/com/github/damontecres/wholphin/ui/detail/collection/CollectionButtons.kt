package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.DefaultFilterOptions
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.ui.components.DeleteButton
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.FilterByButton
import com.github.damontecres.wholphin.ui.components.SortByButton
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import kotlin.time.Duration

@Composable
fun CollectionButtons(
    state: CollectionState,
    onSortChange: (SortAndDirection) -> Unit,
    onClickPlayAll: (Boolean) -> Unit,
    onFilterChange: (GetItemsFilter) -> Unit,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    onClickViewOptions: () -> Unit,
    favoriteOnClick: () -> Unit,
    deleteOnClick: () -> Unit,
    canDelete: Boolean,
    moreOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortOptions = MovieSortOptions
    val filterOptions = DefaultFilterOptions
    val firstFocus = remember { FocusRequester() }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(8.dp),
        modifier =
            modifier
                .focusGroup()
                .focusRestorer(firstFocus),
    ) {
        if (state.items.isNotEmpty()) {
            item {
                ExpandablePlayButton(
                    title = R.string.play,
                    resume = Duration.ZERO,
                    icon = Icons.Default.PlayArrow,
                    onClick = { onClickPlayAll.invoke(false) },
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }
            item {
                ExpandableFaButton(
                    title = R.string.shuffle,
                    iconStringRes = R.string.fa_shuffle,
                    onClick = { onClickPlayAll.invoke(true) },
                )
            }
        }
        item("favorite") {
            val favorite = remember(state.collection) { state.collection?.favorite == true }
            ExpandableFaButton(
                title = if (favorite) R.string.remove_favorite else R.string.add_favorite,
                iconStringRes = R.string.fa_heart,
                onClick = favoriteOnClick,
                iconColor = if (favorite) Color.Red else Color.Unspecified,
                modifier = Modifier,
            )
        }
        if (canDelete) {
            item("delete") {
                DeleteButton(
                    onClick = deleteOnClick,
                    modifier =
                    Modifier,
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
                modifier = Modifier,
            )
        }

        item {
            SortByButton(
                sortOptions = sortOptions,
                current = state.sortAndDirection,
                onSortChange = onSortChange,
                modifier = Modifier,
            )
        }
        item {
            FilterByButton(
                filterOptions = filterOptions,
                current = state.itemFilter,
                onFilterChange = onFilterChange,
                getPossibleValues = getPossibleFilterValues,
                modifier = Modifier,
            )
        }

        item {
            ExpandableFaButton(
                title = R.string.view_options,
                iconStringRes = R.string.fa_sliders,
                onClick = onClickViewOptions,
                modifier = Modifier,
            )
        }
    }
}
