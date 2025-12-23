package com.github.damontecres.wholphin.services

import androidx.navigation3.runtime.NavKey
import com.github.damontecres.wholphin.ui.nav.Destination
import org.acra.ACRA
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages navigating between pages and manages the app's back stack
 */
@Singleton
class NavigationManager
    @Inject
    constructor() {
        var backStack: MutableList<NavKey> = mutableListOf()

        /**
         * Go to the specified [com.github.damontecres.wholphin.ui.nav.Destination]
         */
        fun navigateTo(destination: Destination) {
            backStack.add(destination)
            log()
        }

        /**
         * Go to the specified [Destination], but reset the back stack to Home first
         */
        fun navigateToFromDrawer(destination: Destination) {
            goToHome()
            backStack.add(destination)
            log()
        }

        /**
         * Go to the previous page
         */
        fun goBack() {
            synchronized(this) {
                if (backStack.size > 1) {
                    backStack.removeLastOrNull()
                }
            }
            log()
        }

        /**
         * Go all the way back to the home page
         */
        fun goToHome() {
            while (backStack.size > 1) {
                backStack.removeLastOrNull()
            }
            if (backStack[0] !is Destination.Home) {
                backStack[0] = Destination.Home()
            }
            log()
        }

        /**
         * Go all the way back to the home page, and reload it from scratch
         */
        fun reloadHome() {
            goToHome()
            val id = (backStack[0] as Destination.Home).id + 1
            backStack[0] = Destination.Home(id)
            log()
        }

        private fun log() {
            val dest = backStack.lastOrNull().toString()
            Timber.Forest.i("Current Destination: %s", dest)
            ACRA.errorReporter.putCustomData("destination", dest)
        }
    }
