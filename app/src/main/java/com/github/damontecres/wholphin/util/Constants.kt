package com.github.damontecres.wholphin.util

import com.github.damontecres.wholphin.ui.main.HomeSection
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType

val supportedHomeSection =
    setOf(
        HomeSection.LATEST_MEDIA,
        HomeSection.NEXT_UP,
        HomeSection.RESUME,
    )

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
    )
