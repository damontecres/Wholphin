package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.RequestOrRestoreFocus
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.main.HomePageContent
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import org.jellyfin.sdk.model.api.BaseItemKind

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

    val gridFocusRequester = remember { FocusRequester() }
    if (state.items.isNotEmpty()) {
        RequestOrRestoreFocus(gridFocusRequester)
    }

    var position by rememberPosition(0, 0)

    Box(modifier = modifier) {
        AnimatedVisibility(
            showHeader,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                ExpandableFaButton(
                    title = R.string.view_options,
                    iconStringRes = R.string.fa_sliders,
                    onClick = onClickViewOptions,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val cardViewOptions = state.viewOptions.cardViewOptions
            val homeRows =
                remember(state.separateItems, cardViewOptions) {
                    state.separateItems.map { (type, row) ->
                        if (row is HomeRowLoadingState.Success) {
                            // TODO not great to do this in the UI
                            val viewOptions =
                                if (type == BaseItemKind.EPISODE) {
                                    HomeRowViewOptions(
                                        heightDp = Cards.HEIGHT_EPISODE,
                                        episodeAspectRatio = AspectRatio.WIDE,
                                        showTitles = cardViewOptions.showTitles,
                                        useSeries = false,
                                    )
                                } else {
                                    HomeRowViewOptions(
                                        showTitles = cardViewOptions.showTitles,
                                    )
                                }
                            row.copy(viewOptions = viewOptions)
                        } else {
                            row
                        }
                    }
                }
            val headerBottomPadding by animateDpAsState(
                targetValue = if (state.viewOptions.separateTypes && cardViewOptions.showTitles) 0.dp else 48.dp,
            )
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
                                    .padding(top = 8.dp, bottom = headerBottomPadding, start = 8.dp)
                                    .fillMaxHeight(.33f),
                        )
                    }
                },
            )
        }
    }
}
