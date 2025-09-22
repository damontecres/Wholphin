package com.github.damontecres.dolphin.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.main.MainPage

@Composable
fun DestinationContent(
    destination: Destination,
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        Destination.Main -> {
            MainPage(
                preferences = preferences,
                navigationManager = navigationManager,
                modifier = modifier,
            )
        }
        is Destination.MediaItem -> {
            Text("MediaItem: ${destination.itemId}")
        }
        is Destination.Playback -> TODO()
        Destination.Search -> TODO()
        Destination.Settings -> TODO()
        Destination.Setup -> TODO()
    }
}
