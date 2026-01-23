@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.preferences.PrefContentScale
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

@Serializable
sealed class HomeRowConfig {
    abstract val viewOptions: HomeRowViewOptions

    @Serializable
    data class ContinueWatching(
        val combined: Boolean,
        override val viewOptions: HomeRowViewOptions,
    ) : HomeRowConfig()

    @Serializable
    data class RecentlyAdded(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions,
    ) : HomeRowConfig()

    @Serializable
    data class RecentlyReleased(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions,
    ) : HomeRowConfig()

    @Serializable
    data class Genres(
        val genreId: UUID,
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions,
    ) : HomeRowConfig()
}

data class HomeRowConfigDisplay(
    val title: String,
    val config: HomeRowConfig,
)

@Serializable
data class HomeRowViewOptions(
    val heightDp: Int = Cards.HEIGHT_2X3_DP,
    val spacing: Int = 16,
    val contentScale: PrefContentScale = PrefContentScale.FIT,
    val aspectRatio: AspectRatio = AspectRatio.TALL,
    val imageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
    val showTitles: Boolean = true,
    val useSeries: Boolean = true,
)
