package com.github.damontecres.wholphin.preferences

import android.annotation.SuppressLint
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup

/**
 * Preferences related to card overlays
 */
object CardPreferences {
    val ShowWatchIndicator =
        AppMultiChoicePreference<CardInterfacePreferences, WatchedIndicatorType>(
            title = R.string.watch_indicator,
//            summary = R.string.display_toggles_summary,
            defaultValue =
                listOf(
                    WatchedIndicatorType.MOVIE,
                    WatchedIndicatorType.EPISODE,
                    WatchedIndicatorType.TV_SHOW,
                    WatchedIndicatorType.TV_SEASON,
                ),
            allValues = WatchedIndicatorType.entries.filterNot { it == WatchedIndicatorType.UNRECOGNIZED },
            displayValues = R.array.watch_indicator_types,
            getter = {
                it.watchedIndicatorList
            },
            setter = { prefs, value ->
                prefs.update {
                    clearWatchedIndicator()
                    addAllWatchedIndicator(value)
                }
            },
        )

    @SuppressLint("PrivateResource")
    val ShowWatchProgress =
        AppMultiChoicePreference<CardInterfacePreferences, WatchedIndicatorType>(
            title = R.string.watch_progress,
//            summary = R.string.display_toggles_summary,
            defaultValue =
                listOf(
                    WatchedIndicatorType.MOVIE,
                    WatchedIndicatorType.EPISODE,
                    WatchedIndicatorType.TV_SHOW,
                    WatchedIndicatorType.TV_SEASON,
                ),
            allValues = WatchedIndicatorType.entries.filterNot { it == WatchedIndicatorType.UNRECOGNIZED },
            displayValues = R.array.watch_indicator_types,
            displayValuesSubtitles = R.array.watch_progress_subtitles,
            getter = {
                it.watchedProgressList
            },
            setter = { prefs, value ->
                prefs.update {
                    clearWatchedProgress()
                    addAllWatchedProgress(value)
                }
            },
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

    val ShowVersions =
        AppSwitchPreference<CardInterfacePreferences>(
            title = R.string.versions_title,
            defaultValue = true,
            getter = { it.versionsIndicator },
            setter = { prefs, value ->
                prefs.update { versionsIndicator = value }
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
                        ShowWatchProgress,
                        ShowFavoritesIndicator,
                        ShowUnwatchedCount,
                        ShowVersions,
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
