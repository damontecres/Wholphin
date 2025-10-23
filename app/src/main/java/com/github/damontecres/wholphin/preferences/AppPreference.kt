package com.github.damontecres.wholphin.preferences

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.preferences.PreferenceValidation
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A preference that can be stored in [AppPreferences].
 *
 * @param T The type of the preference value.
 */
sealed interface AppPreference<T> {
    /**
     * String resource ID for the title of the preference
     */
    @get:StringRes
    val title: Int

    /**
     * Default value for the preference for UI purposes
     */
    val defaultValue: T

    /**
     * A function that gets the value from the [AppPreferences] object for UI purposes. This means
     * that it should return the value that is displayed in the UI, which isn't necessarily the raw value
     */
    val getter: (prefs: AppPreferences) -> T

    /**
     * A function that sets the value in the [AppPreferences] object from the UI. It should convert the value if needed
     */
    val setter: (prefs: AppPreferences, value: T) -> AppPreferences

    fun summary(
        context: Context,
        value: T?,
    ): String? = null

    fun validate(value: T): PreferenceValidation = PreferenceValidation.Valid

    companion object {
        val SkipForward =
            AppSliderPreference(
                title = R.string.skip_forward_preference,
                defaultValue = 30,
                min = 10,
                max = 5.minutes.inWholeSeconds,
                interval = 5,
                getter = {
                    it.playbackPreferences.skipForwardMs
                        .milliseconds.inWholeSeconds
                },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences {
                        skipForwardMs = value.seconds.inWholeMilliseconds
                    }
                },
                summarizer = { value ->
                    if (value != null) {
                        "$value seconds"
                    } else {
                        null
                    }
                },
            )

        val SkipBack =
            AppSliderPreference(
                title = R.string.skip_back_preference,
                defaultValue = 10,
                min = 5,
                max = 5.minutes.inWholeSeconds,
                interval = 5,
                getter = {
                    it.playbackPreferences.skipBackMs
                        .milliseconds.inWholeSeconds
                },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences {
                        skipBackMs = value.seconds.inWholeMilliseconds
                    }
                },
                summarizer = { value ->
                    if (value != null) {
                        "$value seconds"
                    } else {
                        null
                    }
                },
            )

//        val GridJumpButtons =
//            AppSwitchPreference(
//                title = R.string.show_grid_jump_buttons,
//                defaultValue = true,
//                getter = { it.interfacePreferences.showGridJumpButtons },
//                setter = { prefs, value ->
//                    prefs.updateInterfacePreferences { showGridJumpButtons = value }
//                },
//                summaryOn = R.string.enabled,
//                summaryOff = R.string.disabled,
//            )

