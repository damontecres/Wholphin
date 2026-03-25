package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.DefaultFilterOptions
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.FilterByButton
import com.github.damontecres.wholphin.ui.components.SortByButton
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import org.jellyfin.sdk.model.api.ItemSortBy
import kotlin.time.Duration

@Composable
fun CollectionButtons(
    state: CollectionState,
    onSortChange: (SortAndDirection) -> Unit,
    onClickPlayAll: (Boolean) -> Unit,
    onFilterChange: (GetItemsFilter) -> Unit,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    onClickViewOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortOptions = MovieSortOptions
    val filterOptions = DefaultFilterOptions
    val endPadding =
        16.dp + if (state.sortAndDirection.sort == ItemSortBy.SORT_NAME) 24.dp else 0.dp
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .padding(start = 16.dp, end = endPadding)
                .fillMaxWidth(),
    ) {
        if (state.items.isNotEmpty()) {
            ExpandablePlayButton(
                title = R.string.play,
                resume = Duration.ZERO,
                icon = Icons.Default.PlayArrow,
                onClick = { onClickPlayAll.invoke(false) },
                modifier = Modifier,
            )
            ExpandableFaButton(
                title = R.string.shuffle,
                iconStringRes = R.string.fa_shuffle,
                onClick = { onClickPlayAll.invoke(true) },
            )
        }
        if (!state.viewOptions.separateTypes) {
            SortByButton(
                sortOptions = sortOptions,
                current = state.sortAndDirection,
                onSortChange = onSortChange,
                modifier = Modifier,
            )
            FilterByButton(
                filterOptions = filterOptions,
                current = state.itemFilter,
                onFilterChange = onFilterChange,
                getPossibleValues = getPossibleFilterValues,
                modifier = Modifier,
            )
        }
        ExpandableFaButton(
            title = R.string.view_options,
            iconStringRes = R.string.fa_sliders,
            onClick = onClickViewOptions,
            modifier = Modifier,
        )
    }
}
