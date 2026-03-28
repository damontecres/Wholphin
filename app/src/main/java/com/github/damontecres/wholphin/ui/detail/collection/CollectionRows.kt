package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.main.HomePageContent
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
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    onFocusPosition: (RowColumn) -> Unit = {},
) {
    var position by rememberPosition(0, 0)

    Box(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp),
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
            HomePageContent(
                homeRows = homeRows,
                position = position,
                onFocusPosition = { newPosition ->
                    position = newPosition
                    onFocusPosition.invoke(newPosition)
                },
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                onClickPlay = onClickPlay,
                showClock = false,
                onUpdateBackdrop = {},
                headerComposable = {},
                takeFocus = false,
                modifier = Modifier,
            )
        }
    }
}
