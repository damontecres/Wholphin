package com.github.damontecres.wholphin.util

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.preference.PreferenceManager
import com.github.damontecres.wholphin.WholphinApplication
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.updatePlaybackPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
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
                    upgradeApp(
                        context,
                        Version.fromString(previousVersion ?: "0.0.0"),
                        Version.fromString(newVersion),
                        appPreferences,
                    )
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception during app upgrade")
                }
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
    if (previous.isEqualOrBefore(Version.fromString("0.1.0-1-g0"))) {
        appPreferences.updateData {
            it.updatePlaybackPreferences { ac3Supported = AppPreference.Ac3Supported.defaultValue }
        }
    }
}
