package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.util.RowLoadingState

@Composable
fun LoadingRow(
    title: String,
    state: RowLoadingState,
    rowIndex: Int,
    position: RowColumn,
    focusRequester: FocusRequester,
    onClickItem: (Int, BaseItem) -> Unit,
    onClickPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    showIfEmpty: Boolean = true,
    horizontalPadding: Dp = 16.dp,
    cardContent: @Composable (
        index: Int,
        item: BaseItem?,
        modifier: Modifier,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
    ) -> Unit = @Composable { index, item, mod, onClick, onLongClick ->
        SeasonCard(
            item = item,
            onClick = {
                onClickPosition.invoke(RowColumn(rowIndex, index))
                onClick.invoke()
            },
            onLongClick = onLongClick,
            imageHeight = Cards.height2x3,
            modifier =
                mod
                    .ifElse(
                        position.row == rowIndex && position.column == index,
                        Modifier.focusRequester(focusRequester),
                    ),
        )
    },
) {
    when (val r = state) {
        is RowLoadingState.Error ->
            LoadingRowPlaceholder(
                title = title,
                message = r.localizedMessage,
                messageColor = MaterialTheme.colorScheme.error,
                modifier = Modifier,
            )

        RowLoadingState.Pending,
        RowLoadingState.Loading,
        ->
            LoadingRowPlaceholder(
                title = title,
                message = stringResource(R.string.loading),
                modifier = modifier,
            )

        is RowLoadingState.Success -> {
            if (r.items.isNotEmpty()) {
                ItemRow(
                    title = title,
                    items = r.items,
                    onClickItem = onClickItem,
                    onLongClickItem = { _, _ -> },
                    modifier = modifier,
                    cardContent = cardContent,
                    horizontalPadding = horizontalPadding,
                )
            } else if (showIfEmpty) {
                LoadingRowPlaceholder(
                    title = title,
                    message = stringResource(R.string.no_results),
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
fun LoadingRowPlaceholder(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    messageColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.padding(bottom = 32.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = messageColor,
        )
    }
}
