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
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import java.util.UUID

@Serializable
sealed class HomeRowConfig {
    abstract val id: UUID
    abstract val viewOptions: HomeRowViewOptions

    abstract fun updateViewOptions(viewOptions: HomeRowViewOptions): HomeRowConfig

    @Serializable
    data class ContinueWatching(
        override val id: UUID,
        val combined: Boolean,
        override val viewOptions: HomeRowViewOptions,
    ) : HomeRowConfig() {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): HomeRowConfig = this.copy(viewOptions = viewOptions)
    }

    @Serializable
    data class RecentlyAdded(
        override val id: UUID,
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions,
    ) : HomeRowConfig() {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): HomeRowConfig = this.copy(viewOptions = viewOptions)
    }

    @Serializable
    data class RecentlyReleased(
        override val id: UUID,
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions,
    ) : HomeRowConfig() {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): HomeRowConfig = this.copy(viewOptions = viewOptions)
    }

    @Serializable
    data class Genres(
        override val id: UUID,
        val parentId: UUID,
        override val viewOptions: HomeRowViewOptions,
    ) : HomeRowConfig() {
        override fun updateViewOptions(viewOptions: HomeRowViewOptions): HomeRowConfig = this.copy(viewOptions = viewOptions)
    }
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
