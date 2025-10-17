package com.github.damontecres.wholphin.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.LicenseInfo
import com.github.damontecres.wholphin.ui.detail.CollectionFolderGeneric
import com.github.damontecres.wholphin.ui.detail.CollectionFolderMovie
import com.github.damontecres.wholphin.ui.detail.CollectionFolderTv
import com.github.damontecres.wholphin.ui.detail.DebugPage
import com.github.damontecres.wholphin.ui.detail.PlaylistDetails
import com.github.damontecres.wholphin.ui.detail.SeriesDetails
import com.github.damontecres.wholphin.ui.detail.livetv.TvGrid
import com.github.damontecres.wholphin.ui.detail.movie.MovieDetails
import com.github.damontecres.wholphin.ui.detail.series.SeriesOverview
import com.github.damontecres.wholphin.ui.main.HomePage
import com.github.damontecres.wholphin.ui.main.SearchPage
import com.github.damontecres.wholphin.ui.playback.PlaybackPage
import com.github.damontecres.wholphin.ui.preferences.PreferencesPage
import com.github.damontecres.wholphin.ui.setup.InstallUpdatePage
import com.github.damontecres.wholphin.ui.setup.SwitchServerContent
import com.github.damontecres.wholphin.ui.setup.SwitchUserContent
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

                BaseItemKind.BOX_SET ->
                    CollectionFolderGeneric(
                        preferences,
                        destination.itemId,
                        destination.item,
                        false,
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

                        CollectionType.BOXSETS ->
                            CollectionFolderGeneric(
                                preferences,
                                destination.itemId,
                                destination.item,
                                true,
                                modifier,
                            )

                        else ->
                            CollectionFolderGeneric(
                                preferences,
                                destination.itemId,
                                destination.item,
                                false,
                                modifier,
                            )
                    }
                }

                BaseItemKind.PLAYLIST ->
                    PlaylistDetails(
                        destination = destination,
                        modifier = modifier,
                    )

                BaseItemKind.USER_VIEW ->
                    CollectionFolderGeneric(
                        preferences,
                        destination.itemId,
                        destination.item,
                        true,
                        modifier,
                    )

                else -> {
                    Text("Unsupported item type: ${destination.type}")
                }
            }

        is Destination.FilteredCollection ->
            CollectionFolderGeneric(
                preferences = preferences,
                itemId = destination.itemId,
                item = null,
                filter = destination.filter,
                recursive = destination.recursive,
                modifier = modifier,
            )

        Destination.LiveTvGuide ->
            TvGrid(
                modifier = modifier,
            )

        Destination.UpdateApp -> InstallUpdatePage(preferences, modifier)

        Destination.License -> LicenseInfo(modifier)

        Destination.Search ->
            SearchPage(
                userPreferences = preferences,
                modifier = modifier,
            )

        Destination.Debug -> DebugPage(preferences, modifier)
    }
}
