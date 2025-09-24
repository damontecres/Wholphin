package com.github.damontecres.dolphin.ui.setup

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.JellyfinUser

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
    LazyColumn(modifier = modifier) {
        items(users) { user ->
            ListItem(
                enabled = true,
                selected = false,
                headlineContent = { Text(text = user.name ?: user.id) },
                supportingContent = { },
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
                    // TODO dialog to remove user
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
                supportingContent = { },
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
                supportingContent = { },
                onClick = { onSwitchServer.invoke() },
                modifier = Modifier,
            )
        }
    }
}
