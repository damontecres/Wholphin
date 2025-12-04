package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import com.github.damontecres.wholphin.ui.formatDateTime
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.seasonEpisodePadded
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import kotlin.time.Duration

@Serializable
data class BaseItem(
    val data: BaseItemDto,
    val useSeriesForPrimary: Boolean,
) {
    val id get() = data.id

    val type get() = data.type

    val name get() = data.name

    val title get() = if (type == BaseItemKind.EPISODE) data.seriesName else name

    val subtitle
        get() =
            if (type == BaseItemKind.EPISODE) data.seasonEpisode + " - " + name else data.productionYear?.toString()

    val subtitleLong: String? by lazy {
        if (type == BaseItemKind.EPISODE) {
            buildList {
                add(data.seasonEpisodePadded)
                add(data.name)
                add(data.premiereDate?.let { formatDateTime(it) })
            }.filterNotNull().joinToString(" - ")
        } else {
            data.productionYear?.toString()
        }
    }

    @Transient
    val indexNumber = data.indexNumber ?: dateAsIndex()

    val playbackPosition get() = data.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val resumeMs get() = playbackPosition.inWholeMilliseconds

    val played get() = data.userData?.played ?: false

    val favorite get() = data.userData?.isFavorite ?: false

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
                    data.seasonId?.let { seasonId ->
                        Destination.SeriesOverview(
                            data.seriesId!!,
                            BaseItemKind.SERIES,
                            this,
                            SeasonEpisodeIds(seasonId, data.parentIndexNumber, id, indexNumber),
                        )
                    } ?: Destination.MediaItem(id, type, this)
                }

                BaseItemKind.SEASON ->
                    Destination.SeriesOverview(
                        data.seriesId!!,
                        BaseItemKind.SERIES,
                        this,
                        SeasonEpisodeIds(id, indexNumber, null, null),
                    )

                else -> Destination.MediaItem(id, type, this)
            }
        return result
    }

    companion object {
        var primaryMaxWidth: Int? = null
            set(value) {
                Timber.v("primaryMaxWidth=$value")
//                field = value
            }
        var primaryMaxHeight: Int? = null
            set(value) {
                Timber.v("primaryMaxHeight=$value")
//                field = value
            }

        fun from(
            dto: BaseItemDto,
            api: ApiClient,
            useSeriesForPrimary: Boolean = false,
        ): BaseItem =
            BaseItem(
                dto,
                useSeriesForPrimary,
            )
    }
}

val BaseItemDto.aspectRatioFloat: Float? get() = width?.let { w -> height?.let { h -> w.toFloat() / h.toFloat() } }
