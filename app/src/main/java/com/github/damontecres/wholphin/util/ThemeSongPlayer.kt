package com.github.damontecres.wholphin.util

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.github.damontecres.wholphin.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.preferences.ThemeSongVolume
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple service to play theme song music
 */
@Singleton
class ThemeSongPlayer
    @Inject
    constructor(
        @param:ApplicationContext val context: Context,
        @param:AuthOkHttpClient val okHttpClient: OkHttpClient,
    ) {
        private var player: Player? = null

        @OptIn(UnstableApi::class)
        fun play(
            volume: ThemeSongVolume,
            url: String,
        ) {
            stop()
            val volumeLevel =
                when (volume) {
                    ThemeSongVolume.UNRECOGNIZED,
                    ThemeSongVolume.DISABLED,
                    -> return

                    ThemeSongVolume.LOWEST -> .05f
                    ThemeSongVolume.LOW -> .1f
                    ThemeSongVolume.MEDIUM -> .25f
                    ThemeSongVolume.HIGH -> .5f
                    ThemeSongVolume.HIGHEST -> 75f
                }
            val player =
                ExoPlayer
                    .Builder(context)
                    .setMediaSourceFactory(
                        DefaultMediaSourceFactory(
                            OkHttpDataSource.Factory(okHttpClient),
                        ),
                    ).build()
                    .apply {
                        this.volume = volumeLevel
                        playWhenReady = true
                    }
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            this.player = player
        }

        fun stop() {
            player?.stop()
            player?.release()
            player = null
        }
    }
