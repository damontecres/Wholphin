package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.JellyfinUser
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup

/**
 * Display a list of users plus option to add a new one or switch servers
 */
@Composable
fun UserList(
    users: List<JellyfinUser>,
    currentUser: JellyfinUser?,
    onSwitchUser: (JellyfinUser) -> Unit,
    onAddUser: () -> Unit,
    onRemoveUser: (JellyfinUser) -> Unit,
    onSwitchServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf<JellyfinUser?>(null) }

    LazyColumn(modifier = modifier) {
        items(users) { user ->
            ListItem(
                enabled = true,
                selected = user == currentUser,
                headlineContent = { Text(text = user.name ?: user.id) },
                leadingContent = {
                    if (user.id == currentUser?.id) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "current user",
                        )
                    }
                },
                onClick = { onSwitchUser.invoke(user) },
                onLongClick = {
                    showDeleteDialog = user
                },
                modifier = Modifier,
            )
        }
        item {
            HorizontalDivider()
            ListItem(
                enabled = true,
                selected = false,
                headlineContent = { Text(text = "Add User") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        tint = Color.Green.copy(alpha = .8f),
                        contentDescription = null,
                    )
                },
                onClick = { onAddUser.invoke() },
                modifier = Modifier,
            )
        }
        item {
            HorizontalDivider()
            ListItem(
                enabled = true,
                selected = false,
                headlineContent = { Text(text = "Switch servers") },
                leadingContent = {
                    Text(
                        text = stringResource(R.string.fa_arrow_left_arrow_right),
                        fontFamily = FontAwesome,
                    )
                },
                onClick = { onSwitchServer.invoke() },
                modifier = Modifier,
            )
        }
    }
    showDeleteDialog?.let { user ->
        DialogPopup(
            showDialog = true,
            title = user.name ?: user.id,
            dialogItems =
                listOf(
                    DialogItem("Switch", R.string.fa_arrow_left_arrow_right) {
                        onSwitchUser.invoke(user)
                    },
                    DialogItem(
                        "Delete",
                        Icons.Default.Delete,
                        Color.Red.copy(alpha = .8f),
                    ) {
                        onRemoveUser.invoke(user)
                    },
                ),
            onDismissRequest = { showDeleteDialog = null },
            dismissOnClick = true,
            waitToLoad = true,
            properties = DialogProperties(),
            elevation = 5.dp,
        )
    }
}
