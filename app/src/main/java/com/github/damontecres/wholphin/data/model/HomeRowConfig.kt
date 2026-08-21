@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.preferences.PrefContentScale
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

@Serializable
sealed interface HomeRowConfig {
    /**
     * The view options the user explicitly chose, or null to follow the default
     *
     * Null is the normal state: a row nobody has sized deserializes to it.
     */
    val viewOptions: HomeRowViewOptions?

    fun updateViewOptions(viewOptions: HomeRowViewOptions?): HomeRowConfig

    /**
     * Continue watching media that the user has started but not finished
     */
    @Serializable
    @SerialName("ContinueWatching")
    data class ContinueWatching(
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): ContinueWatching = this.copy(viewOptions = viewOptions)
    }

    /**
     * Next up row for next episodes in a series the user has started
     */
    @Serializable
    @SerialName("NextUp")
    data class NextUp(
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): NextUp = this.copy(viewOptions = viewOptions)
    }

    /**
     * Combined [ContinueWatching] and [NextUp]
     */
    @Serializable
    @SerialName("ContinueWatchingCombined")
    data class ContinueWatchingCombined(
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): ContinueWatchingCombined = this.copy(viewOptions = viewOptions)
    }

    /**
     * Media recently added to a library
     */
    @Serializable
    @SerialName("RecentlyAdded")
    data class RecentlyAdded(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): RecentlyAdded = this.copy(viewOptions = viewOptions)
    }

    /**
     * Media recently released (premiere date) in a library
     */
    @Serializable
    @SerialName("RecentlyReleased")
    data class RecentlyReleased(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): RecentlyReleased = this.copy(viewOptions = viewOptions)
    }

    /**
     * Row of a genres in a library
     */
    @Serializable
    @SerialName("Genres")
    data class Genres(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): Genres = this.copy(viewOptions = viewOptions)
    }

    /**
     * Row of a studios in a library
     */
    @Serializable
    @SerialName("Studios")
    data class Studios(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): Studios = this.copy(viewOptions = viewOptions)
    }

    /**
     * Favorites for a specific type
     */
    @Serializable
    @SerialName("Favorite")
    data class Favorite(
        val kind: BaseItemKind,
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): Favorite = this.copy(viewOptions = viewOptions)
    }

    /**
     * Currently recording
     */
    @Serializable
    @SerialName("Recordings")
    data class Recordings(
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): Recordings = this.copy(viewOptions = viewOptions)
    }

    /**
     * Programs on now/recommended
     */
    @Serializable
    @SerialName("TvPrograms")
    data class TvPrograms(
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): TvPrograms = this.copy(viewOptions = viewOptions)
    }

    /**
     * Live TV channels
     */
    @Serializable
    @SerialName("TvChannels")
    data class TvChannels(
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): TvChannels = this.copy(viewOptions = viewOptions)
    }

    /**
     * Fetch suggestions from [com.github.damontecres.wholphin.services.SuggestionService] for the given parent ID
     */
    @Serializable
    @SerialName("Suggestions")
    data class Suggestions(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): Suggestions = this.copy(viewOptions = viewOptions)
    }

    /**
     * Fetch by parent ID such as a library, collection, or playlist with optional simple sorting
     */
    @Serializable
    @SerialName("ByParent")
    data class ByParent(
        val parentId: UUID,
        val recursive: Boolean = false,
        val sort: SortAndDirection? = null,
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): ByParent = this.copy(viewOptions = viewOptions)
    }

    /**
     * An arbitrary [GetItemsRequest] allowing to query for anything
     */
    @Serializable
    @SerialName("GetItems")
    data class GetItems(
        val name: String,
        val getItems: GetItemsRequest,
        override val viewOptions: HomeRowViewOptions? = null,
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions?): GetItems = this.copy(viewOptions = viewOptions)
    }
}

/**
 * Root class for home page settings
 *
 * Contains the list of rows and a version
 */
@Serializable
@SerialName("HomePageSettings")
data class HomePageSettings(
    val rows: List<HomeRowConfig>,
    val version: Int,
) {
    companion object {
        val EMPTY = HomePageSettings(listOf(), SUPPORTED_HOME_PAGE_SETTINGS_VERSION)
    }
}

/**
 * This is the max version supported by this version of the app
 */
const val SUPPORTED_HOME_PAGE_SETTINGS_VERSION = 1

/**
 * View options for displaying a row
 *
 * Allows for changing things like height or aspect ratio
 */
