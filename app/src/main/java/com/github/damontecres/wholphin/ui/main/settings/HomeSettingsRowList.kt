package com.github.damontecres.wholphin.ui.main.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.HomeRowConfigDisplay
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.tryRequestFocus

enum class MoveDirection {
    UP,
    DOWN,
}

@Composable
fun HomeSettingsRowList(
    state: HomePageSettingsState,
    onClick: (Int, HomeRowConfigDisplay) -> Unit,
    onClickSaveLocal: () -> Unit,
    onClickAdd: () -> Unit,
    onClickMove: (MoveDirection, Int) -> Unit,
    onClickDelete: (Int) -> Unit,
    modifier: Modifier,
    firstFocus: FocusRequester = remember { FocusRequester() },
) {
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    Column(modifier = modifier) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                modifier
                    .fillMaxHeight()
                    .focusRestorer(firstFocus),
        ) {
            item {
                ListItem(
                    selected = false,
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.add_row),
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                        )
                    },
                    onClick = onClickAdd,
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }
            item {
                ListItem(
                    selected = false,
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.save),
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Create,
                            contentDescription = null,
                        )
                    },
                    onClick = onClickSaveLocal,
                    modifier = Modifier,
                )
            }
            item {
                HorizontalDivider()
            }
            itemsIndexed(state.rows, key = { _, row -> row.id }) { index, row ->
                HomeRowConfigContent(
                    config = row,
                    moveUpAllowed = index > 0,
                    moveDownAllowed = index != state.rows.lastIndex,
                    deleteAllowed = state.rows.size > 1,
                    onClickMove = { onClickMove.invoke(it, index) },
                    onClickDelete = { onClickDelete.invoke(index) },
                    onClick = { onClick.invoke(index, row) },
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun HomeRowConfigContent(
    config: HomeRowConfigDisplay,
    moveUpAllowed: Boolean,
    moveDownAllowed: Boolean,
    deleteAllowed: Boolean,
    onClick: () -> Unit,
    onClickMove: (MoveDirection) -> Unit,
    onClickDelete: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp, max = 88.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        shape = RoundedCornerShape(8.dp),
                    ),
        ) {
            ListItem(
                selected = false,
                headlineContent = {
                    Text(
                        text = config.title,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                        Modifier,
                    )
                },
                onClick = onClick,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.wrapContentWidth(),
            ) {
                Button(
                    onClick = { onClickMove.invoke(MoveDirection.UP) },
                    enabled = moveUpAllowed,
                ) {
                    Text(
                        text = stringResource(R.string.fa_caret_up),
                        fontFamily = FontAwesome,
                    )
                }
                Button(
                    onClick = { onClickMove.invoke(MoveDirection.DOWN) },
                    enabled = moveDownAllowed,
                ) {
                    Text(
                        text = stringResource(R.string.fa_caret_down),
                        fontFamily = FontAwesome,
                    )
                }
                Button(
                    onClick = onClickDelete,
                    enabled = deleteAllowed,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "delete",
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}
