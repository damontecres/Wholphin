package com.github.damontecres.wholphin.data.model

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.github.damontecres.wholphin.ui.DateFormatter
import com.github.damontecres.wholphin.ui.abbreviateNumber
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import com.github.damontecres.wholphin.ui.dot
import com.github.damontecres.wholphin.ui.formatDateTime
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.seasonEpisodePadded
import com.github.damontecres.wholphin.ui.seriesProductionYears
import com.github.damontecres.wholphin.ui.timeRemaining
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.extensions.ticks
import java.util.Locale
import kotlin.time.Duration

@Serializable
@Stable
data class BaseItem(
    val data: BaseItemDto,
    val useSeriesForPrimary: Boolean,
) : CardGridItem {
    val id get() = data.id

    override val gridId get() = id.toString()

    override val playable: Boolean
        get() = type.playable

    override val sortName: String
        get() = data.sortName ?: data.name ?: ""

    val type get() = data.type

    val name get() = data.name

    val title get() = if (type == BaseItemKind.EPISODE) data.seriesName else name

    val subtitle
        get() =
            when (type) {
                BaseItemKind.EPISODE -> data.seasonEpisode + " - " + name
                BaseItemKind.SERIES -> data.seriesProductionYears
                else -> data.productionYear?.toString()
            }

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
    val aspectRatio: Float? = data.primaryImageAspectRatio?.toFloat()?.takeIf { it > 0 }

    val indexNumber get() = data.indexNumber

    val playbackPosition get() = data.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val resumeMs get() = playbackPosition.inWholeMilliseconds

    val played get() = data.userData?.played ?: false

    val favorite get() = data.userData?.isFavorite ?: false

    @Transient
    val timeRemainingOrRuntime: Duration? = data.timeRemaining ?: data.runTimeTicks?.ticks

    @Transient
    val ui =
        BaseItemUi(
            episodeCornerText =
                data.indexNumber?.let { "E$it" }
                    ?: data.premiereDate?.let(::formatDateTime),
            episodeUnplayedCornerText =
                if (type == BaseItemKind.SERIES || type == BaseItemKind.SEASON || type == BaseItemKind.BOX_SET) {
                    data.indexNumber?.let { "E$it" }
                        ?: data.userData
                            ?.unplayedItemCount
                            ?.takeIf { it > 0 }
                            ?.let { abbreviateNumber(it) }
                } else {
                    null
                },
            quickDetails =
                buildAnnotatedString {
                    val details =
                        buildList {
                            if (type == BaseItemKind.EPISODE) {
                                data.seasonEpisode?.let(::add)
                                data.premiereDate?.let { add(DateFormatter.format(it)) }
                            } else if (type == BaseItemKind.SERIES) {
                                data.seriesProductionYears?.let(::add)
                            } else if (type == BaseItemKind.PHOTO) {
                                if (data.productionYear != null) {
                                    add(data.productionYear!!.toString())
                                } else if (data.premiereDate != null) {
                                    add(data.premiereDate!!.toLocalDate().toString())
                                }
                            } else {
                                data.productionYear?.let { add(it.toString()) }
                            }
                            data.runTimeTicks
                                ?.ticks
                                ?.roundMinutes
                                ?.let { add(it.toString()) }
                            data.timeRemaining
                                ?.roundMinutes
                                ?.let { add("$it left") }
                        }
                    details.forEachIndexed { index, string ->
                        append(string)
                        if (index != details.lastIndex) {
                            dot()
                        }
                    }
                    // TODO time remaining

                    data.officialRating?.let {
                        dot()
                        append(it)
                    }
                    data.communityRating?.let {
                        dot()
                        append(String.format(Locale.getDefault(), "%.1f", it))
                        appendInlineContent(id = "star")
                    }
                    data.criticRating?.let {
                        dot()
                        append("${it.toInt()}%")
                        if (it >= 60f) {
                            appendInlineContent(id = "fresh")
                        } else {
                            appendInlineContent(id = "rotten")
                        }
                    }
                },
        )

    private fun dateAsIndex(): Int? =
        data.premiereDate
            ?.let {
                it.year.toString() +
                    it.monthValue.toString().padStart(2, '0') +
                    it.dayOfMonth.toString().padStart(2, '0')
            }?.toIntOrNull()

    fun destination(index: Int? = null): Destination {
        val result =
            // Redirect episodes & seasons to their series if possible
            when (type) {
                BaseItemKind.EPISODE -> {
                    data.seasonId?.let { seasonId ->
                        Destination.SeriesOverview(
                            data.seriesId!!,
                            BaseItemKind.SERIES,
                            SeasonEpisodeIds(seasonId, data.parentIndexNumber, id, indexNumber),
                        )
                    } ?: Destination.MediaItem(this)
                }

                BaseItemKind.SEASON -> {
                    Destination.SeriesOverview(
                        data.seriesId!!,
                        BaseItemKind.SERIES,
                        SeasonEpisodeIds(id, indexNumber, null, null),
                    )
                }

                else -> {
                    Destination.MediaItem(this)
                }
            }
        return result
    }

    companion object {
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

@Immutable
data class BaseItemUi(
    val episodeCornerText: String?,
    val episodeUnplayedCornerText: String?,
    val quickDetails: AnnotatedString,
)
