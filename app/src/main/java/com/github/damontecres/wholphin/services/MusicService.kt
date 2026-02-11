package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.profile.Codec
import dagger.hilt.android.qualifiers.ApplicationContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Singleton
class MusicService
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

        fun addToQueue(
            item: BaseItem,
            position: Int? = null,
        ) {
            if (item.type == BaseItemKind.AUDIO) {
                val url =
                    api.universalAudioApi.getUniversalAudioStreamUrl(
                        itemId = item.id,
                        container =
                            listOf(
                                Codec.Audio.OPUS,
                                Codec.Audio.MP3,
                                Codec.Audio.AAC,
                                Codec.Audio.FLAC,
                            ),
                    )
                val mediaItem =
                    MediaItem
                        .Builder()
                        .setUri(url)
                        .setMediaId(item.id.toServerString())
                        .setTag(item)
                        .build()
                player.addMediaItem(mediaItem)
                if (player.mediaItemCount == 1) {
                    // Start playing if this was the first time added
                    player.play()
                }
            }
        }
    }
