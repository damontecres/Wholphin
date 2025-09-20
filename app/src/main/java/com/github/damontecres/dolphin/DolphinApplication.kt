package com.github.damontecres.dolphin

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DolphinApplication: Application() {
    init {
        instance = this
    }

    companion object{
        lateinit var instance: DolphinApplication
            private set
    }

}