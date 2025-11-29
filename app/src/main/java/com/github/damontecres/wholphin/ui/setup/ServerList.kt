package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.model.api.PublicSystemInfo
import java.util.UUID

sealed interface ServerConnectionStatus {
    data class Success(
        val systemInfo: PublicSystemInfo,
    ) : ServerConnectionStatus

    object Pending : ServerConnectionStatus

    data class Error(
        val message: String?,
    ) : ServerConnectionStatus
}

/**
 * Display a list of servers plus option to add a new one
 */
@Composable
fun ServerList(
    servers: List<JellyfinServer>,
    connectionStatus: Map<UUID, ServerConnectionStatus>,
    onSwitchServer: (JellyfinServer) -> Unit,
    onTestServer: (JellyfinServer) -> Unit,
    onAddServer: () -> Unit,
    onRemoveServer: (JellyfinServer) -> Unit,
    allowAdd: Boolean,
    allowDelete: Boolean,
    modifier: Modifier = Modifier,
) {
    var showDeleteDialog by remember { mutableStateOf<JellyfinServer?>(null) }

    LazyColumn(modifier = modifier) {
        items(servers) { server ->
            val status = connectionStatus[server.id] ?: ServerConnectionStatus.Pending
            ListItem(
                enabled = true,
                selected = false,
                headlineContent = { Text(text = server.name?.ifBlank { null } ?: server.url) },
                supportingContent = { if (server.name.isNotNullOrBlank()) Text(text = server.url) },
                leadingContent = {
                    when (status) {
                        is ServerConnectionStatus.Success -> {}
                        ServerConnectionStatus.Pending -> {
                            CircularProgress(
                                Modifier.size(IconButtonDefaults.MediumIconSize),
                            )
                        }
                        is ServerConnectionStatus.Error -> {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = status.message,
                                tint = MaterialTheme.colorScheme.errorContainer,
                            )
                        }
                    }
                },
                onClick = {
                    when (status) {
                        is ServerConnectionStatus.Success -> onSwitchServer.invoke(server)
                        ServerConnectionStatus.Pending -> {}
                        is ServerConnectionStatus.Error -> onTestServer.invoke(server)
                    }
                },
                onLongClick = {
                    if (allowDelete) {
                        showDeleteDialog = server
                    }
                },
                modifier = Modifier,
            )
        }
        if (allowAdd) {
            item {
                HorizontalDivider()
                ListItem(
                    enabled = true,
                    selected = false,
                    headlineContent = { Text(text = stringResource(R.string.add_server)) },
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
    }
    showDeleteDialog?.let { server ->
        DialogPopup(
            showDialog = allowDelete,
            title = server.name ?: server.url,
            dialogItems =
                listOf(
                    DialogItem(
                        stringResource(R.string.switch_servers),
                        R.string.fa_arrow_left_arrow_right,
                    ) {
                        onSwitchServer.invoke(server)
                    },
                    DialogItem(
                        stringResource(R.string.delete),
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
