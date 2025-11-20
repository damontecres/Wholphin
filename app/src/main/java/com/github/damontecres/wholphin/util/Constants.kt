package com.github.damontecres.wholphin.util

import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

val supportItemKinds =
    setOf(
        BaseItemKind.MOVIE,
        BaseItemKind.EPISODE,
        BaseItemKind.SERIES,
        BaseItemKind.VIDEO,
        BaseItemKind.SEASON,
        BaseItemKind.COLLECTION_FOLDER,
        BaseItemKind.FOLDER,
        BaseItemKind.USER_VIEW,
        BaseItemKind.TRAILER,
        BaseItemKind.TV_CHANNEL,
        BaseItemKind.TV_PROGRAM,
        BaseItemKind.LIVE_TV_CHANNEL,
        BaseItemKind.LIVE_TV_PROGRAM,
        BaseItemKind.RECORDING,
    )

val supportedCollectionTypes =
    setOf(
        CollectionType.MOVIES,
        CollectionType.TVSHOWS,
        CollectionType.HOMEVIDEOS,
        CollectionType.PLAYLISTS,
        CollectionType.BOXSETS,
        CollectionType.LIVETV,
        CollectionType.MUSICVIDEOS,
        CollectionType.FOLDERS,
        null, // Mixed
    )

val supportedPlayableTypes =
    setOf(
        BaseItemKind.MOVIE,
        BaseItemKind.EPISODE,
        BaseItemKind.VIDEO,
        BaseItemKind.TV_CHANNEL,
        BaseItemKind.TV_PROGRAM,
        BaseItemKind.RECORDING,
    )
