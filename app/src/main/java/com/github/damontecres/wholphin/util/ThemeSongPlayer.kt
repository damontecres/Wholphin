package com.github.damontecres.wholphin.util

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.github.damontecres.wholphin.preferences.ThemeSongVolume
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple service to play theme song music
 */
@Singleton
class ThemeSongPlayer
    @Inject
    constructor(
        val playerFactory: PlayerFactory,
    ) {
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
                playerFactory
                    .create(true)
                    .apply {
                        this.volume = volumeLevel
                        playWhenReady = true
                    }
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
        }

        fun stop() {
            playerFactory.release()
        }
    }
