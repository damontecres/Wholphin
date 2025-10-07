package com.github.damontecres.dolphin.ui.setup

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.R
import com.github.damontecres.dolphin.data.JellyfinServer
import com.github.damontecres.dolphin.ui.components.CircularProgress
import com.github.damontecres.dolphin.ui.components.DialogItem
import com.github.damontecres.dolphin.ui.components.DialogPopup
import com.github.damontecres.dolphin.ui.isNotNullOrBlank

sealed interface ServerConnectionStatus {
    object Success : ServerConnectionStatus

    object Pending : ServerConnectionStatus

    data class Error(
        val message: String?,
    ) : ServerConnectionStatus
}

@Composable
fun ServerList(
    servers: List<JellyfinServer>,
    connectionStatus: Map<String, ServerConnectionStatus>,
    onSwitchServer: (JellyfinServer) -> Unit,
    onAddServer: () -> Unit,
    onRemoveServer: (JellyfinServer) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf<JellyfinServer?>(null) }

    LazyColumn(modifier = modifier) {
        items(servers) { server ->
            val status = connectionStatus[server.id] ?: ServerConnectionStatus.Pending
            ListItem(
                enabled = status == ServerConnectionStatus.Success,
                selected = false,
                headlineContent = { Text(text = server.name?.ifBlank { null } ?: server.url) },
                supportingContent = { if (server.name.isNotNullOrBlank()) Text(text = server.url) },
                leadingContent = {
                    when (status) {
                        ServerConnectionStatus.Success -> {}
                        ServerConnectionStatus.Pending -> {
                            CircularProgress()
                        }
                        is ServerConnectionStatus.Error -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = status.message,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                onClick = { onSwitchServer.invoke(server) },
                onLongClick = {
                    // TODO dialog to remove server
                },
                modifier = Modifier,
            )
        }
        item {
            HorizontalDivider()
            ListItem(
                enabled = true,
                selected = false,
                headlineContent = { Text(text = "Add Server") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        tint = Color.Green.copy(alpha = .8f),
                        contentDescription = null,
                    )
                },
                onClick = { onAddServer.invoke() },
                modifier = Modifier,
            )
        }
    }
    showDeleteDialog?.let { server ->
        DialogPopup(
            showDialog = true,
            title = server.name ?: server.url,
            dialogItems =
                listOf(
                    DialogItem("Switch", R.string.fa_arrow_left_arrow_right) {
                        onSwitchServer.invoke(server)
                    },
                    DialogItem(
                        "Delete",
                        Icons.Default.Delete,
                        Color.Red.copy(alpha = .8f),
                    ) {
                        onRemoveServer.invoke(server)
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
