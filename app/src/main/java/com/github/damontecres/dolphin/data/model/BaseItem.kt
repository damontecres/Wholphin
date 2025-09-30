package com.github.damontecres.dolphin.data.model

import com.github.damontecres.dolphin.ui.components.details.SeasonEpisode
import com.github.damontecres.dolphin.ui.nav.Destination
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

@Serializable
data class BaseItem(
    val data: BaseItemDto,
    val imageUrl: String?,
) {
    @Transient val id = data.id

    @Transient val type = data.type

    @Transient val name = data.name

    fun destination(): Destination.MediaItem {
        val result =
            // Redirect episodes & seasons to their series if possible
            when (type) {
                BaseItemKind.EPISODE -> {
                    data.indexNumber?.let { episode ->
                        data.parentIndexNumber?.let { season ->
                            Destination.MediaItem(
                                data.seriesId!!,
                                BaseItemKind.SERIES,
                                this,
                                SeasonEpisode(season, episode),
                            )
                        }
                    } ?: Destination.MediaItem(id, type, this)
                }

                BaseItemKind.SEASON ->
                    data.parentIndexNumber?.let { season ->
                        Destination.MediaItem(
                            data.seriesId!!,
                            BaseItemKind.SERIES,
                            this,
                            SeasonEpisode(season, 0),
                        )
                    } ?: Destination.MediaItem(id, type, this)

                else -> Destination.MediaItem(id, type, this)
            }
        return result
    }

    companion object {
        fun from(
            dto: BaseItemDto,
            api: ApiClient,
        ) = BaseItem(dto, api.imageApi.getItemImageUrl(dto.id, ImageType.PRIMARY))
    }
}
