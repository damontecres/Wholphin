@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.ui.nav

import androidx.navigation3.runtime.NavKey
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisode
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
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
    data object ServerList : Destination(true)

    @Serializable
    data object UserList : Destination(true)

    @Serializable
    data class Home(
        val id: Long = 0L,
    ) : Destination()

    @Serializable
    data class Settings(
        val screen: PreferenceScreenOption,
    ) : Destination(true)

    @Serializable
    data object Search : Destination()

    @Serializable
    data class SeriesOverview(
        val itemId: UUID,
        val type: BaseItemKind,
        @Transient val item: BaseItem? = null,
        val seasonEpisode: SeasonEpisode? = null,
    ) : Destination() {
        override fun toString(): String = "SeriesOverview(itemId=$itemId, type=$type, seasonEpisode=$seasonEpisode)"
    }

    @Serializable
    data class MediaItem(
        val itemId: UUID,
        val type: BaseItemKind,
        @Transient val item: BaseItem? = null,
        val seasonEpisode: SeasonEpisode? = null,
    ) : Destination() {
        override fun toString(): String =
            "MediaItem(itemId=$itemId, type=$type, seasonEpisode=$seasonEpisode, collectionType=${item?.data?.collectionType})"
    }

    @Serializable
    data class Playback(
        val itemId: UUID,
        val positionMs: Long,
        @Transient val item: BaseItem? = null,
        val startIndex: Int? = null,
        val shuffle: Boolean = false,
        val itemPlayback: ItemPlayback? = null,
    ) : Destination(true) {
        override fun toString(): String = "Playback(itemId=$itemId, positionMs=$positionMs)"

        constructor(item: BaseItem) : this(item.id, item.resumeMs ?: 0, item)
    }

    @Serializable
    data class FilteredCollection(
        val itemId: UUID,
        val filter: GetItemsFilter,
        val recursive: Boolean,
    ) : Destination(false)

    @Serializable
    data object LiveTvGuide : Destination(false)

    @Serializable
    data object UpdateApp : Destination(true)

    @Serializable
    data object License : Destination(true)

    @Serializable
    data object Debug : Destination(true)
}
