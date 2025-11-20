package com.github.damontecres.wholphin.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.ItemGrid
import com.github.damontecres.wholphin.ui.components.LicenseInfo
import com.github.damontecres.wholphin.ui.detail.CollectionFolderBoxSet
import com.github.damontecres.wholphin.ui.detail.CollectionFolderGeneric
import com.github.damontecres.wholphin.ui.detail.CollectionFolderLiveTv
import com.github.damontecres.wholphin.ui.detail.CollectionFolderMovie
import com.github.damontecres.wholphin.ui.detail.CollectionFolderPlaylist
import com.github.damontecres.wholphin.ui.detail.CollectionFolderRecordings
import com.github.damontecres.wholphin.ui.detail.CollectionFolderTv
import com.github.damontecres.wholphin.ui.detail.DebugPage
import com.github.damontecres.wholphin.ui.detail.FavoritesPage
import com.github.damontecres.wholphin.ui.detail.PersonPage
import com.github.damontecres.wholphin.ui.detail.PlaylistDetails
import com.github.damontecres.wholphin.ui.detail.movie.MovieDetails
import com.github.damontecres.wholphin.ui.detail.series.SeriesDetails
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
import timber.log.Timber

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
        is Destination.UserList -> SwitchUserContent(destination.server, modifier)

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
                    CollectionFolderBoxSet(
                        preferences,
                        destination.itemId,
                        destination.item,
                        false,
                        modifier,
                    )

                BaseItemKind.PLAYLIST ->
                    PlaylistDetails(
                        destination = destination,
                        modifier = modifier,
                    )

                BaseItemKind.COLLECTION_FOLDER,
                BaseItemKind.USER_VIEW,
                BaseItemKind.FOLDER,
                ->
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.item?.data?.collectionType,
                        modifier = modifier,
                    )

                BaseItemKind.PERSON ->
                    PersonPage(
                        preferences,
                        destination,
                        modifier,
                    )

                else -> {
                    Timber.w("Unsupported item type: ${destination.type}")
                    Text("Unsupported item type: ${destination.type}")
                }
            }

        is Destination.FilteredCollection ->
            CollectionFolderGeneric(
                preferences = preferences,
                itemId = destination.itemId,
                filter = destination.filter,
                recursive = destination.recursive,
                usePosters = true,
                modifier = modifier,
            )

        is Destination.Recordings ->
            CollectionFolderRecordings(
                preferences,
                destination.itemId,
                false,
                modifier,
            )

        is Destination.ItemGrid ->
            ItemGrid(
                destination,
                modifier,
            )

        Destination.Favorites ->
            FavoritesPage(
                preferences = preferences,
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

@Composable
fun CollectionFolder(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    collectionType: CollectionType?,
    modifier: Modifier = Modifier,
) {
    when (collectionType) {
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
            CollectionFolderBoxSet(
                preferences,
                destination.itemId,
                destination.item,
                false,
                modifier,
            )

        CollectionType.PLAYLISTS ->
            CollectionFolderPlaylist(
                preferences,
                destination.itemId,
                destination.item,
                true,
                modifier,
            )

        CollectionType.LIVETV ->
            CollectionFolderLiveTv(
                preferences = preferences,
                destination = destination,
                modifier = modifier,
            )

        CollectionType.HOMEVIDEOS,
        CollectionType.MUSICVIDEOS,
        CollectionType.MUSIC,
        CollectionType.BOOKS,
        CollectionType.PHOTOS,
        ->
            CollectionFolderGeneric(
                preferences,
                destination.itemId,
                usePosters = false,
                recursive = false,
                modifier = modifier,
            )

        CollectionType.FOLDERS,
        CollectionType.TRAILERS,
        CollectionType.UNKNOWN,
        null,
        ->
            CollectionFolderGeneric(
                preferences,
                destination.itemId,
                usePosters = true,
                recursive = false,
                modifier = modifier,
            )
    }
}
