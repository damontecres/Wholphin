package com.github.damontecres.wholphin.ui.playback

import androidx.compose.ui.layout.ContentScale
import com.github.damontecres.wholphin.preferences.PrefContentScale
import org.jellyfin.sdk.model.api.BaseItemKind

val playbackSpeedOptions = listOf(".25", ".5", ".75", "1.0", "1.25", "1.5", "1.75", "2.0")

val playbackScaleOptions =
    mapOf(
        ContentScale.Fit to "Fit",
        ContentScale.None to "None",
        ContentScale.Crop to "Crop",
//        ContentScale.Inside to "Inside",
        ContentScale.FillBounds to "Fill",
        ContentScale.FillWidth to "Fill Width",
        ContentScale.FillHeight to "Fill Height",
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
