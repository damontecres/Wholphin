package com.github.damontecres.wholphin.ui.main.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.AppChoicePreference
import com.github.damontecres.wholphin.preferences.AppClickablePreference
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppSliderPreference
import com.github.damontecres.wholphin.preferences.AppSwitchPreference
import com.github.damontecres.wholphin.preferences.PrefContentScale
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.preferences.ComposablePreference
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun HomeRowSettings(
    title: String,
    preferenceOptions: List<PreferenceGroup<HomeRowViewOptions>>,
    viewOptions: HomeRowViewOptions,
    onViewOptionsChange: (HomeRowViewOptions) -> Unit,
    onApplyApplyAll: () -> Unit,
    modifier: Modifier = Modifier,
    defaultViewOptions: HomeRowViewOptions = HomeRowViewOptions(),
) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    Column(modifier = modifier) {
        TitleText(title)
        LazyColumn {
            preferenceOptions.forEachIndexed { groupIndex, prefGroup ->
                if (preferenceOptions.size > 1) {
                    item {
                        TitleText(stringResource(prefGroup.title))
                    }
                }
                itemsIndexed(prefGroup.preferences) { index, pref ->
                    pref as AppPreference<HomeRowViewOptions, Any>
                    val interactionSource = remember { MutableInteractionSource() }
                    val value = pref.getter.invoke(viewOptions)
                    ComposablePreference(
                        preference = pref,
                        value = value,
                        onNavigate = {},
                        onValueChange = { newValue ->
                            onViewOptionsChange.invoke(pref.setter(viewOptions, newValue))
                        },
                        interactionSource = interactionSource,
                        onClickPreference = { pref ->
                            when (pref) {
                                Options.ViewOptionsReset -> {
                                    onViewOptionsChange.invoke(defaultViewOptions)
                                }

                                Options.ViewOptionsApplyAll -> {
                                    onApplyApplyAll.invoke()
                                }

                                Options.ViewOptionsUseThumb -> {
                                    onViewOptionsChange.invoke(
                                        HomeRowViewOptions(
                                            heightDp = Cards.HEIGHT_EPISODE,
                                            spacing = 20,
                                            imageType = ViewOptionImageType.THUMB,
                                            aspectRatio = AspectRatio.WIDE,
                                            episodeImageType = ViewOptionImageType.THUMB,
                                            episodeAspectRatio = AspectRatio.WIDE,
                                        ),
                                    )
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .ifElse(
                                    groupIndex == 0 && index == 0,
                                    Modifier.focusRequester(firstFocus),
                                ),
                    )
                }
            }
        }
    }
}

internal object Options {
    val ViewOptionsCardHeight =
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
            title = R.string.use_series,
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
        AppClickablePreference<HomeRowViewOptions>(
            title = R.string.apply_all_rows,
        )

    val ViewOptionsReset =
        AppClickablePreference<HomeRowViewOptions>(
            title = R.string.reset,
        )

    val ViewOptionsUseThumb =
        AppClickablePreference<HomeRowViewOptions>(
            title = R.string.use_thumb_images,
        )

    val ViewOptionsEpisodeContentScale =
        AppChoicePreference<HomeRowViewOptions, PrefContentScale>(
            title = R.string.global_content_scale,
            defaultValue = PrefContentScale.FIT,
            displayValues = R.array.content_scale,
            getter = { it.contentScale },
            setter = { viewOptions, value -> viewOptions.copy(episodeContentScale = value) },
            indexToValue = { PrefContentScale.forNumber(it) },
            valueToIndex = { it.number },
        )

    val ViewOptionsEpisodeAspectRatio =
        AppChoicePreference<HomeRowViewOptions, AspectRatio>(
            title = R.string.aspect_ratio,
            defaultValue = AspectRatio.TALL,
            displayValues = R.array.aspect_ratios,
            getter = { it.episodeAspectRatio },
            setter = { viewOptions, value -> viewOptions.copy(episodeAspectRatio = value) },
            indexToValue = { AspectRatio.entries[it] },
            valueToIndex = { it.ordinal },
        )

    val ViewOptionsEpisodeImageType =
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
                viewOptions.copy(episodeImageType = value, episodeAspectRatio = aspectRatio)
            },
            indexToValue = { ViewOptionImageType.entries[it] },
            valueToIndex = { it.ordinal },
        )

    val OPTIONS =
        listOf(
            PreferenceGroup(
                title = R.string.general,
                preferences =
                    listOf(
                        ViewOptionsCardHeight,
                        ViewOptionsSpacing,
                        ViewOptionsImageType,
                        ViewOptionsAspectRatio,
                        ViewOptionsContentScale,
                        // TODO
//                ViewOptionsShowTitles,
                        ViewOptionsUseSeries,
                    ),
            ),
            PreferenceGroup(
                title = R.string.more,
                preferences =
                    listOf(
//                      ViewOptionsApplyAll,
                        ViewOptionsUseThumb,
                        ViewOptionsReset,
                    ),
            ),
        )

    val OPTIONS_EPISODES =
        listOf(
            PreferenceGroup(
                title = R.string.general,
                preferences =
                    listOf(
                        ViewOptionsCardHeight,
                        ViewOptionsSpacing,
                        ViewOptionsImageType,
                        ViewOptionsAspectRatio,
                        ViewOptionsContentScale,
                    ),
            ),
            PreferenceGroup(
                title = R.string.for_episodes,
                preferences =
                    listOf(
                        ViewOptionsUseSeries,
                        ViewOptionsEpisodeImageType,
                        ViewOptionsEpisodeAspectRatio,
                        ViewOptionsEpisodeContentScale,
                    ),
            ),
            PreferenceGroup(
                title = R.string.more,
                preferences =
                    listOf(
                        ViewOptionsUseThumb,
                        ViewOptionsReset,
                    ),
            ),
        )

    val GENRE_OPTIONS =
        listOf(
            PreferenceGroup(
                title = R.string.general,
                preferences =
                    listOf(
                        ViewOptionsCardHeight,
                        ViewOptionsSpacing,
                        ViewOptionsReset,
                    ),
            ),
        )
}
