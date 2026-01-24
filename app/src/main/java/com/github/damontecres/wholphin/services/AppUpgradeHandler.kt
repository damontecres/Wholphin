package com.github.damontecres.wholphin.services

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.preference.PreferenceManager
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.WholphinApplication
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.update
import com.github.damontecres.wholphin.preferences.updateAdvancedPreferences
import com.github.damontecres.wholphin.preferences.updateInterfacePreferences
import com.github.damontecres.wholphin.preferences.updateLiveTvPreferences
import com.github.damontecres.wholphin.preferences.updateMpvOptions
import com.github.damontecres.wholphin.preferences.updatePlaybackOverrides
import com.github.damontecres.wholphin.preferences.updatePlaybackPreferences
import com.github.damontecres.wholphin.preferences.updateSubtitlePreferences
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.Version
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpgradeHandler
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val appPreferences: DataStore<AppPreferences>,
    ) {
        suspend fun run() {
            val pkgInfo = WholphinApplication.instance.packageManager.getPackageInfo(WholphinApplication.instance.packageName, 0)
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val previousVersion = prefs.getString(VERSION_NAME_CURRENT_KEY, null)
            val previousVersionCode = prefs.getLong(VERSION_CODE_CURRENT_KEY, -1)

            val newVersion = pkgInfo.versionName!!
            val newVersionCode =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    pkgInfo.versionCode.toLong()
                }
            if (newVersion != previousVersion || newVersionCode != previousVersionCode) {
                Timber.i(
                    "App updated: $previousVersion=>$newVersion, $previousVersionCode=>$newVersionCode",
                )
                prefs.edit(true) {
                    putString(VERSION_NAME_PREVIOUS_KEY, previousVersion)
                    putLong(VERSION_CODE_PREVIOUS_KEY, previousVersionCode)
                    putString(VERSION_NAME_CURRENT_KEY, newVersion)
                    putLong(VERSION_CODE_CURRENT_KEY, newVersionCode)
                }
                try {
                    copySubfont(true)
                    upgradeApp(
                        context,
                        Version.Companion.fromString(previousVersion ?: "0.0.0"),
                        Version.Companion.fromString(newVersion),
                        appPreferences,
                    )
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception during app upgrade")
                }
            }
        }

        fun copySubfont(overwrite: Boolean) {
            try {
                val fontFileName = "subfont.ttf"
                val outputFile = File(context.filesDir, fontFileName)
                if (!outputFile.exists() || overwrite) {
                    context.assets.open(fontFileName).use { input ->
                        outputFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Timber.i("Wrote font %s to local", fontFileName)
                }
//                val oldFontDir = File(context.filesDir, "fonts")
//                if (oldFontDir.exists()) {
//                    oldFontDir.deleteRecursively()
//                }
            } catch (ex: Exception) {
                Timber.e(ex, "Exception copying subfont.tff")
            }
        }

        companion object {
            const val VERSION_NAME_PREVIOUS_KEY = "version.previous.name"
            const val VERSION_CODE_PREVIOUS_KEY = "version.previous.code"
            const val VERSION_NAME_CURRENT_KEY = "version.current.name"
            const val VERSION_CODE_CURRENT_KEY = "version.current.code"
        }
    }

suspend fun upgradeApp(
    context: Context,
    previous: Version,
    current: Version,
    appPreferences: DataStore<AppPreferences>,
) {
    if (previous.isEqualOrBefore(Version.fromString("0.1.0-2-g0"))) {
        appPreferences.updateData {
            it.updatePlaybackOverrides {
                ac3Supported = AppPreference.Ac3Supported.defaultValue
                downmixStereo = AppPreference.DownMixStereo.defaultValue
                directPlayAss = AppPreference.DirectPlayAss.defaultValue
                directPlayPgs = AppPreference.DirectPlayPgs.defaultValue
            }
        }
    }
    if (previous.isEqualOrBefore(Version.fromString("0.2.3-6-g0"))) {
        appPreferences.updateData {
            it.updateInterfacePreferences {
                navDrawerSwitchOnFocus = AppPreference.NavDrawerSwitchOnFocus.defaultValue
            }
        }
    }
    if (previous.isEqualOrBefore(Version.fromString("0.2.5-11-g0"))) {
        appPreferences.updateData {
            it.updateInterfacePreferences {
                showClock = AppPreference.ShowClock.defaultValue
            }
        }
    }
    if (previous.isEqualOrBefore(Version.fromString("0.2.7-1-g0"))) {
        PreferencesViewModel.resetSubtitleSettings(appPreferences)
    }
    if (previous.isEqualOrBefore(Version.fromString("0.3.2-4-g0"))) {
        appPreferences.updateData {
            it.updateSubtitlePreferences {
                margin = SubtitleSettings.Margin.defaultValue.toInt()
            }
        }
    }

    if (previous.isEqualOrBefore(Version.fromString("0.3.4"))) {
        appPreferences.updateData {
            it.updateAdvancedPreferences {
                if (imageDiskCacheSizeBytes < (AppPreference.ImageDiskCacheSize.min * AppPreference.MEGA_BIT)) {
                    imageDiskCacheSizeBytes =
                        AppPreference.ImageDiskCacheSize.defaultValue * AppPreference.MEGA_BIT
                }
            }
        }
    }

    if (previous.isEqualOrBefore(Version.fromString("0.3.4-2-g0"))) {
        appPreferences.updateData {
            it.updateMpvOptions {
                useGpuNext = AppPreference.MpvGpuNext.defaultValue
            }
        }
    }

    if (previous.isEqualOrBefore(Version.fromString("0.3.4-4-g0"))) {
        appPreferences.updateData {
            it.update {
                signInAutomatically = AppPreference.SignInAuto.defaultValue
            }
        }
    }

    if (previous.isEqualOrBefore(Version.fromString("0.3.5-0-g0"))) {
        appPreferences.updateData {
            it.updateSubtitlePreferences {
                if (edgeThickness < 1) {
                    edgeThickness = SubtitleSettings.EdgeThickness.defaultValue.toInt()
                }
            }
        }
    }
    if (previous.isEqualOrBefore(Version.fromString("0.3.5-56-g0"))) {
        appPreferences.updateData {
            it.updateLiveTvPreferences {
                showHeader = AppPreference.LiveTvShowHeader.defaultValue
                favoriteChannelsAtBeginning =
                    AppPreference.LiveTvFavoriteChannelsBeginning.defaultValue
                sortByRecentlyWatched =
                    AppPreference.LiveTvChannelSortByWatched.defaultValue
                colorCodePrograms =
                    AppPreference.LiveTvColorCodePrograms.defaultValue
            }
        }
    }

    if (previous.isEqualOrBefore(Version.fromString("0.3.6-52-g0"))) {
        if (Build.MODEL.equals("shield android tv", ignoreCase = true)) {
            appPreferences.updateData {
                it.updateMpvOptions {
                    useGpuNext = false
                }
            }
        }
    }

    if (previous.isEqualOrBefore(Version.fromString("0.4.0-1-g0"))) {
        appPreferences.updateData {
            it.updatePlaybackPreferences { playerBackend = PlayerBackend.PREFER_MPV }
        }
        showToast(context, context.getString(R.string.upgrade_mpv_toast), Toast.LENGTH_LONG)
    }

    if (previous.isEqualOrBefore(Version.fromString("0.4.0-2-g0"))) {
        appPreferences.updateData {
            it.updateMpvOptions {
                useGpuNext = false
            }
        }
    }
}
