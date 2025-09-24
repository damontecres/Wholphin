package com.github.damontecres.dolphin.ui.nav

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

class NavigationManager(
    val backStack: NavBackStack<NavKey>,
) {
    fun navigateTo(destination: Destination) {
        backStack.add(destination)
    }

    fun goBack() {
        backStack.removeLastOrNull()
    }

    fun goToHome() {
        backStack.clear()
        backStack.add(Destination.Main)
    }
}
