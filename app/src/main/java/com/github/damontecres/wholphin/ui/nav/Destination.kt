@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.ui.nav

import androidx.annotation.StringRes
import androidx.navigation3.runtime.NavKey
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.util.DiscoverRequestType
import com.github.damontecres.wholphin.util.RequestHandler
import com.github.damontecres.wholphin.util.SEERR_PAGE_SIZE
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

/**
 * Represents a page in the app
 *
 * @param fullScreen whether the page should be full page aka not include the nav drawer
 */
@Serializable
sealed class Destination(
    val fullScreen: Boolean = false,
) : NavKey {
    @Serializable
    data class Home(
        val id: Long = 0L,
    ) : Destination()

    @Serializable
    data object HomeSettings : Destination(true)

    @Serializable
    data class Settings(
        val screen: PreferenceScreenOption,
    ) : Destination(true)

    @Serializable
    data class SubtitleSettings(
        val hdr: Boolean,
    ) : Destination(true)

    @Serializable
    data object Search : Destination()

    @Serializable
    data class SeriesOverview(
        val itemId: UUID,
        val type: BaseItemKind,
        val seasonEpisode: SeasonEpisodeIds? = null,
    ) : Destination() {
        override fun toString(): String = "SeriesOverview(itemId=$itemId, type=$type, seasonEpisode=$seasonEpisode)"
    }

    @Serializable
    data class MediaItem(
        val itemId: UUID,
        val type: BaseItemKind,
        val collectionType: CollectionType? = null,
    ) : Destination() {
        constructor(item: BaseItem) : this(item.id, item.type, item.data.collectionType)
    }

    @Serializable
    data class Recordings(
        val itemId: UUID,
    ) : Destination()

    @Serializable
    data class Playback(
        val itemId: UUID,
        val positionMs: Long,
        val forceTranscoding: Boolean = false,
        val backend: PlayerBackend? = null,
    ) : Destination(true) {
        constructor(item: BaseItem) : this(item.id, item.resumeMs)
    }

    @Serializable
    data class PlaybackList(
        val itemId: UUID,
        val filter: GetItemsFilter = GetItemsFilter(),
        val startIndex: Int? = null,
        val shuffle: Boolean = false,
        val recursive: Boolean = false,
        val sortAndDirection: SortAndDirection? = null,
    ) : Destination(true) {
        override fun toString(): String = "PlaybackList(itemId=$itemId)"
    }

    @Serializable
    data class FilteredCollection(
        val itemId: UUID,
        val parentType: BaseItemKind,
        val filter: CollectionFolderFilter,
        val recursive: Boolean,
    ) : Destination(false)

    @Serializable
    data class ItemGrid<T>(
        val title: String?,
        @param:StringRes val titleRes: Int?,
        @Contextual val request: T,
        val requestHandler: RequestHandler<T>,
        val initialPosition: Int = 0,
        val viewOptions: ViewOptions = ViewOptions(),
    ) : Destination(false)

    @Serializable
    data class MoreHomeRow(
        val title: String,
        val config: HomeRowConfig,
        val initialPosition: Int,
    ) : Destination(false)

    @Serializable
    data class Slideshow(
        val parentId: UUID,
        val index: Int,
        val filter: CollectionFolderFilter,
        val sortAndDirection: SortAndDirection,
        val recursive: Boolean,
        val startSlideshow: Boolean,
    ) : Destination(true)

    @Serializable
    data object Favorites : Destination(false)

    @Serializable
    data object Discover : Destination(false)

    @Serializable
    data class DiscoveredItem(
        val item: DiscoverItem,
    ) : Destination(false)

    data class DiscoverMoreResult(
        val type: DiscoverRequestType,
        val startIndex: Int = SEERR_PAGE_SIZE,
    ) : Destination(false)

    @Serializable
    data object NowPlaying : Destination(true)

    @Serializable
    data object UpdateApp : Destination(true)

    @Serializable
    data object License : Destination(true)

    @Serializable
    data object Debug : Destination(true)
}
