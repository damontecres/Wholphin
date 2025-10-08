package com.github.damontecres.dolphin

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class DolphinApplication : Application() {
    init {
        instance = this

        if (BuildConfig.DEBUG) {
            // TODO minimal logging for release builds?
            Timber.plant(Timber.DebugTree())
        }
    }

    companion object {
        lateinit var instance: DolphinApplication
            private set
    }
}
