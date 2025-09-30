@file:UseSerializers(UuidSerializer::class)

package com.github.damontecres.dolphin.ui.nav

import androidx.navigation3.runtime.NavKey
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.ui.components.details.SeasonEpisode
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
    data object Main : Destination()

    @Serializable
    data object Settings : Destination(true)

    @Serializable
    data object Search : Destination()

    @Serializable
    data class MediaItem(
        val itemId: UUID,
        val type: BaseItemKind,
        @Transient val item: BaseItem? = null,
        val seasonEpisode: SeasonEpisode? = null,
    ) : Destination()

    @Serializable
    data class Playback(
        val itemId: UUID,
        val positionMs: Long,
        @Transient val item: BaseItem? = null,
    ) : Destination(true)
}
