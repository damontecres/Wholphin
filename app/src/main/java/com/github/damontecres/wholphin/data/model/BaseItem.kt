package com.github.damontecres.wholphin.data.model

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication
import com.github.damontecres.wholphin.ui.abbreviateNumber
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.detail.music.artistsString
import com.github.damontecres.wholphin.ui.detail.series.SeasonEpisodeIds
import com.github.damontecres.wholphin.ui.dot
import com.github.damontecres.wholphin.ui.formatDateTime
import com.github.damontecres.wholphin.ui.formatDuration
import com.github.damontecres.wholphin.ui.getDateFormatter
import com.github.damontecres.wholphin.ui.joinNotBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.playback.playable
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.seasonEpisodePadded
import com.github.damontecres.wholphin.ui.seriesProductionYears
import com.github.damontecres.wholphin.ui.timeRemaining
import com.github.damontecres.wholphin.ui.toServerString
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.extensions.ticks
import java.util.Locale
import java.util.UUID
import kotlin.time.Duration

/**
 * Wrapper for [BaseItemDto] with shortcuts for various UI elements
 */
@Serializable
@Stable
data class BaseItem(
    val data: BaseItemDto,
    val useSeriesForPrimary: Boolean = false,
    val imageUrlOverride: String? = null,
    val destinationOverride: Destination? = null,
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

    val subtitle: String? by lazy {
        when (type) {
            BaseItemKind.EPISODE -> listOf(data.seasonEpisode, name).joinNotBlank(" - ")
            BaseItemKind.SERIES -> data.seriesProductionYears
            BaseItemKind.AUDIO -> listOf(data.album, artistsString).joinNotBlank(" - ")
            else -> data.productionYear?.toString()
        }
    }

    val subtitleLong: String? by lazy {
        if (type == BaseItemKind.EPISODE) {
            buildList {
                add(data.seasonEpisodePadded)
                add(data.name)
                add(data.premiereDate?.let { formatDateTime(it) })
            }.joinNotBlank(" - ")
        } else {
            data.productionYear?.toString()
        }
    }

    val canDelete: Boolean get() = data.canDelete == true

    val aspectRatio: Float? get() = data.primaryImageAspectRatio?.toFloat()?.takeIf { it > 0 }

    val indexNumber get() = data.indexNumber

    val playbackPosition get() = data.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val resumeMs get() = playbackPosition.inWholeMilliseconds

    val played get() = data.userData?.played ?: false

    val favorite get() = data.userData?.isFavorite ?: false

    val timeRemainingOrRuntime: Duration?
        get() =
            when (type) {
                BaseItemKind.PROGRAM -> null
                else -> data.timeRemaining ?: data.runTimeTicks?.ticks
            }

    /**
     * Contains pre computed UI elements that would be expensive to create on the main thread
     */
    @Transient
    val ui =
        BaseItemUi(
            episodeCornerText =
                data.indexNumber?.let { "E$it" }
                    ?: data.premiereDate?.let(::formatDateTime),
            episodeUnplayedCornerText =
                if (type == BaseItemKind.SERIES ||
                    type == BaseItemKind.SEASON ||
                    type == BaseItemKind.EPISODE ||
                    type == BaseItemKind.BOX_SET
                ) {
                    data.indexNumber?.let { "E$it" }
                        ?: data.userData
                            ?.unplayedItemCount
                            ?.takeIf { it > 0 }
                            ?.let { abbreviateNumber(it) }
                } else {
                    null
                },
            quickDetails =
                QuickDetailsData(
                    basic =
                        buildAnnotatedString {
                            val details =
                                buildList {
                                    if (type == BaseItemKind.EPISODE) {
                                        data.seasonEpisode?.let(::add)
                                        data.premiereDate?.let { add(getDateFormatter().format(it)) }
                                    } else if (type == BaseItemKind.SERIES) {
                                        data.seriesProductionYears?.let(::add)
                                    } else if (type == BaseItemKind.PHOTO) {
                                        if (data.productionYear != null) {
                                            add(data.productionYear!!.toString())
                                        } else if (data.premiereDate != null) {
                                            add(data.premiereDate!!.toLocalDate().toString())
                                        }
                                    } else if (type == BaseItemKind.BOX_SET) {
                                        data.productionYear?.let { add(it.toString()) }
                                        data.childCount?.let { add("$it items") }
                                    } else if (type == BaseItemKind.PROGRAM) {
                                        data.channelName?.let(::add)
                                        if (data.isSeries == true) {
                                            // TV episode
                                            data.seasonEpisode?.let(::add)
                                            data.premiereDate?.let {
                                                add(getDateFormatter().format(it))
                                            }
                                        } else {
                                            data.productionYear?.let { add(it.toString()) }
                                        }
                                    } else {
                                        data.productionYear?.let { add(it.toString()) }
                                    }
                                    data.runTimeTicks
                                        ?.ticks
                                        ?.roundMinutes
                                        ?.takeIf { it > Duration.ZERO }
                                        ?.let { add(WholphinApplication.instance.resources.formatDuration(it)) }
                                    data.timeRemaining
                                        ?.roundMinutes
                                        ?.let {
                                            val resources = WholphinApplication.instance.resources
                                            add(
                                                resources.getString(
                                                    R.string.time_left,
                                                    resources.formatDuration(it),
                                                ),
                                            )
                                        }
                                }
                            details.forEachIndexed { index, string ->
                                append(string)
                                if (index != details.lastIndex) {
                                    dot()
                                }
                            }
                        },
                    officialRating =
                        data.officialRating?.let {
                            buildAnnotatedString {
                                dot()
                                append(it)
                            }
                        },
                    communityRating =
                        data.communityRating?.let {
                            buildAnnotatedString {
                                dot()
                                append(String.format(Locale.getDefault(), "%.1f", it))
                                appendInlineContent(id = "star")
                            }
                        },
                    criticRating =
                        data.criticRating?.let {
                            buildAnnotatedString {
                                dot()
                                append("${it.toInt()}%")
                                if (it >= 60f) {
                                    appendInlineContent(id = "fresh")
                                } else {
                                    appendInlineContent(id = "rotten")
                                }
                            }
                        },
                ),
        )

    private fun dateAsIndex(): Int? =
        data.premiereDate
            ?.let {
                it.year.toString() +
                    it.monthValue.toString().padStart(2, '0') +
                    it.dayOfMonth.toString().padStart(2, '0')
            }?.toIntOrNull()

    /**
     * Convert this [BaseItem] into a [Destination] to navigate to its page in the app
     */
    fun destination(index: Int? = null): Destination {
        if (destinationOverride != null) return destinationOverride
        if (data.extraType != null || type == BaseItemKind.TRAILER) {
            // Extras including trailers should always play directly
            return Destination.Playback(id, 0)
        }
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

                BaseItemKind.TV_CHANNEL -> {
                    Destination.Playback(
                        itemId = id,
                        positionMs = 0L,
                    )
                }

                BaseItemKind.PROGRAM -> {
                    val channelId = data.channelId
                    if (channelId != null) {
                        Destination.Playback(
                            itemId = channelId,
                            positionMs = 0L,
                        )
                    } else {
                        Destination.MediaItem(this)
                    }
                }

                else -> {
                    Destination.MediaItem(this)
                }
            }
        return result
    }

    companion object {
        @Deprecated("Use regular constructor instead")
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
    val quickDetails: QuickDetailsData,
)