@Serializable
data class HomeRowViewOptions(
    val heightDp: Int = Cards.HEIGHT_2X3_DP,
    val spacing: Int = 16,
    val contentScale: PrefContentScale = PrefContentScale.FILL,
    val aspectRatio: AspectRatio = AspectRatio.TALL,
    val imageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
    val showTitles: Boolean = false,
    val useSeries: Boolean = true,
    val episodeContentScale: PrefContentScale = PrefContentScale.FILL,
    val episodeAspectRatio: AspectRatio = AspectRatio.TALL,
    val episodeImageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
) {
    companion object {
        val genreDefault =
            HomeRowViewOptions(
                heightDp = Cards.HEIGHT_EPISODE,
                aspectRatio = AspectRatio.WIDE,
            )

        val liveTvDefault =
            HomeRowViewOptions(
                heightDp = 96,
                aspectRatio = AspectRatio.WIDE,
                contentScale = PrefContentScale.FIT,
            )

        val musicDefault =
            HomeRowViewOptions(
                heightDp = Cards.HEIGHT_EPISODE,
                aspectRatio = AspectRatio.SQUARE,
            )

        val episodeDefault =
            HomeRowViewOptions(
                heightDp = Cards.HEIGHT_EPISODE,
                aspectRatio = AspectRatio.WIDE,
            )

        fun forCollectionType(collectionType: CollectionType?): HomeRowViewOptions =
            when (collectionType) {
                CollectionType.MUSIC -> {
                    musicDefault
                }

                CollectionType.HOMEVIDEOS,
                CollectionType.MUSICVIDEOS,
                -> {
                    HomeRowViewOptions(aspectRatio = AspectRatio.WIDE)
                }

                CollectionType.LIVETV -> {
                    liveTvDefault
                }

                else -> {
                    HomeRowViewOptions()
                }
            }
    }
}

/**
 * Listed exhaustively so that a new [HomeRowConfig] has to say whether it is scoped to an item
 */
val HomeRowConfig.parentItemId: UUID?
    get() =
        when (this) {
            is HomeRowConfig.ByParent -> parentId

            is HomeRowConfig.Genres -> parentId

            is HomeRowConfig.RecentlyAdded -> parentId

            is HomeRowConfig.RecentlyReleased -> parentId

            is HomeRowConfig.Studios -> parentId

            is HomeRowConfig.Suggestions -> parentId

            is HomeRowConfig.ContinueWatching,
            is HomeRowConfig.ContinueWatchingCombined,
            is HomeRowConfig.Favorite,
            is HomeRowConfig.GetItems,
            is HomeRowConfig.NextUp,
            is HomeRowConfig.Recordings,
            is HomeRowConfig.TvChannels,
            is HomeRowConfig.TvPrograms,
            -> null
        }

/**
 * [collectionType] is that of the row's [parentItemId], and is ignored by rows whose shape does
 * not depend on what they are pointed at. Listed exhaustively so that a new [HomeRowConfig] has
 * to say which it is.
 */
fun HomeRowConfig.defaultViewOptions(collectionType: CollectionType?): HomeRowViewOptions =
    when (this) {
        is HomeRowConfig.ByParent,
        is HomeRowConfig.RecentlyAdded,
        is HomeRowConfig.RecentlyReleased,
        is HomeRowConfig.Suggestions,
        -> {
            HomeRowViewOptions.forCollectionType(collectionType)
        }

        is HomeRowConfig.Genres,
        is HomeRowConfig.Studios,
        -> {
            HomeRowViewOptions.genreDefault
        }

        is HomeRowConfig.TvChannels,
        is HomeRowConfig.TvPrograms,
        -> {
            HomeRowViewOptions.liveTvDefault
        }

        is HomeRowConfig.Favorite -> {
            if (kind == BaseItemKind.EPISODE) {
                HomeRowViewOptions.episodeDefault
            } else {
                HomeRowViewOptions()
            }
        }

        is HomeRowConfig.ContinueWatching,
        is HomeRowConfig.ContinueWatchingCombined,
        is HomeRowConfig.GetItems,
        is HomeRowConfig.NextUp,
        is HomeRowConfig.Recordings,
        -> {
            HomeRowViewOptions()
        }
    }

fun HomeRowConfig.resolveViewOptions(collectionType: CollectionType?): HomeRowViewOptions =
    viewOptions ?: defaultViewOptions(collectionType)
