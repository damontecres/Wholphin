package com.github.damontecres.dolphin.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.detail.MediaItemContent
import com.github.damontecres.dolphin.ui.main.MainPage
import com.github.damontecres.dolphin.ui.playback.PlaybackContent

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
            MediaItemContent(
                preferences = preferences,
                navigationManager = navigationManager,
                destination = destination,
                modifier = modifier,
            )
        }

        is Destination.Playback -> {
            PlaybackContent(
                preferences = preferences,
                navigationManager = navigationManager,
                destination = destination,
                modifier = modifier,
            )
        }
        Destination.Search -> TODO()
        Destination.Settings -> TODO()
        Destination.Setup -> TODO()
    }
}
