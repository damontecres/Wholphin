package com.github.damontecres.dolphin.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun MediaItemContent(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
) {
    when (destination.type) {
        BaseItemKind.SERIES -> TODO()
        BaseItemKind.SEASON -> TODO()
        BaseItemKind.EPISODE -> EpisodeDetails(preferences, navigationManager, destination, modifier)
        BaseItemKind.MOVIE -> TODO()
        BaseItemKind.VIDEO -> TODO()
        BaseItemKind.COLLECTION_FOLDER -> {
            CollectionFolderDetails(
                preferences,
                navigationManager,
                destination,
                modifier,
            )
        }
        else -> {
            Text("Unsupported item type: ${destination.type}")
        }
    }
}
