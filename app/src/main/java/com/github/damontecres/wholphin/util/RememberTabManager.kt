package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.preferences.UserPreferences
import java.util.UUID

interface RememberTabManager {
    /**
     * If enabled, get the remembered tab index for the given item
     */
    fun getRememberedTab(
        preferences: UserPreferences,
        itemId: UUID,
        defaultTab: Int,
    ): Int

    /**
     * If enabled, save the remembered tab index for the given item
     */
    fun saveRememberedTab(
        preferences: UserPreferences,
        itemId: UUID,
        tabIndex: Int,
    )
}
