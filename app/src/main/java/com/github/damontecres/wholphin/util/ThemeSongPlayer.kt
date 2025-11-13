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
import com.github.damontecres.wholphin.util.profile.Codec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple service to play theme song music
 */
@OptIn(UnstableApi::class)
@Singleton
class ThemeSongPlayer
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:AuthOkHttpClient private val authOkHttpClient: OkHttpClient,
        private val api: ApiClient,
    ) {
        private val player: Player by lazy {
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(
                        OkHttpDataSource.Factory(authOkHttpClient),
                    ),
                ).build()
        }

        suspend fun playThemeFor(
            itemId: UUID,
            volume: ThemeSongVolume,
        ): Boolean =
            withContext(Dispatchers.IO) {
                if (volume == ThemeSongVolume.DISABLED || volume == ThemeSongVolume.UNRECOGNIZED) {
                    return@withContext false
                }
                val themeSongs by api.libraryApi.getThemeSongs(itemId)
                return@withContext themeSongs.items.randomOrNull()?.let { theme ->
                    val url =
                        api.universalAudioApi.getUniversalAudioStreamUrl(
                            theme.id,
                            container =
                                listOf(
                                    Codec.Audio.OPUS,
                                    Codec.Audio.MP3,
                                    Codec.Audio.AAC,
                                    Codec.Audio.FLAC,
                                ),
                        )
                    Timber.v("Found theme song for $itemId")
                    withContext(Dispatchers.Main) {
                        play(volume, url)
                    }
                    true
                } ?: false
            }

        private fun play(
            volumeLevel: ThemeSongVolume,
            url: String,
        ) {
            val volumeLevel =
                when (volumeLevel) {
                    ThemeSongVolume.UNRECOGNIZED,
                    ThemeSongVolume.DISABLED,
                    -> return

                    ThemeSongVolume.LOWEST -> .05f
                    ThemeSongVolume.LOW -> .1f
                    ThemeSongVolume.MEDIUM -> .25f
                    ThemeSongVolume.HIGH -> .5f
                    ThemeSongVolume.HIGHEST -> 75f
                }
            player.apply {
                stop()
                volume = volumeLevel
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                play()
            }
        }

        fun stop() {
            Timber.v("Stopping theme song")
            player.stop()
        }
    }
