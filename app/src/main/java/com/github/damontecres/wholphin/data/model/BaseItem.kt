package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisode
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.seasonEpisode
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks

@Serializable
data class BaseItem(
    val data: BaseItemDto,
    val imageUrl: String?,
    val backdropImageUrl: String? = null,
) {
    @Transient val id = data.id

    @Transient val type = data.type

    @Transient val name = data.name

    @Transient
    val title = if (type == BaseItemKind.EPISODE) data.seriesName else name

    @Transient
    val subtitle =
        if (type == BaseItemKind.EPISODE) data.seasonEpisode + " - " + name else data.productionYear?.toString()

    @Transient
    val resumeMs =
        data.userData
            ?.playbackPositionTicks
            ?.ticks
            ?.inWholeMilliseconds

    @Transient
    val indexNumber = data.indexNumber ?: dateAsIndex()

    private fun dateAsIndex(): Int? =
        data.premiereDate
            ?.let {
                it.year.toString() +
                    it.monthValue.toString().padStart(2, '0') +
                    it.dayOfMonth.toString().padStart(2, '0')
            }?.toIntOrNull()

    fun destination(): Destination {
        val result =
            // Redirect episodes & seasons to their series if possible
            when (type) {
                BaseItemKind.EPISODE -> {
                    indexNumber?.let { episode ->
                        data.parentIndexNumber?.let { season ->
                            Destination.SeriesOverview(
                                data.seriesId!!,
                                BaseItemKind.SERIES,
                                this,
                                SeasonEpisode(season, episode),
                            )
                        }
                    } ?: Destination.MediaItem(id, type, this)
                }

                BaseItemKind.SEASON ->
                    data.indexNumber?.let { season ->
                        Destination.SeriesOverview(
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
            useSeriesForPrimary: Boolean = false,
        ): BaseItem {
            val backdropImageUrl =
                if (dto.type == BaseItemKind.EPISODE) {
                    val seriesId = dto.seriesId
                    if (seriesId != null) {
                        api.imageApi.getItemImageUrl(seriesId, ImageType.BACKDROP)
                    } else {
                        api.imageApi.getItemImageUrl(dto.id, ImageType.BACKDROP)
                    }
                } else {
                    api.imageApi.getItemImageUrl(dto.id, ImageType.BACKDROP)
                }
            val primaryImageUrl =
                if (useSeriesForPrimary && dto.type == BaseItemKind.EPISODE) {
                    val seriesId = dto.seriesId
                    if (seriesId != null) {
                        api.imageApi.getItemImageUrl(seriesId, ImageType.PRIMARY)
                    } else {
                        api.imageApi.getItemImageUrl(dto.id, ImageType.PRIMARY)
                    }
                } else if (dto.imageTags == null || dto.imageTags!![ImageType.PRIMARY] == null) {
                    // TODO is this a bad assumption?
                    null
                } else {
                    api.imageApi.getItemImageUrl(dto.id, ImageType.PRIMARY)
                }
            return BaseItem(
                dto,
                primaryImageUrl,
                backdropImageUrl,
            )
        }
    }
}

val BaseItemDto.aspectRatioFloat: Float? get() = width?.let { w -> height?.let { h -> w.toFloat() / h.toFloat() } }
