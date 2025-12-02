package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.ErrorMessage

/**
 * This is generally the root composable of the of the app
 *
 * Here the navigation backstack is used and pages are rendered in the nav drawer or full screen
 */
@Composable
fun ApplicationContent(
    server: JellyfinServer?,
    user: JellyfinUser?,
    navigationManager: NavigationManager,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = navigationManager.backStack,
        onBack = { navigationManager.goBack() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider = { key ->
            key as Destination
            val contentKey = "${key}_${server?.id}_${user?.id}"
            NavEntry(key, contentKey = contentKey) {
                if (key.fullScreen) {
                    DestinationContent(
                        destination = key,
                        preferences = preferences,
                        modifier = modifier.fillMaxSize(),
                    )
                } else if (user != null && server != null) {
                    NavDrawer(
                        destination = key,
                        preferences = preferences,
                        user = user,
                        server = server,
                        modifier = modifier,
                    )
                } else {
                    ErrorMessage("Trying to go to $key without a user logged in", null)
                }
            }
        },
    )
}
