package com.github.damontecres.wholphin.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.filter.DefaultForGenresFilterOptions
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.ItemGrid
import com.github.damontecres.wholphin.ui.components.LicenseInfo
import com.github.damontecres.wholphin.ui.data.MovieSortOptions
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
import com.github.damontecres.wholphin.ui.detail.discover.DiscoverMovieDetails
import com.github.damontecres.wholphin.ui.detail.discover.DiscoverPersonPage
import com.github.damontecres.wholphin.ui.detail.discover.DiscoverSeriesDetails
import com.github.damontecres.wholphin.ui.detail.episode.EpisodeDetails
import com.github.damontecres.wholphin.ui.detail.movie.MovieDetails
import com.github.damontecres.wholphin.ui.detail.series.SeriesDetails
import com.github.damontecres.wholphin.ui.detail.series.SeriesOverview
import com.github.damontecres.wholphin.ui.discover.DiscoverPage
import com.github.damontecres.wholphin.ui.main.HomePage
import com.github.damontecres.wholphin.ui.main.SearchPage
import com.github.damontecres.wholphin.ui.playback.PlaybackPage
import com.github.damontecres.wholphin.ui.preferences.PreferencesPage
import com.github.damontecres.wholphin.ui.setup.InstallUpdatePage
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber

/**
 * Chose the page for the [Destination]
 */
@Composable
fun DestinationContent(
    destination: Destination,
    preferences: UserPreferences,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (destination.fullScreen) {
        LaunchedEffect(Unit) { onClearBackdrop.invoke() }
    }
    when (destination) {
        is Destination.Home -> {
            HomePage(
                preferences = preferences,
                modifier = modifier,
            )
        }

        is Destination.PlaybackList,
        is Destination.Playback,
        -> {
            PlaybackPage(
                preferences = preferences,
                destination = destination,
                modifier = modifier,
            )
        }

        is Destination.Settings -> {
            PreferencesPage(
                preferences.appPreferences,
                destination.screen,
                modifier,
            )
        }

        is Destination.SeriesOverview -> {
            SeriesOverview(
                preferences = preferences,
                destination = destination,
                initialSeasonEpisode = destination.seasonEpisode,
                modifier = modifier,
            )
        }

        is Destination.MediaItem -> {
            when (destination.type) {
                BaseItemKind.SERIES -> {
                    SeriesDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.MOVIE -> {
                    MovieDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.VIDEO -> {
                    // TODO Use VideoDetails
                    MovieDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.EPISODE -> {
                    EpisodeDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.BOX_SET -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolderBoxSet(
                        preferences = preferences,
                        itemId = destination.itemId,
                        recursive = false,
                        playEnabled = true,
                        modifier = modifier,
                    )
                }

                BaseItemKind.PLAYLIST -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    PlaylistDetails(
                        destination = destination,
                        modifier = modifier,
                    )
                }

                BaseItemKind.COLLECTION_FOLDER -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = null,
                        recursiveOverride = null,
                        modifier = modifier,
                    )
                }

                BaseItemKind.FOLDER -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = true,
                        recursiveOverride = null,
                        modifier = modifier,
                    )
                }

                BaseItemKind.USER_VIEW -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = null,
                        recursiveOverride = true,
                        modifier = modifier,
                    )
                }

                BaseItemKind.PERSON -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    PersonPage(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                else -> {
                    Timber.w("Unsupported item type: ${destination.type}")
                    Text("Unsupported item type: ${destination.type}")
                }
            }
        }

        is Destination.FilteredCollection -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderGeneric(
                preferences = preferences,
                itemId = destination.itemId,
                filter = destination.filter,
                recursive = destination.recursive,
                usePosters = true,
                playEnabled = true, // TODO only genres use this currently, so might need to change in future
                filterOptions = DefaultForGenresFilterOptions,
                modifier = modifier,
            )
        }

        is Destination.Recordings -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderRecordings(
                preferences,
                destination.itemId,
                false,
                modifier,
            )
        }

        is Destination.ItemGrid -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            ItemGrid(
                destination,
                modifier,
            )
        }

        Destination.Favorites -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            FavoritesPage(
                preferences = preferences,
                modifier = modifier,
            )
        }

        Destination.UpdateApp -> {
            InstallUpdatePage(preferences, modifier)
        }

        Destination.License -> {
            LicenseInfo(modifier)
        }

        Destination.Search -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            SearchPage(
                userPreferences = preferences,
                modifier = modifier,
            )
        }

        Destination.Debug -> {
            DebugPage(preferences, modifier)
        }

        Destination.Discover -> {
            DiscoverPage(
                preferences = preferences,
                modifier = modifier,
            )
        }

        is Destination.DiscoveredItem -> {
            when (destination.item.type) {
                SeerrItemType.MOVIE -> {
                    DiscoverMovieDetails(
                        preferences = preferences,
                        destination = destination,
                        modifier = modifier,
                    )
                }

                SeerrItemType.TV -> {
                    DiscoverSeriesDetails(
                        preferences = preferences,
                        destination = destination,
                        modifier = modifier,
                    )
                }

                SeerrItemType.PERSON -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    DiscoverPersonPage(
                        person = destination.item,
                        modifier = modifier,
                    )
                }

                SeerrItemType.UNKNOWN -> {
                    Text(
                        text = "Unknown discover type",
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
fun CollectionFolder(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    collectionType: CollectionType?,
    usePostersOverride: Boolean?,
    recursiveOverride: Boolean?,
    modifier: Modifier = Modifier,
) {
    when (collectionType) {
        CollectionType.TVSHOWS -> {
            CollectionFolderTv(
                preferences,
                destination,
                modifier,
            )
        }

        CollectionType.MOVIES -> {
            CollectionFolderMovie(
                preferences,
                destination,
                modifier,
            )
        }

        CollectionType.BOXSETS -> {
            CollectionFolderGeneric(
                preferences = preferences,
                itemId = destination.itemId,
                usePosters = true,
                recursive = false,
                playEnabled = false,
                modifier = modifier,
                sortOptions = MovieSortOptions,
            )
        }

        CollectionType.PLAYLISTS -> {
            CollectionFolderPlaylist(
                preferences,
                destination.itemId,
                true,
                modifier,
            )
        }

        CollectionType.LIVETV -> {
            CollectionFolderLiveTv(
                preferences = preferences,
                destination = destination,
                modifier = modifier,
            )
        }

        CollectionType.HOMEVIDEOS,
        CollectionType.MUSICVIDEOS,
        CollectionType.MUSIC,
        CollectionType.BOOKS,
        CollectionType.PHOTOS,
        -> {
            CollectionFolderGeneric(
                preferences,
                destination.itemId,
                usePosters = usePostersOverride ?: false,
                recursive = recursiveOverride ?: false,
                playEnabled = true,
                modifier = modifier,
            )
        }

        CollectionType.FOLDERS,
        CollectionType.TRAILERS,
        CollectionType.UNKNOWN,
        null,
        -> {
            CollectionFolderGeneric(
                preferences,
                destination.itemId,
                usePosters = usePostersOverride ?: false,
                recursive = recursiveOverride ?: false,
                playEnabled = false,
                modifier = modifier,
            )
        }
    }
}
