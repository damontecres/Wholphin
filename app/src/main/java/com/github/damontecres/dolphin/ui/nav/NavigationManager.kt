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
        while (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    fun reloadHome() {
        goToHome()
        val id = (backStack[0] as Destination.Main).id + 1
        backStack[0] = Destination.Main(id)
    }
}
