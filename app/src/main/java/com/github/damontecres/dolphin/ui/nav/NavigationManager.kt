package com.github.damontecres.dolphin.ui.nav

import androidx.navigation3.runtime.NavKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NavigationManager
    @Inject
    constructor() {
        var backStack: MutableList<NavKey> = mutableListOf()

        fun navigateTo(destination: Destination) {
            backStack.add(destination)
        }

        fun navigateToFromDrawer(destination: Destination) {
            goToHome()
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