data class QuickDetailsData(
    val basic: AnnotatedString,
    val officialRating: AnnotatedString? = null,
    val criticRating: AnnotatedString? = null,
    val communityRating: AnnotatedString? = null,
)

/**
 * Create the special [Destination.FilteredCollection] for the given genre information
 */
fun createGenreDestination(
    genreId: UUID,
    genreName: String,
    parentId: UUID,
    parentName: String?,
    includeItemTypes: List<BaseItemKind>?,
    collectionType: CollectionType,
) = Destination.FilteredCollection(
    itemId = parentId,
    parentType = BaseItemKind.GENRE,
    filter =
        CollectionFolderFilter(
            nameOverride =
                listOfNotNull(
                    genreName,
                    parentName,
                ).joinToString(" "),
            filter =
                GetItemsFilter(
                    genres = listOf(genreId),
                    includeItemTypes = includeItemTypes,
                ),
            useSavedLibraryDisplayInfo = true,
            libraryDisplayInfoIdOverride = "${parentId.toServerString()}_genres",
        ),
    recursive = true,
    collectionType = collectionType,
)

fun createStudioDestination(
    studioId: UUID,
    name: String,
    parentId: UUID,
    parentName: String?,
    includeItemTypes: List<BaseItemKind>?,
) = Destination.FilteredCollection(
    itemId = parentId,
    parentType = BaseItemKind.STUDIO,
    filter =
        CollectionFolderFilter(
            nameOverride =
                listOfNotNull(
                    name,
                    parentName,
                ).joinToString(" "),
            filter =
                GetItemsFilter(
                    studios = listOf(studioId),
                    includeItemTypes = includeItemTypes,
                ),
            useSavedLibraryDisplayInfo = true,
            libraryDisplayInfoIdOverride = "${parentId.toServerString()}_studios",
        ),
    recursive = true,
    collectionType = CollectionType.UNKNOWN,
)

val BaseItem.studioNames get() = data.studios?.mapNotNull { it.name }.orEmpty()
