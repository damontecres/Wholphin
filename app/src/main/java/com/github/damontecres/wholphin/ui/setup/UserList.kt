package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import com.github.damontecres.wholphin.data.model.JellyfinUser
import java.util.UUID
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.AspectRatios

/**
 * Display a list of users plus option to add a new one or switch servers
 * Redesigned to match streaming service style with horizontal scrollable user icons
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
    apiClient: ApiClient? = null,
) {
    var showDeleteDialog by remember { mutableStateOf<JellyfinUser?>(null) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Horizontal scrollable list of user icons - centered
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp), // Spacing to accommodate 20% scale
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp), // Increased padding to accommodate 20% scale
                modifier = Modifier.wrapContentWidth(),
            ) {
                items(users) { user ->
                    UserIconCard(
                        user = user,
                        isCurrentUser = user.id == currentUser?.id,
                        onClick = { onSwitchUser.invoke(user) },
                        onLongClick = { showDeleteDialog = user },
                        apiClient = apiClient,
                    )
                }
                // Add User card - always rightmost
                item {
                    AddUserCard(
                        onClick = { onAddUser.invoke() },
                    )
                }
            }
        }

        // Switch servers button below user list - centered
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        ) {
            Button(
                onClick = { onSwitchServer.invoke() },
                modifier = Modifier.width(200.dp), // Fixed width for consistency
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.fa_arrow_left_arrow_right),
                        fontFamily = FontAwesome,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.switch_servers),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    showDeleteDialog?.let { user ->
        DialogPopup(
            showDialog = true,
            title = user.name ?: user.id.toString(),
            dialogItems =
                listOf(
                    DialogItem(
                        stringResource(R.string.switch_user),
                        R.string.fa_arrow_left_arrow_right,
                    ) {
                        onSwitchUser.invoke(user)
                    },
                    DialogItem(
                        stringResource(R.string.delete),
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

/**
 * Generate a consistent color for a user based on their ID
 */
@Composable
private fun getUserColor(userId: UUID): Color {
    return remember(userId) {
        // Generate a color based on the user ID hash
        val hash = userId.hashCode()
        val hue = (hash % 360).toFloat()
        val saturation = 0.6f + ((hash / 360) % 40).toFloat() / 100f // 0.6-1.0
        val brightness = 0.4f + ((hash / 14400) % 30).toFloat() / 100f // 0.4-0.7 (darker colors)
        
        // Convert HSV to RGB
        val c = brightness * saturation
        val x = c * (1 - kotlin.math.abs((hue / 60f) % 2f - 1))
        val m = brightness - c
        
        val (r, g, b) = when {
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
            blue = (b + m).coerceIn(0f, 1f)
        )
    }
}

@Composable
private fun UserIconCard(
    user: JellyfinUser,
    isCurrentUser: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    apiClient: ApiClient? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    // Generate unique color for this user
    val userColor = getUserColor(user.id)
    
    // Get user profile image URL from Jellyfin API
    val userImageUrl = remember(user.id, apiClient) {
        apiClient?.imageApi?.getUserImageUrl(user.id)
    }
    
    // Track image loading errors
    var imageError by remember { mutableStateOf(false) }
    
    // Card dimensions - circular card
    val cardSize = Cards.height2x3 * 0.75f // ~120dp (same size as before)
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp), // Increased to accommodate 20% scale
    ) {
        // Circular card with colored background
        Surface(
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isCurrentUser) {
                    userColor.copy(alpha = 0.7f)
                } else {
                    userColor.copy(alpha = 0.5f)
                },
                focusedContainerColor = if (isCurrentUser) {
                    userColor.copy(alpha = 0.9f)
                } else {
                    userColor.copy(alpha = 0.7f)
                },
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = CircleShape
                ),
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (userImageUrl != null && !imageError) {
                    AsyncImage(
                        model = userImageUrl,
                        contentDescription = user.name,
                        contentScale = ContentScale.Crop,
                        onError = { imageError = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                    )
                } else {
                    // Show big bold first letter of username
                    val firstLetter = remember(user.name) {
                        (user.name?.firstOrNull()?.uppercaseChar() ?: user.id.toString().firstOrNull()?.uppercaseChar())?.toString() ?: "?"
                    }
                    Text(
                        text = firstLetter,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        
        // Username below the card
        Text(
            text = user.name ?: user.id.toString(),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(cardSize)
                .padding(horizontal = 4.dp),
        )
    }
}

/**
 * Add User card component - displays a + icon in a circle
 */
@Composable
private fun AddUserCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    
    // Use a neutral gray color for the add user card
    val addUserColor = MaterialTheme.colorScheme.surfaceVariant
    
    // Card dimensions - circular card (same as user cards)
    val cardSize = Cards.height2x3 * 0.75f // ~120dp
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp), // Increased to accommodate 20% scale
    ) {
        // Circular card with colored background
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = Modifier.size(cardSize),
            shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = addUserColor.copy(alpha = 0.4f),
                focusedContainerColor = addUserColor.copy(alpha = 0.6f),
            ),
            border = ClickableSurfaceDefaults.border(
                focusedBorder = Border(
                    border = BorderStroke(
                        width = 3.dp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = CircleShape
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
                    contentDescription = stringResource(R.string.add_user),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(cardSize * 0.4f), // Size of the + icon
                )
            }
        }
        
        // "Add User" text below the card
        Text(
            text = stringResource(R.string.add_user),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(cardSize)
                .padding(horizontal = 4.dp),
        )
    }
}
