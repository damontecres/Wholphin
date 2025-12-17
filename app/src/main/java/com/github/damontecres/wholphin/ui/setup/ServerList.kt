package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.ui.Cards
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
                        is ServerConnectionStatus.Success -> {
                            onSwitchServer.invoke(server)
                        }

                        ServerConnectionStatus.Pending -> {}

                        is ServerConnectionStatus.Error -> {
                            onTestServer.invoke(server)
                        }
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

/**
 * Generate a consistent color for a UUID
 */
@Composable
fun rememberIdColor(id: UUID): Color =
    remember(id) {
        // Generate a color based on the server ID hash, fallback to URL hash
        val hash = id.hashCode()
        val hue = (hash % 360).toFloat()
        val saturation = 0.6f + ((hash / 360) % 40).toFloat() / 100f // 0.6-1.0
        val brightness = 0.4f + ((hash / 14400) % 30).toFloat() / 100f // 0.4-0.7 (darker colors)

        // Convert HSV to RGB
        val c = brightness * saturation
        val x = c * (1 - kotlin.math.abs((hue / 60f) % 2f - 1))
        val m = brightness - c

        val (r, g, b) =
            when {
                hue < 60 -> Triple(c, x, 0f)
                hue < 120 -> Triple(x, c, 0f)
                hue < 180 -> Triple(0f, c, x)
                hue < 240 -> Triple(0f, x, c)
                hue < 300 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }

        Color(
            red = (r + m).coerceIn(0f, 1f),
            green = (g + m).coerceIn(0f, 1f),
            blue = (b + m).coerceIn(0f, 1f),
        )
    }

/**
 * Server icon card component - displays a circular card with server name/letter
 */
@Composable
fun ServerIconCard(
    server: JellyfinServer,
    connectionStatus: ServerConnectionStatus,
    isCurrentServer: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    allowDelete: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Generate unique color for this server
    val serverColor = rememberIdColor(server.id)

    // Card dimensions - circular card
    val cardSize = Cards.serverUserCircle

    val displayText =
        remember(server) {
            (server.name ?: server.url.replace(Regex("^https?://"), ""))
                .firstOrNull()
                ?.uppercase()
                ?: "?"
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Circular card with colored background
        Surface(
            onClick = onClick,
            onLongClick = if (allowDelete) onLongClick else null,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor =
                        if (isCurrentServer) {
                            serverColor.copy(alpha = 0.7f)
                        } else {
                            serverColor.copy(alpha = 0.5f)
                        },
                    focusedContainerColor =
                        if (isCurrentServer) {
                            serverColor.copy(alpha = 0.9f)
                        } else {
                            serverColor.copy(alpha = 0.7f)
                        },
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border =
                                BorderStroke(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            shape = CircleShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                // Show connection status indicator or server name/letter
                when (connectionStatus) {
                    is ServerConnectionStatus.Success -> {
                        // Show server name/letter

                        Text(
                            text = displayText,
                            style =
                                MaterialTheme.typography.displayLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                        )
                    }

                    ServerConnectionStatus.Pending -> {
                        CircularProgress(
                            modifier = Modifier.size(cardSize * 0.4f),
                        )
                    }

                    is ServerConnectionStatus.Error -> {
                        // Show warning icon with server letter below
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = connectionStatus.message,
                                tint = MaterialTheme.colorScheme.errorContainer,
                                modifier = Modifier.size(cardSize * 0.3f),
                            )
                            Text(
                                text = displayText,
                                style =
                                    MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                    ),
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }

        // Server name below the card
        Text(
            text = server.name?.ifBlank { null } ?: server.url,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .width(cardSize)
                    .padding(horizontal = 4.dp),
        )
    }
}

/**
 * Add Server card component - displays a + icon in a circle
 */
@Composable
fun AddServerCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }

    // Use a neutral gray color for the add server card
    val addServerColor = MaterialTheme.colorScheme.surfaceVariant

    // Card dimensions - circular card (same as server cards)
    val cardSize = Cards.height2x3 * 0.75f // ~120dp

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Circular card with colored background
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors =
                ClickableSurfaceDefaults.colors(
                    containerColor = addServerColor.copy(alpha = 0.4f),
                    focusedContainerColor = addServerColor.copy(alpha = 0.6f),
                ),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder =
                        Border(
                            border =
                                BorderStroke(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                            shape = CircleShape,
                        ),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.add_server),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(cardSize * 0.4f), // Size of the + icon
                )
            }
        }

        // "Add Server" text below the card
        Text(
            text = stringResource(R.string.add_server),
            style =
                MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Bold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .width(cardSize)
                    .padding(horizontal = 4.dp),
        )
    }
}
