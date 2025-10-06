package com.github.damontecres.dolphin.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.components.LicenseInfo
import com.github.damontecres.dolphin.ui.detail.CollectionFolderDetails
import com.github.damontecres.dolphin.ui.detail.CollectionFolderMovie
import com.github.damontecres.dolphin.ui.detail.CollectionFolderTv
import com.github.damontecres.dolphin.ui.detail.EpisodeDetails
import com.github.damontecres.dolphin.ui.detail.SeasonDetails
import com.github.damontecres.dolphin.ui.detail.SeriesDetails
import com.github.damontecres.dolphin.ui.detail.VideoDetails
import com.github.damontecres.dolphin.ui.detail.movie.MovieDetails
import com.github.damontecres.dolphin.ui.detail.series.SeriesOverview
import com.github.damontecres.dolphin.ui.main.HomePage
import com.github.damontecres.dolphin.ui.main.SearchPage
import com.github.damontecres.dolphin.ui.playback.PlaybackContent
import com.github.damontecres.dolphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.dolphin.ui.preferences.PreferencesPage
import com.github.damontecres.dolphin.ui.setup.SwitchServerContent
import com.github.damontecres.dolphin.ui.setup.SwitchUserContent
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.DeviceProfile

@Composable
fun DestinationContent(
    destination: Destination,
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    deviceProfile: DeviceProfile,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        is Destination.Main ->
            HomePage(
                preferences = preferences,
                navigationManager = navigationManager,
                modifier = modifier,
            )

        is Destination.Playback ->
            PlaybackContent(
                preferences = preferences,
                navigationManager = navigationManager,
                deviceProfile = deviceProfile,
                destination = destination,
                modifier = modifier,
            )

        Destination.ServerList -> SwitchServerContent(navigationManager, modifier)
        Destination.UserList -> SwitchUserContent(navigationManager, modifier)

        Destination.Settings ->
            PreferencesPage(
                navigationManager,
                preferences.appPreferences,
                PreferenceScreenOption.BASIC,
                modifier,
            )

        is Destination.SeriesOverview ->
            SeriesOverview(
                preferences,
                navigationManager,
                destination,
                modifier,
                initialSeasonEpisode = destination.seasonEpisode,
            )

        is Destination.MediaItem ->
            when (destination.type) {
                BaseItemKind.SERIES ->
                    SeriesDetails(
                        preferences,
                        navigationManager,
                        destination,
                        modifier,
                    )

                BaseItemKind.SEASON ->
                    SeasonDetails(
                        preferences,
                        navigationManager,
                        destination,
                        modifier,
                    )

                BaseItemKind.EPISODE ->
                    EpisodeDetails(
                        preferences,
                        navigationManager,
                        destination,
                        modifier,
                    )

                BaseItemKind.MOVIE ->
                    MovieDetails(
                        preferences,
                        navigationManager,
                        destination,
                        modifier,
                    )

                BaseItemKind.VIDEO ->
                    VideoDetails(
                        preferences,
                        navigationManager,
                        destination,
                        modifier,
                    )

                BaseItemKind.COLLECTION_FOLDER -> {
                    when (destination.item?.data?.collectionType) {
                        CollectionType.TVSHOWS ->
                            CollectionFolderTv(
                                preferences,
                                navigationManager,
                                destination,
                                modifier,
                            )

                        CollectionType.MOVIES ->
                            CollectionFolderMovie(
                                preferences,
                                navigationManager,
                                destination,
                                modifier,
                            )

                        else ->
                            CollectionFolderDetails(
                                preferences,
                                navigationManager,
                                destination,
                                modifier,
                            )
                    }
                }

                else -> {
                    Text("Unsupported item type: ${destination.type}")
                }
            }

        Destination.License -> LicenseInfo(modifier)

        Destination.Search ->
            SearchPage(
                navigationManager = navigationManager,
                userPreferences = preferences,
                modifier = modifier,
            )
        Destination.Setup -> TODO()
    }
}
