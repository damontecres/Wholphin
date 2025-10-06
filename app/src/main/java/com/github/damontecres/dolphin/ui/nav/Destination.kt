@file:UseSerializers(UuidSerializer::class)

package com.github.damontecres.dolphin.ui.nav

import androidx.navigation3.runtime.NavKey
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.ui.detail.series.SeasonEpisode
import com.github.damontecres.dolphin.util.UuidSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

@Serializable
sealed class Destination(
    val fullScreen: Boolean = false,
) : NavKey {
    @Serializable
    data object Setup : Destination(true)

    @Serializable
    data object ServerList : Destination(true)

    @Serializable
    data object UserList : Destination(true)

    @Serializable
    data class Main(
        val id: Long = 0L,
    ) : Destination()

    @Serializable
    data object Settings : Destination(true)

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
    ) : Destination(true) {
        override fun toString(): String = "Playback(itemId=$itemId, positionMs=$positionMs)"
    }

    @Serializable
    data object License : Destination(true)
}
