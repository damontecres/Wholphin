package com.github.damontecres.wholphin.ui.preferences

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.theme.WholphinTheme

data class NavDrawerPin(
    val id: String,
    val title: String,
    val pinned: Boolean,
    val item: NavDrawerItem,
) {
    companion object {
        fun create(
            context: Context,
            items: Map<NavDrawerItem, Boolean>,
        ) {
            items.map { (item, pinned) ->
                NavDrawerPin(item.id, item.name(context), pinned, item)
            }
        }
    }
}

enum class MoveDirection {
    UP,
    DOWN,
}

private fun <T> List<T>.move(
    direction: MoveDirection,
    index: Int,
): List<T> =
    toMutableList().apply {
        if (direction == MoveDirection.DOWN) {
            val down = this[index]
            val up = this[index + 1]
            set(index, up)
            set(index + 1, down)
        } else {
            val up = this[index]
            val down = this[index - 1]
            set(index - 1, up)
            set(index, down)
        }
    }

@Composable
fun NavDrawerPreference(
    title: String,
    summary: String?,
    items: List<NavDrawerPin>,
    onSave: (List<NavDrawerPin>) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var showDialog by remember { mutableStateOf(false) }
    ClickPreference(
        title = title,
        summary = summary,
        onClick = { showDialog = true },
        interactionSource = interactionSource,
        modifier = modifier,
    )
    if (showDialog) {
        NavDrawerPreferenceDialog(
            items = items,
            onDismissRequest = { showDialog = false },
            onClick = { index ->
                val newItems =
                    items.toMutableList().apply {
                        set(index, items[index].let { it.copy(pinned = !it.pinned) })
                    }
                onSave.invoke(newItems)
            },
            onMoveUp = { index ->
                onSave(items.move(MoveDirection.UP, index))
            },
            onMoveDown = { index ->
                onSave(items.move(MoveDirection.DOWN, index))
            },
        )
    }
}

@Composable
fun NavDrawerPreferenceDialog(
    items: List<NavDrawerPin>,
    onDismissRequest: () -> Unit,
    onClick: (Int) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.nav_drawer_pins),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    NavDrawerPreferenceListItem(
                        title = item.title,
                        pinned = item.pinned,
                        moveUpAllowed = index > 0,
                        moveDownAllowed = index < items.lastIndex,
                        onClick = { onClick.invoke(index) },
                        onMoveUp = { onMoveUp.invoke(index) },
                        onMoveDown = { onMoveDown.invoke(index) },
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

@Composable
fun NavDrawerPreferenceListItem(
    title: String,
    pinned: Boolean,
    moveUpAllowed: Boolean,
    moveDownAllowed: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp, max = 88.dp),
        ) {
            ListItem(
                selected = false,
                headlineContent = {
                    Text(
                        text = title,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = pinned,
                        onCheckedChange = {
                            onClick.invoke()
                        },
                    )
                },
                onClick = onClick,
                modifier = Modifier.weight(1f),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.wrapContentWidth(),
            ) {
                MoveButton(R.string.fa_caret_up, moveUpAllowed, onMoveUp)
                MoveButton(R.string.fa_caret_down, moveDownAllowed, onMoveDown)
            }
        }
    }
}

@Composable
private fun MoveButton(
    @StringRes icon: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) = Button(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.size(32.dp),
) {
    Text(
        text = stringResource(icon),
        fontSize = 16.sp,
        fontFamily = FontAwesome,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@PreviewTvSpec
@Composable
fun NavDrawerPreferenceListItemPreview() {
    WholphinTheme {
        NavDrawerPreferenceListItem(
            title = "Movies",
            pinned = true,
            moveUpAllowed = true,
            moveDownAllowed = true,
            onClick = {},
            onMoveUp = {},
            onMoveDown = { },
            modifier = Modifier.width(360.dp),
        )
    }
}
