package com.github.damontecres.dolphin.ui.nav

interface NavigationManager {
    fun navigateTo(destination: Destination)

    fun goBack()

    fun goToHome()
}
