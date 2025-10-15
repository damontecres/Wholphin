package com.github.damontecres.wholphin

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

@HiltAndroidApp
class WholphinApplication : Application() {
    init {
        instance = this

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            Timber.plant(
                object : Timber.Tree() {
                    override fun isLoggable(
                        tag: String?,
                        priority: Int,
                    ): Boolean = priority >= Log.INFO

                    override fun log(
                        priority: Int,
                        tag: String?,
                        message: String,
                        t: Throwable?,
                    ) {
                        Log.println(priority, tag ?: "Wholphin", message)
                    }
                },
            )
        }
    }

    companion object {
        lateinit var instance: WholphinApplication
            private set
    }
}
