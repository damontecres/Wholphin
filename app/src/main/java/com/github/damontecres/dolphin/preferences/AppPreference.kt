package com.github.damontecres.dolphin.preferences

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.github.damontecres.dolphin.R
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.preferences.PreferenceGroup
import com.github.damontecres.dolphin.ui.preferences.PreferenceValidation
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A preference that can be stored in the shared preferences.
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
            AppSwitchPreference(
                title = R.string.play_theme_music,
                defaultValue = true,
                getter = { it.interfacePreferences.playThemeSongs },
                setter = { prefs, value ->
                    prefs.updateInterfacePreferences { playThemeSongs = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

//        val PlaybackDebugInfo =
//            AppSwitchPreference(
//                title = R.string.playback_debug_info,
//                prefKey = R.string.pref_key_show_playback_debug_info,
//                defaultValue = false,
//                getter = { it.playbackPreferences.showDebugInfo },
//                setter = { prefs, value ->
//                    prefs.updatePlaybackPreferences { showDebugInfo = value }
//                },
//                summaryOn = R.string.show,
//                summaryOff = R.string.hide,
//            )

//        val AutoCheckForUpdates =
//            AppSwitchPreference(
//                title = R.string.check_for_updates,
//                prefKey = R.string.pref_key_auto_check_updates,
//                defaultValue = true,
//                getter = { it.updatePreferences.checkForUpdates },
//                setter = { prefs, value ->
//                    prefs.updateUpdatePreferences { checkForUpdates = value }
//                },
//                summaryOn = R.string.enabled,
//                summaryOff = R.string.disabled,
//            )

//        val UpdateUrl =
//            AppStringPreference(
//                title = R.string.update_url,
//                defaultValue = "",
//                getter = { it.updatePreferences.updateUrl },
//                setter = { prefs, value ->
//                    prefs.updateUpdatePreferences { updateUrl = value }
//                },
//                summary = R.string.update_url_summary,
//            )

        val OssLicenseInfo =
            AppDestinationPreference(
                title = R.string.license_info,
                destination = Destination.License,
            )
    }
}

val basicPreferences =
    listOf(
        PreferenceGroup(
            title = R.string.basic_interface,
            preferences =
                listOf(
                    AppPreference.SkipForward,
                    AppPreference.SkipBack,
                    AppPreference.ControllerTimeout,
                    AppPreference.SeekBarSteps,
                    AppPreference.HomePageItems,
                    AppPreference.RewatchNextUp,
                    AppPreference.PlayThemeMusic,
                    AppPreference.OssLicenseInfo,
                ),
        ),
    )

val uiPreferences = listOf<PreferenceGroup>()

val advancedPreferences = listOf<PreferenceGroup>()

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
