@file:UseSerializers(UUIDSerializer::class)

package com.github.damontecres.wholphin.data.model

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppChoicePreference
import com.github.damontecres.wholphin.preferences.AppClickablePreference
import com.github.damontecres.wholphin.preferences.AppSliderPreference
import com.github.damontecres.wholphin.preferences.AppSwitchPreference
import com.github.damontecres.wholphin.preferences.PrefContentScale
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

@Serializable
sealed interface HomeRowConfig {
    val viewOptions: HomeRowViewOptions

    fun updateViewOptions(viewOptions: HomeRowViewOptions): HomeRowConfig

    /**
     * Continue watching media that the user has started but not finished
     */
    @Serializable
    @SerialName("ContinueWatching")
    data class ContinueWatching(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): ContinueWatching = this.copy(viewOptions = viewOptions)
    }

    /**
     * Next up row for next episodes in a series the user has started
     */
    @Serializable
    @SerialName("NextUp")
    data class NextUp(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): NextUp = this.copy(viewOptions = viewOptions)
    }

    /**
     * Combined [ContinueWatching] and [NextUp]
     */
    @Serializable
    @SerialName("ContinueWatchingCombined")
    data class ContinueWatchingCombined(
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): ContinueWatchingCombined = this.copy(viewOptions = viewOptions)
    }

    /**
     * Media recently added to a library
     */
    @Serializable
    @SerialName("RecentlyAdded")
    data class RecentlyAdded(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): RecentlyAdded = this.copy(viewOptions = viewOptions)
    }

    /**
     * Media recently released (premiere date) in a library
     */
    @Serializable
    @SerialName("RecentlyReleased")
    data class RecentlyReleased(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): RecentlyReleased = this.copy(viewOptions = viewOptions)
    }

    /**
     * Row of a genres in a library
     */
    @Serializable
    @SerialName("Genres")
    data class Genres(
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions =
            HomeRowViewOptions(
                heightDp = (Cards.HEIGHT_2X3_DP * .75f).toInt().let { it - it % 4 },
                aspectRatio = AspectRatio.WIDE,
            ),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): Genres = this.copy(viewOptions = viewOptions)
    }

    /**
     * Fetch by parent ID such as a library, collection, or playlist with optional simple sorting
     */
    @Serializable
    @SerialName("ByParent")
    data class ByParent(
        val parentId: UUID,
        val recursive: Boolean,
        val sort: SortAndDirection? = null,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): ByParent = this.copy(viewOptions = viewOptions)
    }

    /**
     * An arbitrary [GetItemsRequest] allowing to query for anything
     */
    @Serializable
    @SerialName("GetItems")
    data class GetItems(
        val name: String,
        val getItems: GetItemsRequest,
        override val viewOptions: HomeRowViewOptions = HomeRowViewOptions(),
    ) : HomeRowConfig {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): GetItems = this.copy(viewOptions = viewOptions)
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
    val version: Int = SUPPORTED_HOME_PAGE_SETTINGS_VERSION,
) {
    companion object {
        val EMPTY = HomePageSettings(listOf())
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
    val contentScale: PrefContentScale = PrefContentScale.FIT,
    val aspectRatio: AspectRatio = AspectRatio.TALL,
    val imageType: ViewOptionImageType = ViewOptionImageType.PRIMARY,
    val showTitles: Boolean = true,
    val useSeries: Boolean = true,
) {
    companion object {
        val ViewOptionsColumns =
            AppSliderPreference<HomeRowViewOptions>(
                title = R.string.height,
                defaultValue = Cards.HEIGHT_2X3_DP.toLong(),
                min = 64L,
                max = Cards.HEIGHT_2X3_DP + 64L,
                interval = 4,
                getter = { it.heightDp.toLong() },
                setter = { prefs, value -> prefs.copy(heightDp = value.toInt()) },
            )
        val ViewOptionsSpacing =
            AppSliderPreference<HomeRowViewOptions>(
                title = R.string.spacing,
                defaultValue = 16,
                min = 0,
                max = 32,
                interval = 2,
                getter = { it.spacing.toLong() },
                setter = { prefs, value -> prefs.copy(spacing = value.toInt()) },
            )

        val ViewOptionsContentScale =
            AppChoicePreference<HomeRowViewOptions, PrefContentScale>(
                title = R.string.global_content_scale,
                defaultValue = PrefContentScale.FIT,
                displayValues = R.array.content_scale,
                getter = { it.contentScale },
                setter = { viewOptions, value -> viewOptions.copy(contentScale = value) },
                indexToValue = { PrefContentScale.forNumber(it) },
                valueToIndex = { it.number },
            )

        val ViewOptionsAspectRatio =
            AppChoicePreference<HomeRowViewOptions, AspectRatio>(
                title = R.string.aspect_ratio,
                defaultValue = AspectRatio.TALL,
                displayValues = R.array.aspect_ratios,
                getter = { it.aspectRatio },
                setter = { viewOptions, value -> viewOptions.copy(aspectRatio = value) },
                indexToValue = { AspectRatio.entries[it] },
                valueToIndex = { it.ordinal },
            )

        val ViewOptionsShowTitles =
            AppSwitchPreference<HomeRowViewOptions>(
                title = R.string.show_titles,
                defaultValue = true,
                getter = { it.showTitles },
                setter = { vo, value -> vo.copy(showTitles = value) },
            )

        val ViewOptionsUseSeries =
            AppSwitchPreference<HomeRowViewOptions>(
                title = R.string.go_to_series, // TODO
                defaultValue = true,
                getter = { it.useSeries },
                setter = { vo, value -> vo.copy(useSeries = value) },
            )

        val ViewOptionsImageType =
            AppChoicePreference<HomeRowViewOptions, ViewOptionImageType>(
                title = R.string.image_type,
                defaultValue = ViewOptionImageType.PRIMARY,
                displayValues = R.array.image_types,
                getter = { it.imageType },
                setter = { viewOptions, value ->
                    val aspectRatio =
                        when (value) {
                            ViewOptionImageType.PRIMARY -> AspectRatio.TALL
                            ViewOptionImageType.THUMB -> AspectRatio.WIDE
                        }
                    viewOptions.copy(imageType = value, aspectRatio = aspectRatio)
                },
                indexToValue = { ViewOptionImageType.entries[it] },
                valueToIndex = { it.ordinal },
            )

        val ViewOptionsApplyAll =
            AppClickablePreference<ViewOptions>(
                title = R.string.apply_all_rows,
            )

        val ViewOptionsReset =
            AppClickablePreference<ViewOptions>(
                title = R.string.reset,
            )

        val OPTIONS =
            listOf(
                ViewOptionsImageType,
                ViewOptionsAspectRatio,
                // TODO
//                ViewOptionsShowTitles,
//                ViewOptionsUseSeries,
                ViewOptionsColumns,
                ViewOptionsSpacing,
                ViewOptionsContentScale,
                ViewOptionsApplyAll,
                ViewOptionsReset,
            )
    }
}
