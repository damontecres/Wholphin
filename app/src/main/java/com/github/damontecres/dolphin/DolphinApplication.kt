package com.github.damontecres.dolphin

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class DolphinApplication : Application() {
    init {
        instance = this

        // TODO only plant in debug builds
        Timber.plant(Timber.DebugTree())
    }

    companion object {
        lateinit var instance: DolphinApplication
            private set
    }
}
