package com.github.damontecres.wholphin.preferences

import android.content.Context
import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.ConditionalPreferences
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup
import com.github.damontecres.wholphin.ui.preferences.PreferenceScreenOption
import com.github.damontecres.wholphin.ui.preferences.PreferenceValidation
import com.github.damontecres.wholphin.util.DebugLogTree
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * A preference that can be stored in [AppPreferences].
 *
 * @param T The type of the preference value.
 */
sealed interface AppPreference<Pref, T> {
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
    val getter: (prefs: Pref) -> T

    /**
     * A function that sets the value in the [AppPreferences] object from the UI. It should convert the value if needed
     */
    val setter: (prefs: Pref, value: T) -> Pref

    fun summary(
        context: Context,
        value: T?,
    ): String? = null

    fun validate(value: T): PreferenceValidation = PreferenceValidation.Valid

    companion object {
        val SkipForward =
            AppSliderPreference<AppPreferences>(
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
                        WholphinApplication.instance.resources.getQuantityString(
                            R.plurals.seconds,
                            value.toInt(),
                            value.toInt(),
                        )
                    } else {
                        null
                    }
                },
            )

        val SkipBack =
            AppSliderPreference<AppPreferences>(
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
                        WholphinApplication.instance.resources.getQuantityString(
                            R.plurals.seconds,
                            value.toInt(),
                            value.toInt(),
                        )
                    } else {
                        null
                    }
                },
            )

