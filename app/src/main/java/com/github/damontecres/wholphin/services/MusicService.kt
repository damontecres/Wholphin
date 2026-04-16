package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.AudioItem
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.main.settings.MoveDirection
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.BlockingList
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.PlaybackItemState
import com.github.damontecres.wholphin.util.TrackActivityPlaybackListener
import com.github.damontecres.wholphin.util.profile.supportedAudioCodecs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.instantMixApi
import org.jellyfin.sdk.api.client.extensions.universalAudioApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Manage the global state for playing music
 *
 * Has functions for modifying the queue
 */
@OptIn(UnstableApi::class)
@Singleton
class MusicService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:AuthOkHttpClient private val authOkHttpClient: OkHttpClient,
        @param:DefaultCoroutineScope private val defaultScope: CoroutineScope,
        private val api: ApiClient,
        private val playerFactory: PlayerFactory,
        private val serverRepository: ServerRepository,
        private val imageUrlService: ImageUrlService,
    ) {
        private val _state = MutableStateFlow(MusicServiceState.EMPTY)
        val state: StateFlow<MusicServiceState> = _state

        private val audioFormats by lazy { listOf(*supportedAudioCodecs) }

        val player: Player by lazy {
            playerFactory
                .createAudioPlayer()
                .also {
                    it.addListener(MusicPlayerListener(it, _state))
                }
        }

        private val mutex = Mutex()
        var mediaSession: MediaSession? = null
            private set
        private var activityTracker: TrackActivityPlaybackListener? = null
        private var websocketJob: Job? = null

        /**
         * Start music playback
         *
         * Sets up the media session, activity tracking, and actual playback
         */
        suspend fun start() {
            if (mediaSession == null) {
                mutex.withLock {
                    if (mediaSession == null) {
                        Timber.i("Starting music MediaSession")
                        mediaSession = MediaSession.Builder(context, player).build()
                        activityTracker =
                            TrackActivityPlaybackListener(api, player) {
                                state.value.currentItemId?.let { itemId ->
                                    PlaybackItemState(
                                        itemId = itemId,
                                        playMethod = PlayMethod.DIRECT_PLAY,
//                                        playSessionId = mediaSession?.id,
                                    )
                                }
                            }.also { player.addListener(it) }
                        websocketJob = subscribe()
                    }
                }
            }
            onMain {
                player.prepare()
                player.play()
            }
        }

        /**
         * Stop music playback
         */
        suspend fun stop() {
            mutex.withLock {
                Timber.i("Stopping music")
                if (mediaSession == null) {
                    Timber.w("Stopping but no MediaSession")
                }
                mediaSession?.release()
                mediaSession = null
                onMain {
                    websocketJob?.cancel()
                    websocketJob = null
                    activityTracker?.let {
                        it.release()
                        player.removeListener(it)
                    }
                    activityTracker = null
                    player.stop()
                    player.setMediaItems(emptyList())
                }
                _state.update {
                    MusicServiceState.EMPTY
                }
            }
        }

        /**
         * Fetches instant mix items, replaces the queue, and begins playback
         */
        suspend fun startInstantMix(itemId: UUID) =
            loading {
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
            }
            start()
            addAllToQueue(items, startIndex)
        }

        /**
         * Replace the queue with the given items
         */
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
                updateQueueSize()
                start()
            }
        }

        /**
         * Add an item to the specified index of the queue. If no index specified, it will be added to the end.
         */
        suspend fun addToQueue(
            item: BaseItem,
            index: Int? = null,
        ) {
            if (item.type == BaseItemKind.AUDIO) {
                val mediaItem = convert(item)
                withContext(Dispatchers.Main) {
                    if (index != null) {
                        player.addMediaItem(index, mediaItem)
                    } else {
                        player.addMediaItem(mediaItem)
                    }
                    updateQueueSize()
                    if (player.mediaItemCount == 1) {
                        // Start playing if this was the first time added
                        start()
                    }
                }
            }
        }

        /**
         *  Add all the items in teh list to end of the queue
         *
         *  @param startIndex The index to start from within the source list
         */
        suspend fun addAllToQueue(
            list: BlockingList<BaseItem?>,
            startIndex: Int,
        ) = loading {
            var remaining = startIndex
            list.indices
                .chunked(25)
                .forEach {
                    val mediaItems =
                        it.mapNotNull {
                            if (remaining == 0) {
                                list
                                    .getBlocking(it)
                                    ?.takeIf { it.type == BaseItemKind.AUDIO }
                                    ?.let(::convert)
                            } else {
                                Timber.v("Skipping $remaining")
                                remaining--
                                null
                            }
                        }
                    onMain { player.addMediaItems(mediaItems) }
                }
            updateQueueSize()
            start()
        }

        /**
         * Converts a [BaseItem] into a [MediaItem] setting an [AudioItem] as its tag
         */
        private fun convert(audio: BaseItem): MediaItem {
            val url =
                api.universalAudioApi.getUniversalAudioStreamUrl(
                    itemId = audio.id,
                    container = audioFormats,
                )
            Timber.i("url=%s", url)
            val imageUrl =
                audio.data.albumId?.let { albumId ->
                    imageUrlService.getItemImageUrl(
                        itemId = albumId,
                        imageType = ImageType.PRIMARY,
                    )
                }
            return MediaItem
                .Builder()
                .setUri(url)
                .setMediaId(audio.id.toServerString())
                .setTag(AudioItem.from(audio, imageUrl))
                .build()
        }

        /**
         * Updates the state for when the queue changes
         */
        private suspend fun updateQueueSize() {
//            val ids =
//                withContext(Dispatchers.Default) {
//                    (0..<player.mediaItemCount).map { player.getMediaItemAt(it).mediaId.toUUID() }
//                }
            val timeline = onMain { player.currentTimeline }
            val window = Timeline.Window()
            val ids =
                (0..<timeline.windowCount)
                    .map {
                        timeline.getWindow(it, window)
                        window.mediaItem.mediaId.toUUID()
                    }.toSet()
            withContext(Dispatchers.Main) {
                _state.update {
                    it.copy(
                        queueVersion = it.queueVersion + 1,
                        queueSize = player.mediaItemCount,
                        queuedIds = ids,
                    )
                }
            }
        }

        /**
         * Move an item within the queue
         */
        suspend fun moveQueue(
            index: Int,
            direction: MoveDirection,
        ) = withContext(Dispatchers.Main) {
            player.moveMediaItem(index, if (direction == MoveDirection.UP) index - 1 else index + 1)
            updateQueueSize()
        }

        /**
         * Move an item within the queue
         */
        suspend fun moveQueue(
            index: Int,
            newIndex: Int,
        ) = withContext(Dispatchers.Main) {
            player.moveMediaItem(index, newIndex)
            updateQueueSize()
        }

        /**
         * Start playback at the given index of the queue
         */
        suspend fun playIndex(index: Int) {
            onMain {
                player.seekTo(index, 0L)
                player.play()
            }
            // MusicPlayerListener will update state
        }

        /**
         * Play this item next after the current, ie add the item as the next index in the queue
         */
        suspend fun playNext(song: BaseItem) {
            val mediaItem = convert(song)
            onMain {
                player.addMediaItem(state.value.currentIndex + 1, mediaItem)
                if (player.mediaItemCount == 1) {
                    start()
                }
            }
            updateQueueSize()
        }

        /**
         * From the item at the given index from the queue
         */
        suspend fun removeFromQueue(index: Int) {
            onMain { player.removeMediaItem(index) }
            updateQueueSize()
        }

        private suspend fun <T> loading(block: suspend () -> T): T {
            _state.update { it.copy(loadingState = LoadingState.Loading) }
            val result = block.invoke()
            _state.update { it.copy(loadingState = LoadingState.Success) }
            return result
        }

        /**
         * Subscribes to the server websocket to receive playback commands
         */
        private fun subscribe(): Job =
            api.webSocket
                .subscribe<PlaystateMessage>()
                .onEach { message ->
                    message.data?.let {
                        withContext(Dispatchers.Main) {
                            when (it.command) {
                                PlaystateCommand.STOP -> {
                                    stop()
                                }

                                PlaystateCommand.PAUSE -> {
                                    player.pause()
                                }

                                PlaystateCommand.UNPAUSE -> {
                                    player.play()
                                }

                                PlaystateCommand.NEXT_TRACK -> {
                                    player.seekToNext()
                                }

                                PlaystateCommand.PREVIOUS_TRACK -> {
                                    player.seekToPrevious()
                                }

                                PlaystateCommand.SEEK -> {
                                    it.seekPositionTicks?.ticks?.let {
                                        player.seekTo(
                                            it.inWholeMilliseconds,
                                        )
                                    }
                                }

                                PlaystateCommand.REWIND -> {
                                    player.seekBack(10.seconds)
                                }

                                PlaystateCommand.FAST_FORWARD -> {
                                    player.seekForward(30.seconds)
                                }

                                PlaystateCommand.PLAY_PAUSE -> {
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            }
                        }
                    }
                }.catch { ex ->
                    Timber.e(ex, "Error in websocket subscription")
                }.launchIn(defaultScope)
    }

