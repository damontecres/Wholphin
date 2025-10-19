package com.github.damontecres.wholphin.util

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.github.damontecres.wholphin.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.hilt.StandardOkHttpClient
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerFactory
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:StandardOkHttpClient private val standardOkHttpClient: OkHttpClient,
        @param:AuthOkHttpClient private val authOkHttpClient: OkHttpClient,
    ) {
        private var player: Player? = null

        @OptIn(UnstableApi::class)
        fun create(useAuth: Boolean): Player {
            release()
            val player =
                ExoPlayer
                    .Builder(context)
                    .setMediaSourceFactory(
                        DefaultMediaSourceFactory(
                            OkHttpDataSource.Factory(if (useAuth) authOkHttpClient else standardOkHttpClient),
                        ),
                    ).build()
            this.player = player
            return player
        }

        fun release() {
            player?.let {
                it.stop()
                it.release()
            }
            player = null
        }
    }