//        val GridJumpButtons =
//            AppSwitchPreference<AppPreferences>(
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
//            AppSwitchPreference<AppPreferences>(
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
            AppSliderPreference<AppPreferences>(
                title = R.string.hide_controller_timeout,
                defaultValue = 5000,
                min = 500,
                max = 15.seconds.inWholeMilliseconds,
                interval = 100,
                getter = { it.playbackPreferences.controllerTimeoutMs },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { controllerTimeoutMs = value }
                },
                summarizer = { value ->
                    value?.let {
                        WholphinApplication.instance.getString(
                            R.string.decimal_seconds,
                            value / 1000.0,
                        )
                    }
                },
            )

        val SeekBarSteps =
            AppSliderPreference<AppPreferences>(
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
            AppSliderPreference<AppPreferences>(
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
            AppSwitchPreference<AppPreferences>(
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
            AppSwitchPreference<AppPreferences>(
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
            AppChoicePreference<AppPreferences, ThemeSongVolume>(
                title = R.string.play_theme_music,
                defaultValue = ThemeSongVolume.MEDIUM,
                getter = { it.interfacePreferences.playThemeSongs },
                setter = { prefs, value ->
                    prefs.updateInterfacePreferences { playThemeSongs = value }
                },
                displayValues = R.array.theme_song_volume,
                indexToValue = { ThemeSongVolume.forNumber(it) },
                valueToIndex = { if (it != ThemeSongVolume.UNRECOGNIZED) it.number else 0 },
            )

        val PlaybackDebugInfo =
            AppSwitchPreference<AppPreferences>(
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
            AppSwitchPreference<AppPreferences>(
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
            AppSliderPreference<AppPreferences>(
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
                        WholphinApplication.instance.getString(R.string.disabled)
                    } else {
                        "${value}s"
                    }
                },
            )

        val AutoPlayNextDelay =
            AppSliderPreference<AppPreferences>(
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
                    if (value == null) {
                        ""
                    } else if (value == 0L) {
                        WholphinApplication.instance.getString(R.string.immediate)
                    } else {
                        WholphinApplication.instance.resources.getQuantityString(
                            R.plurals.seconds,
                            value.toInt(),
                            value.toInt(),
                        )
                    }
                },
            )

        val PassOutProtection =
            AppSliderPreference<AppPreferences>(
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
                    if (value == null) {
                        ""
                    } else if (value == 0L) {
                        WholphinApplication.instance.getString(R.string.disabled)
                    } else {
                        WholphinApplication.instance.resources.getQuantityString(
                            R.plurals.hours,
                            value.toInt(),
                            value.toInt(),
                        )
                    }
                },
            )

        const val MEGA_BIT = 1024 * 1024L
        const val DEFAULT_BITRATE = 100 * MEGA_BIT
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
                20 * MEGA_BIT,
                *(30..100 step 10).map { it * MEGA_BIT }.toTypedArray(),
                *(120..200 step 20).map { it * MEGA_BIT }.toTypedArray(),
            )
        val MaxBitrate =
            AppSliderPreference<AppPreferences>(
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
            AppSwitchPreference<AppPreferences>(
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
            AppSwitchPreference<AppPreferences>(
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
            AppSwitchPreference<AppPreferences>(
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
            AppSwitchPreference<AppPreferences>(
                title = R.string.direct_play_pgs,
                defaultValue = true,
                getter = { it.playbackPreferences.overrides.directPlayPgs },
                setter = { prefs, value ->
                    prefs.updatePlaybackOverrides { directPlayPgs = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val DirectPlayDoviProfile7 =
            AppSwitchPreference<AppPreferences>(
                title = R.string.force_dovi_profile_7,
                defaultValue = false,
                getter = { it.playbackPreferences.overrides.directPlayDolbyVisionEL },
                setter = { prefs, value ->
                    prefs.updatePlaybackOverrides { directPlayDolbyVisionEL = value }
                },
                summary = R.string.force_dovi_profile_7_summary,
            )

        val DecodeAv1 =
            AppSwitchPreference<AppPreferences>(
                title = R.string.software_decoding_av1,
                defaultValue = true,
                getter = { it.playbackPreferences.overrides.decodeAv1 },
                setter = { prefs, value ->
                    prefs.updatePlaybackOverrides { decodeAv1 = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val RememberSelectedTab =
            AppSwitchPreference<AppPreferences>(
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
            AppChoicePreference<AppPreferences, AppThemeColors>(
                title = R.string.app_theme,
                defaultValue = AppThemeColors.PURPLE,
                getter = { it.interfacePreferences.appThemeColors },
                setter = { prefs, value ->
                    prefs.updateInterfacePreferences { appThemeColors = value }
                },
                displayValues = R.array.app_theme_colors,
                indexToValue = { AppThemeColors.forNumber(it) },
                valueToIndex = { if (it != AppThemeColors.UNRECOGNIZED) it.number else 0 },
            )

        val InstalledVersion =
            AppClickablePreference<AppPreferences>(
                title = R.string.installed_version,
                getter = { },
                setter = { prefs, _ -> prefs },
            )

        val Update =
            AppClickablePreference<AppPreferences>(
                title = R.string.check_for_updates,
                getter = { },
                setter = { prefs, _ -> prefs },
            )

        val AutoCheckForUpdates =
            AppSwitchPreference<AppPreferences>(
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
            AppStringPreference<AppPreferences>(
                title = R.string.update_url,
                defaultValue = "https://api.github.com/repos/damontecres/Wholphin/releases/latest",
                getter = { it.updateUrl },
                setter = { prefs, value ->
                    prefs.update { updateUrl = value }
                },
                summary = R.string.update_url_summary,
            )

        val OssLicenseInfo =
            AppDestinationPreference<AppPreferences>(
                title = R.string.license_info,
                destination = Destination.License,
            )

        val AdvancedSettings =
            AppDestinationPreference<AppPreferences>(
                title = R.string.advanced_settings,
                destination = Destination.Settings(PreferenceScreenOption.ADVANCED),
            )

        val SkipIntros =
            AppChoicePreference<AppPreferences, SkipSegmentBehavior>(
                title = R.string.skip_intro_behavior,
                defaultValue = SkipSegmentBehavior.ASK_TO_SKIP,
                getter = { it.playbackPreferences.skipIntros },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { skipIntros = value }
                },
                displayValues = R.array.skip_behaviors,
                indexToValue = { SkipSegmentBehavior.forNumber(it) },
                valueToIndex = { if (it != SkipSegmentBehavior.UNRECOGNIZED) it.number else 0 },
            )

        val SkipOutros =
            AppChoicePreference<AppPreferences, SkipSegmentBehavior>(
                title = R.string.skip_outro_behavior,
                defaultValue = SkipSegmentBehavior.ASK_TO_SKIP,
                getter = { it.playbackPreferences.skipOutros },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { skipOutros = value }
                },
                displayValues = R.array.skip_behaviors,
                indexToValue = { SkipSegmentBehavior.forNumber(it) },
                valueToIndex = { if (it != SkipSegmentBehavior.UNRECOGNIZED) it.number else 0 },
            )

        val SkipCommercials =
            AppChoicePreference<AppPreferences, SkipSegmentBehavior>(
                title = R.string.skip_commercials_behavior,
                defaultValue = SkipSegmentBehavior.ASK_TO_SKIP,
                getter = { it.playbackPreferences.skipCommercials },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { skipCommercials = value }
                },
                displayValues = R.array.skip_behaviors,
                indexToValue = { SkipSegmentBehavior.forNumber(it) },
                valueToIndex = { if (it != SkipSegmentBehavior.UNRECOGNIZED) it.number else 0 },
            )

        val SkipPreviews =
            AppChoicePreference<AppPreferences, SkipSegmentBehavior>(
                title = R.string.skip_previews_behavior,
                defaultValue = SkipSegmentBehavior.IGNORE,
                getter = { it.playbackPreferences.skipPreviews },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { skipPreviews = value }
                },
                displayValues = R.array.skip_behaviors,
                indexToValue = { SkipSegmentBehavior.forNumber(it) },
                valueToIndex = { if (it != SkipSegmentBehavior.UNRECOGNIZED) it.number else 0 },
            )

        val SkipRecaps =
            AppChoicePreference<AppPreferences, SkipSegmentBehavior>(
                title = R.string.skip_recap_behavior,
                defaultValue = SkipSegmentBehavior.IGNORE,
                getter = { it.playbackPreferences.skipRecaps },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { skipRecaps = value }
                },
                displayValues = R.array.skip_behaviors,
                indexToValue = { SkipSegmentBehavior.forNumber(it) },
                valueToIndex = { if (it != SkipSegmentBehavior.UNRECOGNIZED) it.number else 0 },
            )

        val GlobalContentScale =
            AppChoicePreference<AppPreferences, PrefContentScale>(
                title = R.string.global_content_scale,
                defaultValue = PrefContentScale.FIT,
                getter = { it.playbackPreferences.globalContentScale },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { globalContentScale = value }
                },
                displayValues = R.array.content_scale,
                indexToValue = { PrefContentScale.forNumber(it) },
                valueToIndex = { if (it != PrefContentScale.UNRECOGNIZED) it.number else 0 },
            )

        val FfmpegPreference =
            AppChoicePreference<AppPreferences, MediaExtensionStatus>(
                title = R.string.ffmpeg_extension_pref,
                defaultValue = MediaExtensionStatus.MES_FALLBACK,
                getter = { it.playbackPreferences.overrides.mediaExtensionsEnabled },
                setter = { prefs, value ->
                    prefs.updatePlaybackOverrides { mediaExtensionsEnabled = value }
                },
                displayValues = R.array.ffmpeg_extension_options,
                indexToValue = { MediaExtensionStatus.forNumber(it) },
                valueToIndex = { if (it != MediaExtensionStatus.UNRECOGNIZED) it.number else 0 },
            )

        val ClearImageCache =
            AppClickablePreference<AppPreferences>(
                title = R.string.clear_image_cache,
                getter = { },
                setter = { prefs, _ -> prefs },
            )

        val UserPinnedNavDrawerItems =
            AppClickablePreference<AppPreferences>(
                title = R.string.nav_drawer_pins,
                summary = R.string.nav_drawer_pins_summary,
                getter = { },
                setter = { prefs, _ -> prefs },
            )

        val SendCrashReports =
            AppSwitchPreference<AppPreferences>(
                title = R.string.send_crash_reports,
                defaultValue = true,
                getter = {
                    PreferenceManager
                        .getDefaultSharedPreferences(WholphinApplication.instance)
                        .getBoolean("acra.enable", true)
                },
                setter = { prefs, value ->
                    PreferenceManager
                        .getDefaultSharedPreferences(WholphinApplication.instance)
                        .edit {
                            putBoolean("acra.enable", value)
                        }
                    prefs.update { sendCrashReports = value }
                },
                summary = R.string.send_crash_reports_summary,
            )

        val SendAppLogs =
            AppClickablePreference<AppPreferences>(
                title = R.string.send_app_logs,
                summary = R.string.send_app_logs_summary,
                getter = { },
                setter = { prefs, _ -> prefs },
            )

        val NavDrawerSwitchOnFocus =
            AppSwitchPreference<AppPreferences>(
                title = R.string.nav_drawer_switch_on_focus,
                defaultValue = true,
                getter = { it.interfacePreferences.navDrawerSwitchOnFocus },
                setter = { prefs, value ->
                    prefs.updateInterfacePreferences { navDrawerSwitchOnFocus = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.nav_drawer_switch_on_focus_summary_off,
            )

        val ShowNextUpTiming =
            AppChoicePreference<AppPreferences, ShowNextUpWhen>(
                title = R.string.show_next_up_when,
                defaultValue = ShowNextUpWhen.END_OF_PLAYBACK,
                getter = { it.playbackPreferences.showNextUpWhen },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { showNextUpWhen = value }
                },
                displayValues = R.array.show_next_up_when_options,
                indexToValue = { ShowNextUpWhen.forNumber(it) },
                valueToIndex = { if (it != ShowNextUpWhen.UNRECOGNIZED) it.number else 0 },
            )

        val ShowClock =
            AppSwitchPreference<AppPreferences>(
                title = R.string.show_clock,
                defaultValue = true,
                getter = { it.interfacePreferences.showClock },
                setter = { prefs, value ->
                    prefs.updateInterfacePreferences { showClock = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val BackdropStylePref =
            AppChoicePreference<AppPreferences, BackdropStyle>(
                title = R.string.backdrop_display,
                defaultValue = BackdropStyle.BACKDROP_DYNAMIC_COLOR,
                getter = { it.interfacePreferences.backdropStyle },
                setter = { prefs, value ->
                    prefs.updateInterfacePreferences { backdropStyle = value }
                },
                displayValues = R.array.backdrop_style_options,
                indexToValue = { BackdropStyle.forNumber(it) },
                valueToIndex = { it.number },
            )

        val OneClickPause =
            AppSwitchPreference<AppPreferences>(
                title = R.string.one_click_pause,
                defaultValue = false,
                getter = { it.playbackPreferences.oneClickPause },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { oneClickPause = value }
                },
                summaryOn = R.string.one_click_pause_summary_on,
                summaryOff = R.string.disabled,
            )

        val SubtitleStyle =
            AppDestinationPreference<AppPreferences>(
                title = R.string.subtitle_style,
                destination = Destination.Settings(PreferenceScreenOption.SUBTITLES),
            )

        val RefreshRateSwitching =
            AppSwitchPreference<AppPreferences>(
                title = R.string.refresh_rate_switching,
                defaultValue = false,
                getter = { it.playbackPreferences.refreshRateSwitching },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences {
                        if (!value) resolutionSwitching = false
                        refreshRateSwitching = value
                    }
                },
                summaryOn = R.string.automatic,
                summaryOff = R.string.disabled,
            )

        val ResolutionSwitching =
            AppSwitchPreference<AppPreferences>(
                title = R.string.resolution_switching,
                defaultValue = false,
                getter = { it.playbackPreferences.resolutionSwitching },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences {
                        if (value) refreshRateSwitching = true
                        resolutionSwitching = value
                    }
                },
                summaryOn = R.string.automatic,
                summaryOff = R.string.disabled,
            )

        val PlayerBackendPref =
            AppChoicePreference<AppPreferences, PlayerBackend>(
                title = R.string.player_backend,
                defaultValue = PlayerBackend.PREFER_MPV,
                getter = { it.playbackPreferences.playerBackend },
                setter = { prefs, value ->
                    prefs.updatePlaybackPreferences { playerBackend = value }
                },
                displayValues = R.array.player_backend_options,
                subtitles = R.array.player_backend_options_subtitles,
                indexToValue = { PlayerBackend.forNumber(it) },
                valueToIndex = { it.number },
            )

        val ExoPlayerSettings =
            AppDestinationPreference<AppPreferences>(
                title = R.string.exoplayer_options,
                destination = Destination.Settings(PreferenceScreenOption.EXO_PLAYER),
            )

        val MpvSettings =
            AppDestinationPreference<AppPreferences>(
                title = R.string.mpv_options,
                destination = Destination.Settings(PreferenceScreenOption.MPV),
            )

        val MpvHardwareDecoding =
            AppSwitchPreference<AppPreferences>(
                title = R.string.mpv_hardware_decoding,
                defaultValue = true,
                getter = { it.playbackPreferences.mpvOptions.enableHardwareDecoding },
                setter = { prefs, value ->
                    prefs.updateMpvOptions { enableHardwareDecoding = value }
                },
                summary = R.string.disable_if_crash,
            )

        val MpvGpuNext =
            AppSwitchPreference<AppPreferences>(
                title = R.string.mpv_use_gpu_next,
                defaultValue = true,
                getter = { it.playbackPreferences.mpvOptions.useGpuNext },
                setter = { prefs, value ->
                    prefs.updateMpvOptions { useGpuNext = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val MpvConfFile =
            AppClickablePreference<AppPreferences>(
                title = R.string.mpv_conf,
                summary = null,
            )

        val DebugLogging =
            AppSwitchPreference<AppPreferences>(
                title = R.string.verbose_logging,
                defaultValue = false,
                getter = { DebugLogTree.INSTANCE.enabled },
                setter = { prefs, value ->
                    DebugLogTree.INSTANCE.enabled = value
                    prefs.update { debugLogging = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val SignInAuto =
            AppSwitchPreference<AppPreferences>(
                title = R.string.sign_in_auto,
                defaultValue = true,
                getter = { it.signInAutomatically },
                setter = { prefs, value ->
                    prefs.update { signInAutomatically = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val RequireProfilePin =
            AppClickablePreference<AppPreferences>(
                title = R.string.require_pin_code,
            )

        val ImageDiskCacheSize =
            AppSliderPreference<AppPreferences>(
                title = R.string.image_cache_size,
                defaultValue = 200,
                min = 25,
                max = 1_000,
                interval = 25,
                getter = {
                    it.advancedPreferences.imageDiskCacheSizeBytes / MEGA_BIT
                },
                setter = { prefs, value ->
                    prefs.updateAdvancedPreferences {
                        imageDiskCacheSizeBytes = value * MEGA_BIT
                    }
                },
                summarizer = { value ->
                    if (value != null) {
                        "$value MB"
                    } else {
                        null
                    }
                },
            )

        val LiveTvShowHeader =
            AppSwitchPreference<AppPreferences>(
                title = R.string.show_details,
                defaultValue = true,
                getter = { it.interfacePreferences.liveTvPreferences.showHeader },
                setter = { prefs, value ->
                    prefs.updateLiveTvPreferences { showHeader = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )
        val LiveTvFavoriteChannelsBeginning =
            AppSwitchPreference<AppPreferences>(
                title = R.string.favorite_channels_at_beginning,
                defaultValue = true,
                getter = { it.interfacePreferences.liveTvPreferences.favoriteChannelsAtBeginning },
                setter = { prefs, value ->
                    prefs.updateLiveTvPreferences { favoriteChannelsAtBeginning = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )
        val LiveTvChannelSortByWatched =
            AppSwitchPreference<AppPreferences>(
                title = R.string.sort_channels_recently_watched,
                defaultValue = false,
                getter = { it.interfacePreferences.liveTvPreferences.sortByRecentlyWatched },
                setter = { prefs, value ->
                    prefs.updateLiveTvPreferences { sortByRecentlyWatched = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )
        val LiveTvColorCodePrograms =
            AppSwitchPreference<AppPreferences>(
                title = R.string.color_code_programs,
                defaultValue = true,
                getter = { it.interfacePreferences.liveTvPreferences.colorCodePrograms },
                setter = { prefs, value ->
                    prefs.updateLiveTvPreferences { colorCodePrograms = value }
                },
                summaryOn = R.string.enabled,
                summaryOff = R.string.disabled,
            )

        val SeerrIntegration =
            AppClickablePreference<AppPreferences>(
                title = R.string.seerr_integration,
                getter = { },
                setter = { prefs, _ -> prefs },
            )
    }
}

val basicPreferences =
    listOf(
        PreferenceGroup(
            title = R.string.ui_interface,
            preferences =
                listOf(
                    AppPreference.SignInAuto,
                    AppPreference.HomePageItems,
                    AppPreference.CombineContinueNext,
                    AppPreference.RewatchNextUp,
                    AppPreference.PlayThemeMusic,
                    AppPreference.RememberSelectedTab,
                    AppPreference.SubtitleStyle,
                    AppPreference.ThemeColors,
                ),
        ),
        PreferenceGroup(
            title = R.string.playback,
            preferences =
                listOf(
                    AppPreference.SkipForward,
                    AppPreference.SkipBack,
                    AppPreference.SkipBackOnResume,
                ),
        ),
        PreferenceGroup(
            title = R.string.next_up,
            preferences =
                listOf(
                    AppPreference.ShowNextUpTiming,
                    AppPreference.AutoPlayNextUp,
                    AppPreference.AutoPlayNextDelay,
                    AppPreference.PassOutProtection,
                ),
        ),
        PreferenceGroup(
            title = R.string.profile_specific_settings,
            preferences =
                listOf(
                    AppPreference.RequireProfilePin,
                    AppPreference.UserPinnedNavDrawerItems,
                ),
        ),
        PreferenceGroup(
            title = R.string.about,
            preferences =
                buildList {
                    add(AppPreference.InstalledVersion)
                    if (UpdateChecker.ACTIVE) {
                        add(AppPreference.Update)
                    }
                },
        ),
        PreferenceGroup(
            title = R.string.more,
            preferences =
                listOf(
                    AppPreference.SeerrIntegration,
                    AppPreference.AdvancedSettings,
                ),
        ),
    )

val uiPreferences = listOf<PreferenceGroup>()

private val ExoPlayerSettings =
    listOf(
        AppPreference.FfmpegPreference,
        AppPreference.DownMixStereo,
        AppPreference.Ac3Supported,
        AppPreference.DirectPlayAss,
        AppPreference.DirectPlayPgs,
        AppPreference.DirectPlayDoviProfile7,
        AppPreference.DecodeAv1,
    )

val ExoPlayerPreferences =
    listOf(
        PreferenceGroup(
            title = R.string.exoplayer_options,
            preferences = ExoPlayerSettings,
        ),
    )

private val MpvSettings =
    listOf(
        AppPreference.MpvHardwareDecoding,
        AppPreference.MpvGpuNext,
        AppPreference.MpvConfFile,
    )

val MpvPreferences =
    listOf(
        PreferenceGroup(
            title = R.string.mpv_options,
            preferences = MpvSettings,
        ),
    )

val advancedPreferences =
    buildList {
        add(
            PreferenceGroup(
                title = R.string.ui_interface,
                preferences =
                    listOf(
                        AppPreference.ShowClock,
                        // Temporarily disabled, see https://github.com/damontecres/Wholphin/pull/127#issuecomment-3478058418
//                    AppPreference.NavDrawerSwitchOnFocus,
                        AppPreference.ControllerTimeout,
                        AppPreference.BackdropStylePref,
                    ),
            ),
        )
        add(
            PreferenceGroup(
                title = R.string.playback,
                preferences =
                    listOf(
                        AppPreference.OneClickPause,
                        AppPreference.GlobalContentScale,
                        AppPreference.MaxBitrate,
                        AppPreference.RefreshRateSwitching,
                        AppPreference.ResolutionSwitching,
                        AppPreference.PlaybackDebugInfo,
                    ),
            ),
        )
        add(
            PreferenceGroup(
                title = R.string.skip,
                preferences =
                    listOf(
                        AppPreference.SkipIntros,
                        AppPreference.SkipOutros,
                        AppPreference.SkipCommercials,
                        AppPreference.SkipPreviews,
                        AppPreference.SkipRecaps,
                    ),
            ),
        )
        add(
            PreferenceGroup(
                title = R.string.player_backend,
                preferences = listOf(AppPreference.PlayerBackendPref),
                conditionalPreferences =
                    listOf(
                        ConditionalPreferences(
                            { it.playbackPreferences.playerBackend == PlayerBackend.EXO_PLAYER },
                            ExoPlayerSettings,
                        ),
                        ConditionalPreferences(
                            { it.playbackPreferences.playerBackend == PlayerBackend.MPV },
                            MpvSettings,
                        ),
                        ConditionalPreferences(
                            { it.playbackPreferences.playerBackend == PlayerBackend.PREFER_MPV },
                            listOf(
                                AppPreference.ExoPlayerSettings,
                                AppPreference.MpvSettings,
                            ),
                        ),
                    ),
            ),
        )
        if (UpdateChecker.ACTIVE) {
            add(
                PreferenceGroup(
                    title = R.string.updates,
                    preferences =
                        listOf(
                            AppPreference.AutoCheckForUpdates,
                            AppPreference.UpdateUrl,
                        ),
                ),
            )
        }
        add(
            PreferenceGroup(
                title = R.string.more,
                preferences =
                    listOf(
                        AppPreference.SendAppLogs,
                        AppPreference.SendCrashReports,
                        AppPreference.DebugLogging,
                        AppPreference.ImageDiskCacheSize,
                        AppPreference.ClearImageCache,
                        AppPreference.OssLicenseInfo,
                    ),
            ),
        )
    }

val liveTvPreferences =
    listOf(
        AppPreference.LiveTvShowHeader,
        AppPreference.LiveTvFavoriteChannelsBeginning,
        AppPreference.LiveTvChannelSortByWatched,
        AppPreference.LiveTvColorCodePrograms,
    )

data class AppSwitchPreference<Pref>(
    @get:StringRes override val title: Int,
    override val defaultValue: Boolean,
    override val getter: (prefs: Pref) -> Boolean,
    override val setter: (prefs: Pref, value: Boolean) -> Pref,
    val validator: (value: Boolean) -> PreferenceValidation = { PreferenceValidation.Valid },
    @param:StringRes val summary: Int? = null,
    @param:StringRes val summaryOn: Int? = null,
    @param:StringRes val summaryOff: Int? = null,
) : AppPreference<Pref, Boolean> {
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

open class AppStringPreference<Pref>(
    @param:StringRes override val title: Int,
    override val defaultValue: String,
    override val getter: (Pref) -> String,
    override val setter: (Pref, String) -> Pref,
    @param:StringRes val summary: Int?,
) : AppPreference<Pref, String> {
    override fun summary(
        context: Context,
        value: String?,
    ): String? = summary?.let { context.getString(it) } ?: value
}

data class AppChoicePreference<Pref, T>(
    @param:StringRes override val title: Int,
    override val defaultValue: T,
    @param:ArrayRes val displayValues: Int,
    val indexToValue: (index: Int) -> T,
    val valueToIndex: (T) -> Int,
    override val getter: (prefs: Pref) -> T,
    override val setter: (prefs: Pref, value: T) -> Pref,
    @param:StringRes val summary: Int? = null,
    @param:ArrayRes val subtitles: Int? = null,
) : AppPreference<Pref, T>

data class AppMultiChoicePreference<Pref, T>(
    @param:StringRes override val title: Int,
    override val defaultValue: List<T>,
    val allValues: List<T>,
    @param:ArrayRes val displayValues: Int,
    override val getter: (prefs: Pref) -> List<T>,
    override val setter: (prefs: Pref, value: List<T>) -> Pref,
    @param:StringRes val summary: Int? = null,
    val toSharedPrefs: (T) -> String,
    val fromSharedPrefs: (String) -> T?,
) : AppPreference<Pref, List<T>>

data class AppClickablePreference<Pref>(
    @param:StringRes override val title: Int,
    override val defaultValue: Unit = Unit,
    override val getter: (prefs: Pref) -> Unit = { },
    override val setter: (prefs: Pref, value: Unit) -> Pref = { prefs, _ -> prefs },
    @param:StringRes val summary: Int? = null,
) : AppPreference<Pref, Unit> {
    override fun summary(
        context: Context,
        value: Unit?,
    ): String? = summary?.let { context.getString(it) }
}

data class AppDestinationPreference<Pref>(
    @param:StringRes override val title: Int,
    override val defaultValue: Unit = Unit,
    override val getter: (prefs: Pref) -> Unit = { },
    override val setter: (prefs: Pref, value: Unit) -> Pref = { prefs, _ -> prefs },
    @param:StringRes val summary: Int? = null,
    val destination: Destination,
) : AppPreference<Pref, Unit> {
    override fun summary(
        context: Context,
        value: Unit?,
    ): String? = summary?.let { context.getString(it) }
}

class AppSliderPreference<Pref>(
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
    override val getter: (prefs: Pref) -> Long,
    override val setter: (prefs: Pref, value: Long) -> Pref,
    @param:StringRes val summary: Int? = null,
    val summarizer: ((Long?) -> String?)? = null,
) : AppPreference<Pref, Long> {
    override fun summary(
        context: Context,
        value: Long?,
    ): String? =
        summarizer?.invoke(value)
            ?: summary?.let { context.getString(it) }
            ?: value?.toString()
}
