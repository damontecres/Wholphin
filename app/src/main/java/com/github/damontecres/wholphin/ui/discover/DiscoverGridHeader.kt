package com.github.damontecres.wholphin.ui.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.DiscoverFilter
import com.github.damontecres.wholphin.data.filter.DiscoverFilterBy
import com.github.damontecres.wholphin.data.filter.DiscoverSort
import com.github.damontecres.wholphin.data.filter.DiscoverSortAndDirection
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.ui.components.DiscoverFilterByButton
import com.github.damontecres.wholphin.ui.components.DiscoverSortByButton
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.GridTitle

@Composable
fun DiscoverGridHeader(
    showHeader: Boolean,
    showTitle: Boolean,
    title: String,
    sortAndDirection: DiscoverSortAndDirection,
    onSortChange: (DiscoverSortAndDirection) -> Unit,
    sortOptions: List<DiscoverSort>,
    getPossibleFilterValues: suspend (DiscoverFilterBy<*>) -> List<FilterValueOption>,
    onClickShowViewOptions: () -> Unit,
    modifier: Modifier = Modifier,
    currentFilter: DiscoverFilter = DiscoverFilter(),
    filterOptions: List<DiscoverFilterBy<*>> = listOf(),
    onFilterChange: (DiscoverFilter) -> Unit = {},
    onShowFilterDropdown: ((Boolean) -> Unit)? = null,
    filterButtonFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    AnimatedVisibility(
        showHeader,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (showTitle) {
                GridTitle(title)
            }
            val endPadding =
                16.dp // + if (sortAndDirection.sort == DiscoverSort.ALPHABETICAL) 24.dp else 0.dp
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .padding(start = 16.dp, end = endPadding)
                        .focusRestorer()
                        .fillMaxWidth(),
            ) {
                if (sortOptions.isNotEmpty() || filterOptions.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.focusRestorer(),
                    ) {
                        if (sortOptions.isNotEmpty()) {
                            DiscoverSortByButton(
                                sortOptions = sortOptions,
                                current = sortAndDirection,
                                onSortChange = onSortChange,
                                modifier = Modifier,
                            )
                        }
                        if (filterOptions.isNotEmpty()) {
                            DiscoverFilterByButton(
                                filterOptions = filterOptions,
                                current = currentFilter,
                                onFilterChange = onFilterChange,
                                getPossibleValues = getPossibleFilterValues,
                                modifier = Modifier.focusRequester(filterButtonFocusRequester),
                                onShow = onShowFilterDropdown,
                            )
                        }
                        ExpandableFaButton(
                            title = R.string.view_options,
                            iconStringRes = R.string.fa_sliders,
                            onClick = onClickShowViewOptions,
                            modifier = Modifier,
                        )
                    }
                }
            }
        }
    }
}
