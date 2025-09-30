package com.github.damontecres.dolphin.ui.setup

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.JellyfinServer
import com.github.damontecres.dolphin.ui.components.CircularProgress

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
    LazyColumn(modifier = modifier) {
        items(servers) { server ->
            val status = connectionStatus[server.id] ?: ServerConnectionStatus.Pending
            ListItem(
                enabled = status == ServerConnectionStatus.Success,
                selected = false,
                headlineContent = { Text(text = server.name ?: server.id) },
                supportingContent = { Text(text = server.url) },
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
                supportingContent = { },
                onClick = { onAddServer.invoke() },
                modifier = Modifier,
            )
        }
    }
}
