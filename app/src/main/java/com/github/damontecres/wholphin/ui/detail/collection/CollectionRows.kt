package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.RequestOrRestoreFocus
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.FilterByButton
import com.github.damontecres.wholphin.ui.components.SortByButton
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.main.HomePageContent
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.rememberPosition
import org.jellyfin.sdk.model.api.ItemSortBy
import kotlin.time.Duration

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionRows(
    preferences: UserPreferences,
    state: CollectionState,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onSortChange: (SortAndDirection) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    onClickPlayAll: (Boolean) -> Unit,
    onChangeBackdrop: (BaseItem) -> Unit,
    onFilterChange: (GetItemsFilter) -> Unit,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    onClickViewOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showHeader by rememberSaveable { mutableStateOf(true) }
    val headerRowFocusRequester = remember { FocusRequester() }

    val gridFocusRequester = remember { FocusRequester() }
    if (state.items.isNotEmpty()) {
        RequestOrRestoreFocus(gridFocusRequester)
    }

    var position by rememberPosition(0, 0)

    // TODO enable sort & filter?
    val sortOptions = emptyList<ItemSortBy>() // MovieSortOptions
    val filterOptions = emptyList<ItemFilterBy<*>>() // DefaultFilterOptions

    Box(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimatedVisibility(
                showHeader,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val endPadding =
                        16.dp + if (state.sortAndDirection.sort == ItemSortBy.SORT_NAME) 24.dp else 0.dp
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .padding(start = 16.dp, end = endPadding)
                                .fillMaxWidth()
                                .focusRequester(headerRowFocusRequester),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier,
                        ) {
                            if (sortOptions.isNotEmpty()) {
                                SortByButton(
                                    sortOptions = sortOptions,
                                    current = state.sortAndDirection,
                                    onSortChange = onSortChange,
                                    modifier = Modifier,
                                )
                            }
                            if (filterOptions.isNotEmpty()) {
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

                        if (state.items.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier,
                            ) {
                                ExpandablePlayButton(
                                    title = R.string.play,
                                    resume = Duration.ZERO,
                                    icon = Icons.Default.PlayArrow,
                                    onClick = { onClickPlayAll.invoke(false) },
                                )
                                ExpandableFaButton(
                                    title = R.string.shuffle,
                                    iconStringRes = R.string.fa_shuffle,
                                    onClick = { onClickPlayAll.invoke(true) },
                                )
                            }
                        }
                    }
                }
            }
            val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
            val density = LocalDensity.current
            val homeRows =
                remember(state.separateItems) {
                    state.separateItems.values.toList()
                }
            HomePageContent(
                homeRows = homeRows,
                position = position,
                onFocusPosition = { newPosition ->
                    showHeader = newPosition.row <= 0
                    position = newPosition
                },
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                onClickPlay = onClickPlay,
                showClock = false,
                onUpdateBackdrop = onChangeBackdrop,
                headerComposable = { focusedItem ->
                    AnimatedVisibility(state.viewOptions.cardViewOptions.showDetails) {
                        HomePageHeader(
                            item = focusedItem,
                            modifier =
                                Modifier
                                    .padding(top = 8.dp, bottom = 48.dp, start = 8.dp)
                                    .fillMaxHeight(.33f),
                        )
                    }
                },
            )
        }
    }
}
