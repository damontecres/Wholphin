package com.github.damontecres.wholphin.ui

import androidx.compose.runtime.Immutable

@Immutable
data class InterfacePreferences(
    val watchProgress: Boolean = true,
    val watchedIndicator: Boolean = true,
    val seasonProgress: Boolean = true,
    val seriesProgress: Boolean = true,
//    val upperRightSeasonNumber: Boolean = false,
    val upperRightEpisodeNumber: Boolean = true,
    val favoriteIndicator: Boolean = true,
    val versionIndicator: Boolean = true,
    val unwatchedCount: Boolean = true,
) {
    companion object {
        val NONE =
            InterfacePreferences(
                watchProgress = false,
                watchedIndicator = false,
                seasonProgress = false,
                seriesProgress = false,
                upperRightEpisodeNumber = false,
                favoriteIndicator = false,
                versionIndicator = false,
                unwatchedCount = false,
            )
    }
}
