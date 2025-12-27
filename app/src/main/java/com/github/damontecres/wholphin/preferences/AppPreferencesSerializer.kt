package com.github.damontecres.wholphin.preferences

import androidx.compose.ui.graphics.toArgb
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class AppPreferencesSerializer
    @Inject
    constructor() : Serializer<AppPreferences> {
        override val defaultValue: AppPreferences =
            AppPreferences
                .newBuilder()
                .apply {
                    updateUrl = AppPreference.UpdateUrl.defaultValue
                    autoCheckForUpdates = AppPreference.AutoCheckForUpdates.defaultValue
                    sendCrashReports = AppPreference.SendCrashReports.defaultValue
                    debugLogging = AppPreference.DebugLogging.defaultValue
                    signInAutomatically = AppPreference.SignInAuto.defaultValue

                    playbackPreferences =
                        PlaybackPreferences
                            .newBuilder()
                            .apply {
                                skipForwardMs =
                                    AppPreference.SkipForward.defaultValue.seconds.inWholeMilliseconds
                                skipBackMs =
                                    AppPreference.SkipBack.defaultValue.seconds.inWholeMilliseconds
                                controllerTimeoutMs = AppPreference.ControllerTimeout.defaultValue
                                seekBarSteps = AppPreference.SeekBarSteps.defaultValue.toInt()
                                showDebugInfo = AppPreference.PlaybackDebugInfo.defaultValue
                                autoPlayNext = AppPreference.AutoPlayNextUp.defaultValue
                                autoPlayNextDelaySeconds =
                                    AppPreference.AutoPlayNextDelay.defaultValue
                                skipBackOnResumeSeconds =
                                    AppPreference.SkipBackOnResume.defaultValue.seconds.inWholeMilliseconds
                                maxBitrate = AppPreference.DEFAULT_BITRATE
                                skipIntros = AppPreference.SkipIntros.defaultValue
                                skipOutros = AppPreference.SkipOutros.defaultValue
                                skipCommercials = AppPreference.SkipCommercials.defaultValue
                                skipPreviews = AppPreference.SkipPreviews.defaultValue
                                skipRecaps = AppPreference.SkipRecaps.defaultValue
                                passOutProtectionMs =
                                    AppPreference.PassOutProtection.defaultValue.hours.inWholeMilliseconds
                                showNextUpWhen = AppPreference.ShowNextUpTiming.defaultValue
                                playerBackend = AppPreference.PlayerBackendPref.defaultValue
                                refreshRateSwitching =
                                    AppPreference.RefreshRateSwitching.defaultValue

                                overrides =
                                    PlaybackOverrides
                                        .newBuilder()
                                        .apply {
                                            ac3Supported = AppPreference.Ac3Supported.defaultValue
                                            downmixStereo = AppPreference.DownMixStereo.defaultValue
                                            directPlayAss = AppPreference.DirectPlayAss.defaultValue
                                            directPlayPgs = AppPreference.DirectPlayPgs.defaultValue
                                            mediaExtensionsEnabled =
                                                AppPreference.FfmpegPreference.defaultValue
                                        }.build()

                                mpvOptions =
                                    MpvOptions
                                        .newBuilder()
                                        .apply {
                                            enableHardwareDecoding =
                                                AppPreference.MpvHardwareDecoding.defaultValue
                                            useGpuNext = AppPreference.MpvGpuNext.defaultValue
                                        }.build()
                            }.build()
                    homePagePreferences =
                        HomePagePreferences
                            .newBuilder()
                            .apply {
                                maxItemsPerRow = AppPreference.HomePageItems.defaultValue.toInt()
                                enableRewatchingNextUp = AppPreference.RewatchNextUp.defaultValue
                                combineContinueNext = AppPreference.CombineContinueNext.defaultValue
                            }.build()
                    interfacePreferences =
                        InterfacePreferences
                            .newBuilder()
                            .apply {
                                playThemeSongs = AppPreference.PlayThemeMusic.defaultValue
                                appThemeColors = AppPreference.ThemeColors.defaultValue
                                navDrawerSwitchOnFocus =
                                    AppPreference.NavDrawerSwitchOnFocus.defaultValue
                                showClock = AppPreference.ShowClock.defaultValue
                                backdropStyle = AppPreference.BackdropStylePref.defaultValue

                                subtitlesPreferences =
                                    SubtitlePreferences
                                        .newBuilder()
                                        .apply {
                                            resetSubtitles()
                                        }.build()

                                liveTvPreferences =
                                    LiveTvPreferences
                                        .newBuilder()
                                        .apply {
                                            showHeader = AppPreference.LiveTvShowHeader.defaultValue
                                            favoriteChannelsAtBeginning =
                                                AppPreference.LiveTvFavoriteChannelsBeginning.defaultValue
                                            sortByRecentlyWatched =
                                                AppPreference.LiveTvChannelSortByWatched.defaultValue
                                            colorCodePrograms =
                                                AppPreference.LiveTvColorCodePrograms.defaultValue
                                        }.build()
                            }.build()

                    advancedPreferences =
                        AdvancedPreferences
                            .newBuilder()
                            .apply {
                                imageDiskCacheSizeBytes =
                                    AppPreference.ImageDiskCacheSize.defaultValue * AppPreference.MEGA_BIT
                            }.build()
                }.build()

        override suspend fun readFrom(input: InputStream): AppPreferences {
            try {
                return AppPreferences.parseFrom(input)
            } catch (exception: InvalidProtocolBufferException) {
                throw CorruptionException("Cannot read proto.", exception)
            }
        }

        override suspend fun writeTo(
            t: AppPreferences,
            output: OutputStream,
        ) = t.writeTo(output)
    }

