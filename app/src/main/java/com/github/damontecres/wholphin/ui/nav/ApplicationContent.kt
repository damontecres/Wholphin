package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.github.damontecres.wholphin.data.JellyfinServer
import com.github.damontecres.wholphin.data.JellyfinUser
import com.github.damontecres.wholphin.preferences.UserPreferences
import org.jellyfin.sdk.model.api.DeviceProfile
import timber.log.Timber

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
    deviceProfile: DeviceProfile,
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
            Timber.d("Navigate: %s", key)
            NavEntry(key, contentKey = contentKey) {
                if (key.fullScreen) {
                    DestinationContent(
                        destination = key,
                        preferences = preferences,
                        deviceProfile = deviceProfile,
                        modifier = modifier.fillMaxSize(),
                    )
                } else {
                    NavDrawer(
                        destination = key,
                        preferences = preferences,
                        deviceProfile = deviceProfile,
                        user = user,
                        server = server,
                        modifier = modifier,
                    )
                }
            }
        },
    )
}