@Stable
data class MusicServiceState(
    val queueVersion: Long,
    val queueSize: Int,
    val currentIndex: Int,
    val currentItemId: UUID?,
    val status: NowPlayingStatus,
    val currentItemTitle: String?,
    val loadingState: LoadingState = LoadingState.Pending,
    val queuedIds: Set<UUID>,
) {
    companion object {
        val EMPTY =
            MusicServiceState(
                0L,
                0,
                0,
                null,
                NowPlayingStatus.IDLE,
                null,
                LoadingState.Pending,
                emptySet(),
            )
    }
}

enum class NowPlayingStatus {
    PLAYING,
    PAUSED,
    IDLE,
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
                queueSize = player.mediaItemCount,
                currentIndex = player.currentMediaItemIndex,
                status = if (player.isPlaying) NowPlayingStatus.PLAYING else NowPlayingStatus.IDLE,
            )
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        Timber.v("MusicPlayerListener onIsPlayingChanged")
        state.update {
            it.copy(
                status = if (isPlaying) NowPlayingStatus.PLAYING else NowPlayingStatus.PAUSED,
            )
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        Timber.v("MusicPlayerListener onMediaItemTransition")
        updateCurrentIndex()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
//        Timber.v("MusicPlayerListener onTimelineChanged")
        if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
            updateCurrentIndex()
        }
    }

    private fun updateCurrentIndex() {
        state.update { state ->
            player.currentMediaItemIndex.takeIf { it >= 0 }?.let { currentMediaItemIndex ->
                if (currentMediaItemIndex in (0..<player.mediaItemCount)) {
                    val item =
                        player.getMediaItemAt(currentMediaItemIndex).localConfiguration?.tag as? AudioItem
                    state.copy(
                        currentIndex = currentMediaItemIndex,
                        currentItemId = player.getMediaItemAt(currentMediaItemIndex).mediaId.toUUIDOrNull(),
                        currentItemTitle = item?.title,
                    )
                } else {
                    state
                }
            } ?: state
        }
    }
}

/**
 * Remember the queue currently playing
 *
 * @see MusicServiceState
 */
@Composable
fun rememberQueue(
    player: Player,
    queueVersion: Long,
    queueSize: Int,
): List<AudioItem> =
    remember(queueVersion, queueSize) {
        object : AbstractList<AudioItem>() {
            override val size: Int
                get() = player.mediaItemCount

            override fun get(index: Int): AudioItem = player.getMediaItemAt(index).localConfiguration?.tag as AudioItem
        }
    }
