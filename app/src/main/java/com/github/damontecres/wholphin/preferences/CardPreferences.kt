package com.github.damontecres.wholphin.preferences

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup

object CardPreferences {
    val ShowWatchIndicator =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.watch_indicator,
            defaultValue = true,
            getter = { it.watchedIndicator },
            setter = { prefs, value ->
                prefs.update { watchedIndicator = value }
            },
            summaryOn = R.string.show,
            summaryOff = R.string.hide,
        )

    val ShowWatchProgress =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.watch_progress,
            defaultValue = true,
            getter = { it.watchedProgress },
            setter = { prefs, value ->
                prefs.update { watchedProgress = value }
            },
            summaryOn = R.string.show,
            summaryOff = R.string.hide,
        )

    val ShowCollectionWatchProgress =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.watch_progress,
            defaultValue = true,
            getter = { it.collectionWatchedProgress },
            setter = { prefs, value ->
                prefs.update { collectionWatchedProgress = value }
            },
            summary = R.string.collections,
        )

    val ShowSeriesWatchProgress =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.watch_progress,
            defaultValue = true,
            getter = { it.seriesWatchedProgress },
            setter = { prefs, value ->
                prefs.update { seriesWatchedProgress = value }
            },
            summary = R.string.tv_shows_title,
        )

    val ShowSeasonWatchProgress =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.watch_progress,
            defaultValue = true,
            getter = { it.seasonWatchedProgress },
            setter = { prefs, value ->
                prefs.update { seasonWatchedProgress = value }
            },
            summary = R.string.tv_seasons,
        )

    val ShowFavoritesIndicator =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.favorites,
            defaultValue = true,
            getter = { it.favoriteIndicator },
            setter = { prefs, value ->
                prefs.update { favoriteIndicator = value }
            },
            summaryOn = R.string.show,
            summaryOff = R.string.hide,
        )

    val ShowUnwatchedCount =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.unwatched_count,
            defaultValue = true,
            getter = { it.unwatchedCount },
            setter = { prefs, value ->
                prefs.update { unwatchedCount = value }
            },
            summaryOn = R.string.show,
            summaryOff = R.string.hide,
        )

    val Preferences =
        listOf(
            PreferenceGroup(
                title = R.string.ui_interface,
                preferences =
                    listOf(
                        ShowWatchIndicator,
                        ShowWatchProgress,
                        ShowCollectionWatchProgress,
                        ShowSeriesWatchProgress,
                        ShowSeasonWatchProgress,
                        ShowFavoritesIndicator,
                        ShowUnwatchedCount,
                    ),
            ),
        )
}

inline fun CardInterfacePreferences.update(block: CardInterfacePreferences.Builder.() -> Unit): CardInterfacePreferences =
    toBuilder().apply(block).build()