//        val ShowGridFooter =
//            AppSwitchPreference(
//                title = R.string.grid_position_footer,
//                defaultValue = true,
//                getter = { it.interfacePreferences.showPositionFooter },
//                setter = { prefs, value ->
//                    prefs.updateInterfacePreferences { showPositionFooter = value }
//                },
//                summaryOn = R.string.show,
//                summaryOff = R.string.hide,
//            )

        val ControllerTimeout =
            AppSliderPreference(
                title = R.string.hide_controller_timeout,
                defaultValue = 5000,
                min = 500,
                max = 15.seconds.inWholeMilliseconds,
                interval = 100,
                getter = { it.playbackPreferences.controllerTimeoutMs },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { controllerTimeoutMs = value }
                },
                summarizer = { value -> value?.let { "${value / 1000.0} seconds" } },
            )

        val SeekBarSteps =
            AppSliderPreference(
                title = R.string.seek_bar_steps,
                defaultValue = 16,
                min = 4,
                max = 64,
                interval = 1,
                getter = { it.playbackPreferences.seekBarSteps.toLong() },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { seekBarSteps = value.toInt() }
                },
                summarizer = { value -> value?.toString() },
            )

        val HomePageItems =
            AppSliderPreference(
                title = R.string.max_homepage_items,
                defaultValue = 25,
                min = 5,
                max = 50,
                interval = 1,
                getter = { it.homePagePreferences.maxItemsPerRow.toLong() },
                setter = { prefs, value ->
                    prefs.updateHomePagePreferences { maxItemsPerRow = value.toInt() }
                },
                summarizer = { value -> value?.toString() },
            )

        val CombineContinueNext =
            AppSwitchPreference(
                title = R.string.combine_continue_next,
                defaultValue = false,
                getter = { it.homePagePreferences.combineContinueNext },
                setter = { prefs, value ->
                    prefs.updateHomePagePreferences { combineContinueNext = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val RewatchNextUp =
            AppSwitchPreference(
                title = R.string.rewatch_next_up,
                defaultValue = false,
                getter = { it.homePagePreferences.enableRewatchingNextUp },
                setter = { prefs, value ->
                    prefs.updateHomePagePreferences { enableRewatchingNextUp = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val PlayThemeMusic =
            AppChoicePreference<ThemeSongVolume>(
                title = R.string.play_theme_music,
                defaultValue = ThemeSongVolume.MEDIUM,
                getter = { it.interfacePreferences.playThemeSongs },
                setter = { prefs, value ->
                    prefs.updateInterfacePreferences { playThemeSongs = value }
                },
                displayValues = R.array.theme_song_volume,
                indexToValue = { ThemeSongVolume.forNumber(it) },
                valueToIndex = { it.number },
            )

        val PlaybackDebugInfo =
            AppSwitchPreference(
                title = R.string.playback_debug_info,
                defaultValue = false,
                getter = { it.playbackPreferences.showDebugInfo },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { showDebugInfo = value }
                },
                summaryOn = R.string.show,
                summaryOff = R.string.hide,
            )

        val AutoPlayNextUp =
            AppSwitchPreference(
                title = R.string.auto_play_next,
                defaultValue = true,
                getter = { it.playbackPreferences.autoPlayNext },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { autoPlayNext = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val SkipBackOnResume =
            AppSliderPreference(
                title = R.string.skip_back_on_resume_preference,
                defaultValue = 0,
                min = 0,
                max = 10,
                interval = 1,
                getter = { it.playbackPreferences.skipBackOnResumeSeconds.milliseconds.inWholeSeconds },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences {
                        skipBackOnResumeSeconds = value.seconds.inWholeMilliseconds
                    }
                },
                summarizer = { value ->
                    if (value == 0L) {
                        "Disabled"
                    } else {
                        "${value}s"
                    }
                },
            )

        val AutoPlayNextDelay =
            AppSliderPreference(
                title = R.string.auto_play_next_delay,
                defaultValue = 15,
                min = 0,
                max = 60,
                interval = 5,
                getter = { it.playbackPreferences.autoPlayNextDelaySeconds },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { autoPlayNextDelaySeconds = value }
                },
                summarizer = { value ->
                    if (value == 0L) {
                        "Immediate"
                    } else {
                        "$value seconds"
                    }
                },
            )

        val PassOutProtection =
            AppSliderPreference(
                title = R.string.pass_out_protection,
                defaultValue = 2,
                min = 0,
                max = 3,
                interval = 1,
                getter = { it.playbackPreferences.passOutProtectionMs.milliseconds.inWholeHours },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences {
                        passOutProtectionMs = value.hours.inWholeMilliseconds
                    }
                },
                summarizer = { value ->
                    if (value == 0L) {
                        "Disabled"
                    } else {
                        "$value hours"
                    }
                },
            )

        private const val MEGA_BIT = 1024 * 1024L
        const val DEFAULT_BITRATE = 20 * MEGA_BIT
        private val bitrateValues =
            listOf(
                500 * 1024L,
                750 * 1024L,
                1 * MEGA_BIT,
                2 * MEGA_BIT,
                3 * MEGA_BIT,
                5 * MEGA_BIT,
                8 * MEGA_BIT,
                10 * MEGA_BIT,
                15 * MEGA_BIT,
                DEFAULT_BITRATE,
                *(30..100 step 10).map { it * MEGA_BIT }.toTypedArray(),
                *(120..200 step 20).map { it * MEGA_BIT }.toTypedArray(),
            )
        val MaxBitrate =
            AppSliderPreference(
                title = R.string.max_bitrate,
                defaultValue = bitrateValues.indexOf(DEFAULT_BITRATE).toLong(),
                min = 0,
                max = bitrateValues.size - 1L,
                interval = 1,
                getter = {
                    bitrateValues.indexOf(it.playbackPreferences.maxBitrate).toLong()
                },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences {
                        maxBitrate = bitrateValues[value.toInt()]
                    }
                },
                summarizer = { value ->
                    if (value != null) {
                        val v = bitrateValues.getOrNull(value.toInt()) ?: DEFAULT_BITRATE
                        if (v < MEGA_BIT) {
                            "${v / 1024} kbps"
                        } else {
                            "${v / MEGA_BIT} Mbps"
                        }
                    } else {
                        null
                    }
                },
            )

        val Ac3Supported =
            AppSwitchPreference(
                title = R.string.ac3_supported,
                defaultValue = true,
                getter = { it.playbackPreferences.overrides.ac3Supported },
                setter = { prefs, value ->
                    prefs.updatePlaybackOverrides { ac3Supported = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )
        val DownMixStereo =
            AppSwitchPreference(
                title = R.string.downmix_stereo,
                defaultValue = false,
                getter = { it.playbackPreferences.overrides.downmixStereo },
                setter = { prefs, value ->
                    prefs.updatePlaybackOverrides { downmixStereo = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )
        val DirectPlayAss =
            AppSwitchPreference(
                title = R.string.direct_play_ass,
                defaultValue = true,
                getter = { it.playbackPreferences.overrides.directPlayAss },
                setter = { prefs, value ->
                    prefs.updatePlaybackOverrides { directPlayAss = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )
        val DirectPlayPgs =
            AppSwitchPreference(
                title = R.string.direct_play_pgs,
                defaultValue = true,
                getter = { it.playbackPreferences.overrides.directPlayPgs },
                setter = { prefs, value ->
                    prefs.updatePlaybackOverrides { directPlayPgs = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val RememberSelectedTab =
            AppSwitchPreference(
                title = R.string.remember_selected_tab,
                defaultValue = false,
                getter = { it.interfacePreferences.rememberSelectedTab },
                setter = { prefs, value ->
                    prefs.updateInterfacePreferences { rememberSelectedTab = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val ThemeColors =
            AppChoicePreference<AppThemeColors>(
                title = R.string.app_theme,
                defaultValue = AppThemeColors.PURPLE,
                getter = { it.interfacePreferences.appThemeColors },
                setter = { prefs, value ->
                    prefs.updateInterfacePreferences { appThemeColors = value }
                },
                displayValues = R.array.app_theme_colors,
                indexToValue = { AppThemeColors.forNumber(it) },
                valueToIndex = { it.number },
            )

        val InstalledVersion =
            AppClickablePreference(
                title = R.string.installed_version,
                getter = { },
                setter = { prefs, _ -> prefs },
            )

        val Update =
            AppClickablePreference(
                title = R.string.check_for_updates,
                getter = { },
                setter = { prefs, _ -> prefs },
            )

        val AutoCheckForUpdates =
            AppSwitchPreference(
                title = R.string.auto_check_for_updates,
                defaultValue = true,
                getter = { it.autoCheckForUpdates },
                setter = { prefs, value ->
                    prefs.update { autoCheckForUpdates = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val UpdateUrl =
            AppStringPreference(
                title = R.string.update_url,
                defaultValue = "https://api.github.com/repos/damontecres/Wholphin/releases/latest",
                getter = { it.updateUrl },
                setter = { prefs, value ->
                    prefs.update { updateUrl = value }
                },
                summary = R.string.update_url_summary,
            )

        val OssLicenseInfo =
            AppDestinationPreference(
                title = R.string.license_info,
                destination = Destination.License,
            )

        val AdvancedSettings =
            AppDestinationPreference(
                title = R.string.advanced_settings,
                destination = Destination.Settings(PreferenceScreenOption.ADVANCED),
            )

        val SkipIntros =
            AppChoicePreference<SkipSegmentBehavior>(
                title = R.string.skip_intro_behavior,
                defaultValue = SkipSegmentBehavior.ASK_TO_SKIP,
                getter = { it.playbackPreferences.skipIntros },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { skipIntros = value }
                },
                displayValues = R.array.skip_behaviors,
                indexToValue = { SkipSegmentBehavior.forNumber(it) },
                valueToIndex = { it.number },
            )

        val SkipOutros =
            AppChoicePreference<SkipSegmentBehavior>(
                title = R.string.skip_outro_behavior,
                defaultValue = SkipSegmentBehavior.ASK_TO_SKIP,
                getter = { it.playbackPreferences.skipOutros },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { skipOutros = value }
                },
                displayValues = R.array.skip_behaviors,
                indexToValue = { SkipSegmentBehavior.forNumber(it) },
                valueToIndex = { it.number },
            )

        val SkipCommercials =
            AppChoicePreference<SkipSegmentBehavior>(
                title = R.string.skip_comercials_behavior,
                defaultValue = SkipSegmentBehavior.ASK_TO_SKIP,
                getter = { it.playbackPreferences.skipCommercials },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { skipCommercials = value }
                },
                displayValues = R.array.skip_behaviors,
                indexToValue = { SkipSegmentBehavior.forNumber(it) },
                valueToIndex = { it.number },
            )

        val SkipPreviews =
            AppChoicePreference<SkipSegmentBehavior>(
                title = R.string.skip_previews_behavior,
                defaultValue = SkipSegmentBehavior.IGNORE,
                getter = { it.playbackPreferences.skipPreviews },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { skipPreviews = value }
                },
                displayValues = R.array.skip_behaviors,
                indexToValue = { SkipSegmentBehavior.forNumber(it) },
                valueToIndex = { it.number },
            )

        val SkipRecaps =
            AppChoicePreference<SkipSegmentBehavior>(
                title = R.string.skip_recap_behavior,
                defaultValue = SkipSegmentBehavior.IGNORE,
                getter = { it.playbackPreferences.skipRecaps },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { skipRecaps = value }
                },
                displayValues = R.array.skip_behaviors,
                indexToValue = { SkipSegmentBehavior.forNumber(it) },
                valueToIndex = { it.number },
            )

        val ClearImageCache =
            AppClickablePreference(
                title = R.string.clear_image_cache,
                getter = { },
                setter = { prefs, _ -> prefs },
            )

        val UserPinnedNavDrawerItems =
            AppClickablePreference(
                title = R.string.nav_drawer_pins,
                getter = { },
                setter = { prefs, _ -> prefs },
            )
    }
}

val basicPreferences =
    listOf(
        PreferenceGroup(
            title = R.string.basic_interface,
            preferences =
                listOf(
                    AppPreference.HomePageItems,
                    AppPreference.RewatchNextUp,
                    AppPreference.CombineContinueNext,
                    AppPreference.PlayThemeMusic,
                    AppPreference.RememberSelectedTab,
                    AppPreference.ThemeColors,
                ),
        ),
        PreferenceGroup(
            title = R.string.playback,
            preferences =
                listOf(
                    AppPreference.SkipForward,
                    AppPreference.SkipBack,
                    AppPreference.ControllerTimeout,
                    AppPreference.AutoPlayNextUp,
                    AppPreference.AutoPlayNextDelay,
                    AppPreference.PassOutProtection,
                    AppPreference.SkipBackOnResume,
                ),
        ),
        PreferenceGroup(
            title = R.string.profile_specific_settings,
            preferences =
                listOf(
                    AppPreference.UserPinnedNavDrawerItems,
                ),
        ),
        PreferenceGroup(
            title = R.string.about,
            preferences =
                listOf(
                    AppPreference.InstalledVersion,
                    AppPreference.Update,
                ),
        ),
        PreferenceGroup(
            title = R.string.more,
            preferences =
                listOf(
                    AppPreference.AdvancedSettings,
                ),
        ),
    )

val uiPreferences = listOf<PreferenceGroup>()

val advancedPreferences =
    listOf(
        PreferenceGroup(
            title = R.string.playback,
            preferences =
                listOf(
                    AppPreference.SkipIntros,
                    AppPreference.SkipOutros,
                    AppPreference.SkipCommercials,
                    AppPreference.SkipPreviews,
                    AppPreference.SkipRecaps,
                    AppPreference.MaxBitrate,
                    AppPreference.PlaybackDebugInfo,
                ),
        ),
        PreferenceGroup(
            title = R.string.playback_overrides,
            preferences =
                listOf(
                    AppPreference.DownMixStereo,
                    AppPreference.Ac3Supported,
                    AppPreference.DirectPlayAss,
                    AppPreference.DirectPlayPgs,
                ),
        ),
        PreferenceGroup(
            title = R.string.updates,
            preferences =
                listOf(
                    AppPreference.AutoCheckForUpdates,
                    AppPreference.UpdateUrl,
                ),
        ),
        PreferenceGroup(
            title = R.string.more,
            preferences =
                listOf(
                    AppPreference.ClearImageCache,
                    AppPreference.OssLicenseInfo,
                ),
        ),
    )

data class AppSwitchPreference(
    @get:StringRes override val title: Int,
    override val defaultValue: Boolean,
    override val getter: (prefs: AppPreferences) -> Boolean,
    override val setter: (prefs: AppPreferences, value: Boolean) -> AppPreferences,
    val validator: (value: Boolean) -> PreferenceValidation = { PreferenceValidation.Valid },
    @param:StringRes val summary: Int? = null,
    @param:StringRes val summaryOn: Int? = null,
    @param:StringRes val summaryOff: Int? = null,
) : AppPreference<Boolean> {
    override fun summary(
        context: Context,
        value: Boolean?,
    ): String? =
        when {
            summaryOn != null && value == true -> context.getString(summaryOn)
            summaryOff != null && value == false -> context.getString(summaryOff)
            else -> summary?.let { context.getString(summary) }
        }
}

open class AppStringPreference(
    @param:StringRes override val title: Int,
    override val defaultValue: String,
    override val getter: (AppPreferences) -> String,
    override val setter: (AppPreferences, String) -> AppPreferences,
    @param:StringRes val summary: Int?,
) : AppPreference<String> {
    override fun summary(
        context: Context,
        value: String?,
    ): String? = summary?.let { context.getString(it) } ?: value
}

data class AppChoicePreference<T>(
    @param:StringRes override val title: Int,
    override val defaultValue: T,
    @param:ArrayRes val displayValues: Int,
    val indexToValue: (index: Int) -> T,
    val valueToIndex: (T) -> Int,
    override val getter: (prefs: AppPreferences) -> T,
    override val setter: (prefs: AppPreferences, value: T) -> AppPreferences,
    @param:StringRes val summary: Int? = null,
) : AppPreference<T>

data class AppMultiChoicePreference<T>(
    @param:StringRes override val title: Int,
    override val defaultValue: List<T>,
    val allValues: List<T>,
    @param:ArrayRes val displayValues: Int,
    override val getter: (prefs: AppPreferences) -> List<T>,
    override val setter: (prefs: AppPreferences, value: List<T>) -> AppPreferences,
    @param:StringRes val summary: Int? = null,
    val toSharedPrefs: (T) -> String,
    val fromSharedPrefs: (String) -> T?,
) : AppPreference<List<T>>

data class AppClickablePreference(
    @param:StringRes override val title: Int,
    override val defaultValue: Unit = Unit,
    override val getter: (prefs: AppPreferences) -> Unit = { },
    override val setter: (prefs: AppPreferences, value: Unit) -> AppPreferences = { prefs, _ -> prefs },
    @param:StringRes val summary: Int? = null,
) : AppPreference<Unit> {
    override fun summary(
        context: Context,
        value: Unit?,
    ): String? = summary?.let { context.getString(it) }
}

data class AppDestinationPreference(
    @param:StringRes override val title: Int,
    override val defaultValue: Unit = Unit,
    override val getter: (prefs: AppPreferences) -> Unit = { },
    override val setter: (prefs: AppPreferences, value: Unit) -> AppPreferences = { prefs, _ -> prefs },
    @param:StringRes val summary: Int? = null,
    val destination: Destination,
) : AppPreference<Unit> {
    override fun summary(
        context: Context,
        value: Unit?,
    ): String? = summary?.let { context.getString(it) }
}

class AppSliderPreference(
    @param:StringRes override val title: Int,
    override val defaultValue: Long,
    /**
     * Minimum value for the slider. Similar to [defaultValue], this is for UI purposes only
     */
    val min: Long = 0,
    /**
     * Max value for the slider. Similar to [defaultValue], this is for UI purposes only
     */
    val max: Long = 100,
    val interval: Int = 1,
    override val getter: (prefs: AppPreferences) -> Long,
    override val setter: (prefs: AppPreferences, value: Long) -> AppPreferences,
    @param:StringRes val summary: Int? = null,
    val summarizer: ((Long?) -> String?)? = null,
) : AppPreference<Long> {
    override fun summary(
        context: Context,
        value: Long?,
    ): String? =
        summarizer?.invoke(value)
            ?: summary?.let { context.getString(it) }
            ?: value?.toString()
}