inline fun AppPreferences.update(block: AppPreferences.Builder.() -> Unit): AppPreferences = toBuilder().apply(block).build()

inline fun AppPreferences.updatePlaybackPreferences(block: PlaybackPreferences.Builder.() -> Unit): AppPreferences =
    update {
        playbackPreferences = playbackPreferences.toBuilder().apply(block).build()
    }

inline fun AppPreferences.updatePlaybackOverrides(block: PlaybackOverrides.Builder.() -> Unit): AppPreferences =
    updatePlaybackPreferences {
        overrides = overrides.toBuilder().apply(block).build()
    }

inline fun AppPreferences.updateMpvOptions(block: MpvOptions.Builder.() -> Unit): AppPreferences =
    updatePlaybackPreferences {
        mpvOptions = mpvOptions.toBuilder().apply(block).build()
    }

inline fun AppPreferences.updateHomePagePreferences(block: HomePagePreferences.Builder.() -> Unit): AppPreferences =
    update {
        homePagePreferences = homePagePreferences.toBuilder().apply(block).build()
    }

inline fun AppPreferences.updateInterfacePreferences(block: InterfacePreferences.Builder.() -> Unit): AppPreferences =
    update {
        interfacePreferences = interfacePreferences.toBuilder().apply(block).build()
    }

inline fun AppPreferences.updateSubtitlePreferences(block: SubtitlePreferences.Builder.() -> Unit): AppPreferences =
    updateInterfacePreferences {
        subtitlesPreferences = subtitlesPreferences.toBuilder().apply(block).build()
    }

inline fun AppPreferences.updateLiveTvPreferences(block: LiveTvPreferences.Builder.() -> Unit): AppPreferences =
    updateInterfacePreferences {
        liveTvPreferences = liveTvPreferences.toBuilder().apply(block).build()
    }

inline fun AppPreferences.updateAdvancedPreferences(block: AdvancedPreferences.Builder.() -> Unit): AppPreferences =
    update {
        advancedPreferences = advancedPreferences.toBuilder().apply(block).build()
    }

fun SubtitlePreferences.Builder.resetSubtitles() {
    fontSize = SubtitleSettings.FontSize.defaultValue.toInt()
    fontColor = SubtitleSettings.FontColor.defaultValue.toArgb()
    fontBold = SubtitleSettings.FontBold.defaultValue
    fontItalic = SubtitleSettings.FontItalic.defaultValue
    fontOpacity = SubtitleSettings.FontOpacity.defaultValue.toInt()
    edgeColor = SubtitleSettings.EdgeColor.defaultValue.toArgb()
    edgeStyle = SubtitleSettings.EdgeStylePref.defaultValue
    backgroundColor = SubtitleSettings.BackgroundColor.defaultValue.toArgb()
    backgroundOpacity = SubtitleSettings.BackgroundOpacity.defaultValue.toInt()
    backgroundStyle = SubtitleSettings.BackgroundStylePref.defaultValue
    margin = SubtitleSettings.Margin.defaultValue.toInt()
    edgeThickness = SubtitleSettings.EdgeThickness.defaultValue.toInt()
}
