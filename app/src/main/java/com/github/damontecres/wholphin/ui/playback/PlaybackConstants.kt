package com.github.damontecres.wholphin.ui.playback

import androidx.compose.ui.layout.ContentScale
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.PrefContentScale
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

val playbackSpeedOptions = listOf(".25", ".5", ".75", "1.0", "1.25", "1.5", "1.75", "2.0")

val playbackScaleOptions =
    mapOf(
        ContentScale.Fit to R.string.content_scale_fit,
        ContentScale.None to R.string.content_scale_none,
        ContentScale.Crop to R.string.content_scale_crop,
//        ContentScale.Inside to "Inside",
        ContentScale.FillBounds to R.string.content_scale_fill,
        ContentScale.FillWidth to R.string.content_scale_fill_width,
        ContentScale.FillHeight to R.string.content_scale_fill_height,
    )

val PrefContentScale.scale: ContentScale
    get() =
        when (this) {
            PrefContentScale.FIT -> ContentScale.Fit
            PrefContentScale.NONE -> ContentScale.None
            PrefContentScale.CROP -> ContentScale.Crop
            PrefContentScale.FILL -> ContentScale.FillBounds
            PrefContentScale.Fill_WIDTH -> ContentScale.FillWidth
            PrefContentScale.FILL_HEIGHT -> ContentScale.FillHeight
            PrefContentScale.UNRECOGNIZED -> ContentScale.Fit
        }

/**
 * Whether the type can be played as-is
 *
 * For example, a video file is playable as-is, but a playlist requires fetching the items first
 */
val BaseItemKind.playable: Boolean
    get() =
        when (this) {
            BaseItemKind.EPISODE,
            BaseItemKind.MOVIE,
            BaseItemKind.MUSIC_VIDEO,
            BaseItemKind.TRAILER,
            BaseItemKind.VIDEO,
            BaseItemKind.LIVE_TV_CHANNEL,
            BaseItemKind.LIVE_TV_PROGRAM,
            BaseItemKind.PROGRAM,
            BaseItemKind.RECORDING,
            BaseItemKind.TV_CHANNEL,
            BaseItemKind.TV_PROGRAM,
            -> true

            // TODO add support for these eventually
            BaseItemKind.AUDIO_BOOK,
            BaseItemKind.AUDIO,
            BaseItemKind.CHANNEL,
            -> false

            BaseItemKind.AGGREGATE_FOLDER,
            BaseItemKind.BASE_PLUGIN_FOLDER,
            BaseItemKind.BOOK,
            BaseItemKind.BOX_SET,
            BaseItemKind.CHANNEL_FOLDER_ITEM,
            BaseItemKind.COLLECTION_FOLDER,
            BaseItemKind.FOLDER,
            BaseItemKind.GENRE,
            BaseItemKind.MANUAL_PLAYLISTS_FOLDER,
            BaseItemKind.MUSIC_ALBUM,
            BaseItemKind.MUSIC_ARTIST,
            BaseItemKind.MUSIC_GENRE,
            BaseItemKind.PERSON,
            BaseItemKind.PHOTO,
            BaseItemKind.PHOTO_ALBUM,
            BaseItemKind.PLAYLIST,
            BaseItemKind.PLAYLISTS_FOLDER,
            BaseItemKind.SEASON,
            BaseItemKind.SERIES,
            BaseItemKind.STUDIO,
            BaseItemKind.USER_ROOT_FOLDER,
            BaseItemKind.USER_VIEW,
            BaseItemKind.YEAR,
            -> false
        }

fun getTypeFor(collectionType: CollectionType): BaseItemKind? =
    when (collectionType) {
        CollectionType.UNKNOWN -> null
        CollectionType.MOVIES -> BaseItemKind.MOVIE
        CollectionType.TVSHOWS -> BaseItemKind.SERIES
        CollectionType.MUSIC -> BaseItemKind.AUDIO
        CollectionType.MUSICVIDEOS -> BaseItemKind.MUSIC_VIDEO
        CollectionType.TRAILERS -> BaseItemKind.TRAILER
        CollectionType.HOMEVIDEOS -> BaseItemKind.VIDEO
        CollectionType.BOXSETS -> BaseItemKind.BOX_SET
        CollectionType.BOOKS -> BaseItemKind.BOOK
        CollectionType.PHOTOS -> BaseItemKind.PHOTO_ALBUM
        CollectionType.LIVETV -> BaseItemKind.LIVE_TV_CHANNEL
        CollectionType.PLAYLISTS -> BaseItemKind.PLAYLIST
        CollectionType.FOLDERS -> BaseItemKind.FOLDER
    }
