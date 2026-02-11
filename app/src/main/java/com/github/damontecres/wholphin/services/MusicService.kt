package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.BlockingList
import com.github.damontecres.wholphin.util.profile.Codec
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.instantMixApi
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
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
        private val serverRepository: ServerRepository,
    ) {
        private val _state = MutableStateFlow(MusicServiceState.EMPTY)
        val state: StateFlow<MusicServiceState> = _state

        val player: Player by lazy {
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(
                        OkHttpDataSource.Factory(authOkHttpClient),
                    ),
                ).build()
                .also {
                    it.addListener(MusicPlayerListener(it, _state))
                    it.prepare()
                }
        }

        /**
         * Fetches instant mix items, replaces the queue, and begins playback
         */
        suspend fun startInstantMix(itemId: UUID) {
            val items =
                api.instantMixApi
                    .getInstantMixFromItem(
                        userId = serverRepository.currentUser.value?.id,
                        itemId = itemId,
                        limit = 200,
                        fields = DefaultItemFields,
                    ).content.items
                    .map { BaseItem(it, false) }
            setQueue(items, false)
        }

        /**
         * Replace the queue with the given list and starting playing the song as startIndex as soon as its ready
         *
         * Fetches each item in a blocking way and adds to the queue
         */
        suspend fun setQueue(
            items: BlockingList<BaseItem?>,
            startIndex: Int,
            shuffled: Boolean,
        ) = withContext(Dispatchers.IO) {
            Timber.d("setQueue: %s items, startIndex=%s, shuffled=%s", items.size, startIndex, shuffled)
            withContext(Dispatchers.Main) {
                player.setMediaItems(emptyList())
                player.shuffleModeEnabled = shuffled
                player.play()
            }
            (startIndex..items.lastIndex).forEach {
                val item = items.getBlocking(it)
                if (item != null && item.type == BaseItemKind.AUDIO) {
                    val mediaItem = convert(item)
                    withContext(Dispatchers.Main) {
                        player.addMediaItem(mediaItem)
                    }
                }
            }
        }

        suspend fun setQueue(
            items: List<BaseItem>,
            shuffled: Boolean,
        ) {
            Timber.d("setQueue: %s items, shuffled=%s", items.size, shuffled)
            val mediaItems =
                items
                    .filter { it.type == BaseItemKind.AUDIO }
                    .map(::convert)
            withContext(Dispatchers.Main) {
                player.setMediaItems(mediaItems)
                player.shuffleModeEnabled = shuffled
                player.play()
            }
        }

        suspend fun addToQueue(
            item: BaseItem,
            position: Int? = null,
        ) {
            if (item.type == BaseItemKind.AUDIO) {
                val mediaItem = convert(item)
                withContext(Dispatchers.Main) {
                    player.addMediaItem(mediaItem)
                    if (player.mediaItemCount == 1) {
                        // Start playing if this was the first time added
                        player.play()
                    }
                }
            }
        }

        private fun convert(audio: BaseItem): MediaItem {
            val url =
                api.universalAudioApi.getUniversalAudioStreamUrl(
                    itemId = audio.id,
                    container =
                        listOf(
                            Codec.Audio.OPUS,
                            Codec.Audio.MP3,
                            Codec.Audio.AAC,
                            Codec.Audio.FLAC,
                        ),
                )
            return MediaItem
                .Builder()
                .setUri(url)
                .setMediaId(audio.id.toServerString())
                .setTag(AudioItem.from(audio))
                .build()
        }
    }

data class MusicServiceState(
    val queue: List<AudioItem>,
    val currentIndex: Int,
    val isPlaying: Boolean,
) {
    companion object {
        val EMPTY = MusicServiceState(emptyList(), 0, false)
    }

    val currentItemId: UUID? get() = if (isPlaying) queue.getOrNull(currentIndex)?.id else null
}

/**
 * Listens to [Player] events and updates the [StateFlow]
 */
private class MusicPlayerListener(
    private val player: Player,
    private val state: MutableStateFlow<MusicServiceState>,
) : Player.Listener {
    init {
        Timber.v("MusicPlayerListener init")
        state.update {
            it.copy(
                queue = PlayerMediaItemList(player),
                currentIndex = player.currentMediaItemIndex,
                isPlaying = player.isPlaying,
            )
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        Timber.v("MusicPlayerListener onIsPlayingChanged")
        state.update {
            it.copy(
                isPlaying = isPlaying,
            )
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        Timber.v("MusicPlayerListener onMediaItemTransition")
        state.update {
            it.copy(
                currentIndex = player.currentMediaItemIndex,
            )
        }
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        Timber.v("MusicPlayerListener onTimelineChanged")
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            state.update {
                it.copy(
                    queue = PlayerMediaItemList(player),
                    currentIndex = player.currentMediaItemIndex,
                )
            }
        }
    }
}

private class PlayerMediaItemList(
    private val player: Player,
) : AbstractList<AudioItem>() {
    override fun get(index: Int): AudioItem {
//        Timber.v("get %s", index)
        return player.getMediaItemAt(index).localConfiguration?.tag as AudioItem
    }

    override val size: Int
        get() {
//            Timber.v("size %s", player.mediaItemCount)
            return player.mediaItemCount
        }
}
