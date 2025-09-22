package com.github.damontecres.dolphin.ui.nav

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.scene.rememberSceneSetupNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.github.damontecres.dolphin.preferences.UserPreferences

@Composable
fun ApplicationContent(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(Destination.Main)

    val navigationManager =
        object : NavigationManager {
            override fun navigateTo(destination: Destination) {
                backStack.add(destination)
            }

            override fun goBack() {
                backStack.removeLastOrNull()
            }

            override fun goToHome() {
                backStack.clear()
                backStack.add(Destination.Main)
            }
        }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators =
            listOf(
                rememberSceneSetupNavEntryDecorator(),
                rememberSavedStateNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider = { key ->
            NavEntry(key) {
                key as Destination
                if (key.fullScreen) {
                    DestinationContent(
                        destination = key,
                        preferences = preferences,
                        navigationManager = navigationManager,
                        modifier = modifier.fillMaxSize(),
                    )
                } else {
                    NavDrawer(
                        destination = key,
                        preferences = preferences,
                        navigationManager = navigationManager,
                        modifier = modifier,
                    )
                }
            }
        },
    )
}
