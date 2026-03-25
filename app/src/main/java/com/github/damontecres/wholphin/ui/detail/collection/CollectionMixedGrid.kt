package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.playback.scale
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import org.jellyfin.sdk.model.api.ItemSortBy

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionMixedGrid(
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
    letterPosition: suspend (Char) -> Int,
    onClickViewOptions: () -> Unit,
    modifier: Modifier = Modifier,
    onFocusPosition: (RowColumn) -> Unit = {},
) {
    val gridFocusRequester = remember { FocusRequester() }

    Box(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
            val density = LocalDensity.current

            val cardViewOptions = state.viewOptions.cardViewOptions
            CardGrid(
                pager = state.items,
                onClickItem = { index: Int, item: BaseItem -> onClickItem.invoke(RowColumn(0, index), item) },
                onLongClickItem = { index: Int, item: BaseItem -> onLongClickItem.invoke(RowColumn(0, index), item) },
                onClickPlay = { index: Int, item: BaseItem -> onClickPlay.invoke(RowColumn(0, index), item) },
                letterPosition = letterPosition,
                gridFocusRequester = gridFocusRequester,
                showJumpButtons = false, // TODO add preference
                showLetterButtons = state.sortAndDirection.sort == ItemSortBy.SORT_NAME,
                modifier =
                    Modifier
                        .fillMaxSize(),
                initialPosition = 0,
                positionCallback = { _, newPosition ->
                    onFocusPosition.invoke(RowColumn(0, newPosition))
                },
                cardContent = { item, onClick, onLongClick, mod ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = cardViewOptions.contentScale.scale,
                        imageAspectRatio = cardViewOptions.aspectRatio.ratio,
                        imageType = cardViewOptions.imageType,
                        showTitle = cardViewOptions.showTitles,
                        modifier = mod,
                    )
                },
                columns = cardViewOptions.columns,
                spacing = cardViewOptions.spacing.dp,
                bringIntoViewSpec =
                    remember(cardViewOptions) {
                        val spacingPx = with(density) { cardViewOptions.spacing.dp.toPx() }
                        if (cardViewOptions.showDetails) {
                            ScrollToTopBringIntoViewSpec(spacingPx)
                        } else {
                            defaultBringIntoViewSpec
                        }
                    },
            )
        }
    }
}
