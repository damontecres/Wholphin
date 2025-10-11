package com.github.damontecres.dolphin.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.components.LicenseInfo
import com.github.damontecres.dolphin.ui.detail.CollectionFolderGeneric
import com.github.damontecres.dolphin.ui.detail.CollectionFolderMovie
import com.github.damontecres.dolphin.ui.detail.CollectionFolderTv
import com.github.damontecres.dolphin.ui.detail.EpisodeDetails
import com.github.damontecres.dolphin.ui.detail.SeasonDetails
import com.github.damontecres.dolphin.ui.detail.SeriesDetails
import com.github.damontecres.dolphin.ui.detail.movie.MovieDetails
import com.github.damontecres.dolphin.ui.detail.series.SeriesOverview
import com.github.damontecres.dolphin.ui.main.HomePage
import com.github.damontecres.dolphin.ui.main.SearchPage
import com.github.damontecres.dolphin.ui.playback.PlaybackPage
import com.github.damontecres.dolphin.ui.preferences.PreferencesPage
import com.github.damontecres.dolphin.ui.setup.InstallUpdatePage
import com.github.damontecres.dolphin.ui.setup.SwitchServerContent
import com.github.damontecres.dolphin.ui.setup.SwitchUserContent
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.DeviceProfile

/**
 * Chose the page for the [Destination]
 */
@Composable
fun DestinationContent(
    destination: Destination,
    preferences: UserPreferences,
    deviceProfile: DeviceProfile,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        is Destination.Home ->
            HomePage(
                preferences = preferences,
                modifier = modifier,
            )

        is Destination.Playback ->
            PlaybackPage(
                preferences = preferences,
                deviceProfile = deviceProfile,
                destination = destination,
                modifier = modifier,
            )

        Destination.ServerList -> SwitchServerContent(modifier)
        Destination.UserList -> SwitchUserContent(modifier)

        is Destination.Settings ->
            PreferencesPage(
                preferences.appPreferences,
                destination.screen,
                modifier,
            )

        is Destination.SeriesOverview ->
            SeriesOverview(
                preferences,
                destination,
                modifier,
                initialSeasonEpisode = destination.seasonEpisode,
            )

        is Destination.MediaItem ->
            when (destination.type) {
                BaseItemKind.SERIES ->
                    SeriesDetails(
                        preferences,
                        destination,
                        modifier,
                    )

                BaseItemKind.SEASON ->
                    SeasonDetails(
                        preferences,
                        destination,
                        modifier,
                    )

                BaseItemKind.EPISODE ->
                    EpisodeDetails(
                        preferences,
                        destination,
                        modifier,
                    )

                BaseItemKind.MOVIE ->
                    MovieDetails(
                        preferences,
                        destination,
                        modifier,
                    )

                BaseItemKind.VIDEO ->
                    // TODO Use VideoDetails
                    MovieDetails(
                        preferences,
                        destination,
                        modifier,
                    )

                BaseItemKind.COLLECTION_FOLDER -> {
                    when (destination.item?.data?.collectionType) {
                        CollectionType.TVSHOWS ->
                            CollectionFolderTv(
                                preferences,
                                destination,
                                modifier,
                            )

                        CollectionType.MOVIES ->
                            CollectionFolderMovie(
                                preferences,
                                destination,
                                modifier,
                            )

                        else ->
                            CollectionFolderGeneric(
                                preferences,
                                destination,
                                modifier,
                            )
                    }
                }

                else -> {
                    Text("Unsupported item type: ${destination.type}")
                }
            }

        Destination.UpdateApp -> InstallUpdatePage(preferences, modifier)

        Destination.License -> LicenseInfo(modifier)

        Destination.Search ->
            SearchPage(
                userPreferences = preferences,
                modifier = modifier,
            )
    }
}
