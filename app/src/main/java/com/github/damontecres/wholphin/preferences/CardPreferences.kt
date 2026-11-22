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
            title = androidx.compose.ui.R.string.in_progress,
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
            title = R.string.collections,
            defaultValue = true,
            getter = { it.collectionWatchedProgress },
            setter = { prefs, value ->
                prefs.update { collectionWatchedProgress = value }
            },
            summaryOn = R.string.show,
            summaryOff = R.string.hide,
        )

    val ShowSeriesWatchProgress =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.tv_shows_title,
            defaultValue = true,
            getter = { it.seriesWatchedProgress },
            setter = { prefs, value ->
                prefs.update { seriesWatchedProgress = value }
            },
            summaryOn = R.string.show,
            summaryOff = R.string.hide,
        )

    val ShowSeasonWatchProgress =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.tv_seasons,
            defaultValue = true,
            getter = { it.seasonWatchedProgress },
            setter = { prefs, value ->
                prefs.update { seasonWatchedProgress = value }
            },
            summaryOn = R.string.show,
            summaryOff = R.string.hide,
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

    val ShowEpisodeNumber =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.episode_number_text,
            defaultValue = true,
            getter = { it.episodeEpisodeNumber },
            setter = { prefs, value ->
                prefs.update { episodeEpisodeNumber = value }
            },
            summaryOn = R.string.show,
            summaryOff = R.string.hide,
        )

    val ShowSeasonNumber =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.season_number_text,
            defaultValue = false,
            getter = { it.episodeSeasonNumber },
            setter = { prefs, value ->
                prefs.update { episodeSeasonNumber = value }
            },
            summaryOn = R.string.show,
            summaryOff = R.string.hide,
        )

    val Preferences =
        listOf(
            PreferenceGroup(
                title = R.string.general,
                preferences =
                    listOf(
                        ShowWatchIndicator,
                        ShowFavoritesIndicator,
                        ShowUnwatchedCount,
                    ),
            ),
            PreferenceGroup(
                title = R.string.watch_progress,
                preferences =
                    listOf(
                        ShowWatchProgress,
                        ShowCollectionWatchProgress,
                        ShowSeriesWatchProgress,
                        ShowSeasonWatchProgress,
                    ),
            ),
            PreferenceGroup(
                title = R.string.episodes,
                preferences =
                    listOf(
                        ShowEpisodeNumber,
                        ShowSeasonNumber,
                    ),
            ),
        )
}

inline fun CardInterfacePreferences.update(block: CardInterfacePreferences.Builder.() -> Unit): CardInterfacePreferences =
    toBuilder().apply(block).build()
