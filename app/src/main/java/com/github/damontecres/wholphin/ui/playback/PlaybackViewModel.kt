package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.ui.text.intl.Locale
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.data.model.PlaylistCreator
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.data.model.chooseSource
import com.github.damontecres.wholphin.data.model.chooseStream
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.SkipSegmentBehavior
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.EqualityMutableLiveData
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.TrackActivityPlaybackListener
import com.github.damontecres.wholphin.util.TrackSupport
import com.github.damontecres.wholphin.util.checkForSupport
import com.github.damontecres.wholphin.util.formatDateTime
import com.github.damontecres.wholphin.util.seasonEpisodePadded
import com.github.damontecres.wholphin.util.subtitleMimeTypes
import com.github.damontecres.wholphin.util.supportItemKinds
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.subtitleApi
import org.jellyfin.sdk.api.client.extensions.trickplayApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo
import org.jellyfin.sdk.model.api.TrickplayInfo
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

enum class TranscodeType {
    DIRECT_PLAY,
    DIRECT_STREAM,
    TRANSCODE,
}

data class StreamDecision(
    val itemId: UUID,
    val type: PlayMethod,
    val url: String,
)

@HiltViewModel
class PlaybackViewModel
    @Inject
    constructor(
        @param:ApplicationContext val context: Context,
        val api: ApiClient,
        val playlistCreator: PlaylistCreator,
        val navigationManager: NavigationManager,
        val itemPlaybackDao: ItemPlaybackDao,
        val serverRepository: ServerRepository,
        val itemPlaybackRepository: ItemPlaybackRepository,
    ) : ViewModel(),
        Player.Listener {
        val player =
            ExoPlayer
                .Builder(context)
                .build()
                .apply {
                    playWhenReady = true
                }

        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)

        val title = MutableLiveData<String?>(null)
        val subtitle = MutableLiveData<String?>(null)
        val duration = MutableLiveData<Duration?>(null)
        val audioStreams = MutableLiveData<List<AudioStream>>(listOf())
        val subtitleStreams = MutableLiveData<List<SubtitleStream>>(listOf())
        val currentPlayback = MutableLiveData<CurrentPlayback?>(null)
        val currentItemPlayback = MutableLiveData<ItemPlayback>()
        val trickplay = MutableLiveData<TrickplayInfo?>(null)
        val chapters = MutableLiveData<List<Chapter>>(listOf())
        val currentSegment = EqualityMutableLiveData<MediaSegmentDto?>(null)

        private lateinit var preferences: UserPreferences
        private lateinit var deviceProfile: DeviceProfile
        private lateinit var itemId: UUID
        private lateinit var dto: BaseItemDto
        private var activityListener: TrackActivityPlaybackListener? = null

        val nextUp = MutableLiveData<BaseItem?>()
        private var isPlaylist = false

        val playlist = MutableLiveData<Playlist>(Playlist(listOf()))

        init {
            player.addListener(this)
            addCloseable { player.removeListener(this@PlaybackViewModel) }
            addCloseable {
                this@PlaybackViewModel.activityListener?.let {
                    it.release()
                    player.removeListener(it)
                }
            }
            addCloseable { player.release() }
            subscribe()
        }

        fun init(
            destination: Destination.Playback,
            deviceProfile: DeviceProfile,
            preferences: UserPreferences,
        ) {
            nextUp.value = null
            this.preferences = preferences
            this.deviceProfile = deviceProfile
            val itemId = destination.itemId
            this.itemId = itemId
            val item = destination.item
            viewModelScope.launch(
                LoadingExceptionHandler(
                    loading,
                    "Error preparing for playback for ${destination.itemId}",
                ) + Dispatchers.IO,
            ) {
                val queriedItem = item?.data ?: api.userLibraryApi.getItem(itemId).content
                val base =
                    if (queriedItem.type == BaseItemKind.PLAYLIST) {
                        isPlaylist = true
                        val playlist =
                            playlistCreator.createFromPlaylistId(
                                queriedItem.id,
                                destination.startIndex,
                                destination.shuffle,
                            )
                        withContext(Dispatchers.Main) {
                            this@PlaybackViewModel.playlist.value = playlist
                        }
                        // TODO start index
                        playlist.items.first().data
                    } else {
                        queriedItem
                    }

                val played = play(base, destination.positionMs, destination.itemPlayback)
                if (!played) {
                    playUpNextUp()
                }

                if (!isPlaylist && queriedItem.type == BaseItemKind.EPISODE) {
                    val playlist =
                        playlistCreator.createFromEpisode(queriedItem.seriesId!!, queriedItem.id)
                    withContext(Dispatchers.Main) {
                        this@PlaybackViewModel.playlist.value = playlist
                    }
                }
                maybeSetupPlaylistListener()
            }
        }

        private suspend fun play(
            base: BaseItemDto,
            positionMs: Long,
            itemPlayback: ItemPlayback? = null,
        ): Boolean =
            withContext(Dispatchers.IO) {
                Timber.i("Playing ${base.id}")
                if (base.type !in supportItemKinds) {
                    showToast(
                        context,
                        "Unsupported type '${base.type}', skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }
                val isLiveTv = base.type == BaseItemKind.TV_CHANNEL

                dto = base
                val title =
                    if (base.type == BaseItemKind.EPISODE) {
                        base.seriesName
                    } else {
                        base.name
                    }
                val subtitle =
                    if (base.type == BaseItemKind.EPISODE) {
                        buildList {
                            add(base.seasonEpisodePadded)
                            add(base.name)
                            add(base.premiereDate?.let { formatDateTime(it) })
                        }.filterNotNull().joinToString(" - ")
                    } else {
                        base.productionYear?.toString()
                    }
                withContext(Dispatchers.Main) {
                    this@PlaybackViewModel.title.value = title
                    this@PlaybackViewModel.subtitle.value = subtitle
                }

                val playbackConfig =
                    if (itemPlayback != null) {
                        itemPlayback
                    } else {
                        val user = serverRepository.currentUser!!
                        itemPlaybackDao.getItem(user, base.id)?.let {
                            Timber.v("Fetched itemPlayback from DB: %s", it)
                            if (it.sourceId != null) {
                                it
                            } else {
                                null
                            }
                        }
                    }
                val mediaSource = chooseSource(base, playbackConfig)

                if (mediaSource == null) {
                    showToast(
                        context,
                        "Item has no media sources, skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }

//                mediaSource.mediaStreams
//                    ?.filter { it.type == MediaStreamType.VIDEO }
//                    ?.forEach { Timber.v("${it.videoRangeType}, ${it.videoRange}") }
                val subtitleStreams =
                    mediaSource.mediaStreams
                        ?.filter { it.type == MediaStreamType.SUBTITLE }
                        ?.map {
                            SubtitleStream(
                                it.index,
                                it.language,
                                it.title,
                                it.codec,
                                it.codecTag,
                                it.isExternal,
                                it.isForced,
                                it.isDefault,
                                it.displayTitle,
                            )
                        }.orEmpty()
                val audioStreams =
                    mediaSource.mediaStreams
                        ?.filter { it.type == MediaStreamType.AUDIO }
                        ?.map {
                            AudioStream(
                                it.index,
                                it.language,
                                it.title,
                                it.codec,
                                it.codecTag,
                                it.channels,
                                it.channelLayout,
                            )
                        }?.sortedWith(compareBy<AudioStream> { it.language }.thenByDescending { it.channels })
                        .orEmpty()

                val audioIndex =
                    chooseStream(base, playbackConfig, MediaStreamType.AUDIO, preferences)
                        ?.index

                val subtitleIndex =
                    chooseStream(base, playbackConfig, MediaStreamType.SUBTITLE, preferences)
                        ?.index

//                Timber.v("base.mediaStreams=${base.mediaStreams}")
//                Timber.v("subtitleTracks=$subtitleStreams")
//                Timber.v("audioStreams=$audioStreams")
                Timber.d("Selected mediaSource=${mediaSource.id}, audioIndex=$audioIndex, subtitleIndex=$subtitleIndex")

                val itemPlaybackToUse =
                    playbackConfig ?: ItemPlayback(
                        rowId = -1,
                        userId = -1,
                        itemId = base.id,
                        sourceId = if (!isLiveTv) mediaSource.id?.toUUIDOrNull() else null,
                        audioIndex = audioIndex ?: TrackIndex.UNSPECIFIED,
                        subtitleIndex = subtitleIndex ?: TrackIndex.UNSPECIFIED,
                    )
                withContext(Dispatchers.Main) {
                    this@PlaybackViewModel.currentItemPlayback.value = itemPlaybackToUse
                }

                withContext(Dispatchers.Main) {
                    this@PlaybackViewModel.audioStreams.value = audioStreams
                    this@PlaybackViewModel.subtitleStreams.value = subtitleStreams

                    changeStreams(
                        base,
                        itemPlaybackToUse,
                        audioIndex,
                        subtitleIndex,
                        if (positionMs > 0) positionMs else C.TIME_UNSET,
                        itemPlayback != null, // If it was passed in, then it was not queried from the database
                    )
                    player.prepare()

                    this@PlaybackViewModel.chapters.value = Chapter.fromDto(base, api)
                    Timber.v("chapters=${this@PlaybackViewModel.chapters.value?.size}")
                }
                listenForSegments()
                return@withContext true
            }

        @OptIn(UnstableApi::class)
        private suspend fun changeStreams(
            item: BaseItemDto,
            currentItemPlayback: ItemPlayback = this@PlaybackViewModel.currentItemPlayback.value!!,
            audioIndex: Int?,
            subtitleIndex: Int?,
            positionMs: Long = C.TIME_UNSET,
            userInitiated: Boolean,
        ) = withContext(Dispatchers.IO) {
            val itemId = item.id

            // TODO
//            if (currentItemPlayback.let {
//                    it.itemId == itemId &&
//                        it.audioIndex == audioIndex &&
//                        it.subtitleIndex == subtitleIndex
//                } == true
//            ) {
//                Timber.i("No change in playback for changeStreams")
//                return@withContext
//            }
            Timber.d("changeStreams: userInitiated=$userInitiated, audioIndex=$audioIndex, subtitleIndex=$subtitleIndex")

            // TODO if the new audio or subtitle index is already in the streams (eg direct play), should toggle in the player instead
            val maxBitrate =
                preferences.appPreferences.playbackPreferences.maxBitrate
                    .takeIf { it > 0 } ?: AppPreference.DEFAULT_BITRATE
            val response by
                api.mediaInfoApi
                    .getPostedPlaybackInfo(
                        itemId,
                        PlaybackInfoDto(
                            startTimeTicks = null,
                            deviceProfile = deviceProfile,
                            enableDirectPlay = true,
                            enableDirectStream = true,
                            maxAudioChannels = null,
                            audioStreamIndex = audioIndex,
                            subtitleStreamIndex = subtitleIndex,
                            allowVideoStreamCopy = true,
                            allowAudioStreamCopy = true,
                            autoOpenLiveStream = true,
                            mediaSourceId = currentItemPlayback.sourceId?.toServerString(),
                            alwaysBurnInSubtitleWhenTranscoding = null,
                            maxStreamingBitrate = maxBitrate.toInt(),
                        ),
                    )
            if (response.errorCode != null) {
                loading.setValueOnMain(LoadingState.Error(response.errorCode?.serialName))
                return@withContext
            }
            val source = response.mediaSources.firstOrNull()
            source?.let { source ->
                val mediaUrl =
                    if (source.supportsDirectPlay) {
                        api.videosApi.getVideoStreamUrl(
                            itemId = itemId,
                            mediaSourceId = source.id,
                            static = true,
                            tag = source.eTag,
                            playSessionId = response.playSessionId,
                        )
                    } else if (source.supportsDirectStream) {
                        api.createUrl(source.transcodingUrl!!)
                    } else {
                        api.createUrl(source.transcodingUrl!!)
                    }
                val transcodeType =
                    when {
                        source.supportsDirectPlay -> PlayMethod.DIRECT_PLAY
                        source.supportsDirectStream -> PlayMethod.DIRECT_STREAM
                        source.supportsTranscoding -> PlayMethod.TRANSCODE
                        else -> throw Exception("No supported playback method")
                    }
                val decision = StreamDecision(itemId, transcodeType, mediaUrl)
                Timber.v("Playback decision: $decision")

                val externalSubtitleCount =
                    source.mediaStreams
                        ?.count { it.type == MediaStreamType.SUBTITLE && it.isExternal } ?: 0

                val externalSubtitle =
                    source.mediaStreams
                        ?.firstOrNull { it.type == MediaStreamType.SUBTITLE && it.index == subtitleIndex && it.isExternal }
                        ?.let {
                            it.deliveryUrl?.let { deliveryUrl ->
                                var flags = 0
                                if (it.isForced) flags = flags.or(C.SELECTION_FLAG_FORCED)
                                if (it.isDefault) flags = flags.or(C.SELECTION_FLAG_DEFAULT)
                                MediaItem.SubtitleConfiguration
                                    .Builder(
                                        api.createUrl(deliveryUrl).toUri(),
                                    ).setId("${it.index + 1}") // Indexes are 1 based
                                    .setMimeType(subtitleMimeTypes[it.codec])
                                    .setLanguage(it.language)
                                    .setSelectionFlags(flags)
                                    .build()
                            }
                        }

                val mediaItem =
                    MediaItem
                        .Builder()
                        .setMediaId(itemId.toString())
                        .setUri(mediaUrl.toUri())
                        .setSubtitleConfigurations(listOfNotNull(externalSubtitle))
                        .build()

                val playback =
                    CurrentPlayback(
                        listOf(),
                        playMethod = transcodeType,
                        playSessionId = response.playSessionId,
                        liveStreamId = source.liveStreamId,
                    )
                val itemPlayback =
                    currentItemPlayback.copy(
                        sourceId = source.id?.toUUIDOrNull(),
                        audioIndex = audioIndex ?: TrackIndex.UNSPECIFIED,
                        subtitleIndex = subtitleIndex ?: TrackIndex.DISABLED,
                    )
                if (userInitiated) {
                    viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
                        Timber.v("Saving user initiated item playback: %s", itemPlayback)
                        val updated = itemPlaybackRepository.saveItemPlayback(itemPlayback)
                        withContext(Dispatchers.Main) {
                            this@PlaybackViewModel.currentItemPlayback.value = updated
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    // TODO, don't need to release & recreate when switching streams
                    this@PlaybackViewModel.activityListener?.let {
                        it.release()
                        player.removeListener(it)
                    }

                    val activityListener =
                        TrackActivityPlaybackListener(
                            api = api,
                            player = player,
                            playback = playback,
                            itemPlayback = itemPlayback,
                        )
                    player.addListener(activityListener)
                    this@PlaybackViewModel.activityListener = activityListener

                    duration.value = source.runTimeTicks?.ticks
                    loading.value = LoadingState.Success
                    currentPlayback.value = playback
                    this@PlaybackViewModel.currentItemPlayback.value = itemPlayback
                    player.setMediaItem(
                        mediaItem,
                        positionMs,
                    )
                    if (audioIndex != null || subtitleIndex != null) {
                        val onTracksChangedListener =
                            object : Player.Listener {
                                override fun onTracksChanged(tracks: Tracks) {
                                    Timber.v("onTracksChanged: $tracks")
                                    if (tracks.groups.isNotEmpty()) {
                                        applyTrackSelections(
                                            player,
                                            source,
                                            audioIndex,
                                            subtitleIndex,
                                            externalSubtitleCount,
                                        )
                                        player.removeListener(this)
                                    }
                                }
                            }
                        player.addListener(onTracksChangedListener)
                    }
                }
                val trickPlayInfo =
                    item.trickplay
                        ?.get(source.id)
                        ?.values
                        ?.firstOrNull()
//                Timber.v("Trickplay info: $trickPlayInfo")
                withContext(Dispatchers.Main) {
                    trickplay.value = trickPlayInfo
                }
            }
        }

        fun changeAudioStream(index: Int) {
            viewModelScope.launch(ExceptionHandler()) {
                changeStreams(
                    dto,
                    currentItemPlayback.value!!,
                    index,
                    currentItemPlayback.value?.subtitleIndex,
                    player.currentPosition,
                    true,
                )
            }
        }

        fun changeSubtitleStream(index: Int?): Job =
            viewModelScope.launch(ExceptionHandler()) {
                changeStreams(
                    dto,
                    currentItemPlayback.value!!,
                    currentItemPlayback.value?.audioIndex,
                    index,
                    player.currentPosition,
                    true,
                )
            }

        fun getTrickplayUrl(index: Int): String? {
            val itemId = dto.id
            val mediaSourceId = currentItemPlayback.value?.sourceId
            val trickPlayInfo = trickplay.value ?: return null
            return api.trickplayApi.getTrickplayTileImageUrl(
                itemId,
                trickPlayInfo.width,
                index,
                mediaSourceId,
            )
        }

        private fun maybeSetupPlaylistListener() {
            playlist.value?.let { playlist ->
                if (playlist.hasNext()) {
                    Timber.v("Adding lister for playlist with ${playlist.items.size} items")
                    val listener =
                        object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_ENDED) {
                                    viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
                                        val nextItem = playlist.peek()
                                        Timber.v("Setting next up to ${nextItem?.id}")
                                        withContext(Dispatchers.Main) {
                                            nextUp.value = nextItem
                                        }
                                    }
                                }
                            }
                        }
                    player.addListener(listener)
                    addCloseable { player.removeListener(listener) }
                }
            }
        }

        private var segmentJob: Job? = null

        private fun listenForSegments() {
            segmentJob?.cancel()
            segmentJob =
                viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                    val prefs = preferences.appPreferences.playbackPreferences
                    val segments by api.mediaSegmentsApi.getItemSegments(itemId)
                    if (segments.items.isNotEmpty()) {
                        while (isActive) {
                            delay(500L)
                            val currentTicks =
                                withContext(Dispatchers.Main) { player.currentPosition.milliseconds.inWholeTicks }
                            val currentSegment =
                                segments.items
                                    .firstOrNull {
                                        it.type != MediaSegmentType.UNKNOWN && currentTicks >= it.startTicks && currentTicks < it.endTicks
                                    }
                            if (currentSegment != null) {
                                Timber.d(
                                    "Found media segment for %s: %s, %s",
                                    currentSegment.itemId,
                                    currentSegment.id,
                                    currentSegment.type,
                                )
                                val behavior =
                                    when (currentSegment.type) {
                                        MediaSegmentType.COMMERCIAL -> prefs.skipCommercials
                                        MediaSegmentType.PREVIEW -> prefs.skipPreviews
                                        MediaSegmentType.RECAP -> prefs.skipRecaps
                                        MediaSegmentType.OUTRO -> prefs.skipOutros
                                        MediaSegmentType.INTRO -> prefs.skipIntros
                                        MediaSegmentType.UNKNOWN -> SkipSegmentBehavior.IGNORE
                                    }
                                withContext(Dispatchers.Main) {
                                    when (behavior) {
                                        SkipSegmentBehavior.AUTO_SKIP -> {
                                            this@PlaybackViewModel.currentSegment.value = null
                                            player.seekTo(currentSegment.endTicks.ticks.inWholeMilliseconds + 1)
                                        }

                                        SkipSegmentBehavior.ASK_TO_SKIP -> {
                                            this@PlaybackViewModel.currentSegment.value = currentSegment
                                        }

                                        else -> {
                                            this@PlaybackViewModel.currentSegment.value = null
                                        }
                                    }
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    this@PlaybackViewModel.currentSegment.value = null
                                }
                            }
                        }
                    }
                }
        }

        private var lastInteractionDate: Date = Date()

        fun reportInteraction() {
//            Timber.v("reportInteraction")
            lastInteractionDate = Date()
        }

        fun shouldAutoPlayNextUp(): Boolean =
            preferences.appPreferences.playbackPreferences.let {
                it.autoPlayNext &&
                    if (it.passOutProtectionMs > 0) {
                        (Date().time - lastInteractionDate.time) < it.passOutProtectionMs
                    } else {
                        true
                    }
            }

        fun playUpNextUp() {
            playlist.value?.let {
                if (it.hasNext()) {
                    viewModelScope.launch(ExceptionHandler()) {
                        cancelUpNextEpisode()
                        val item = it.getAndAdvance()
                        val played = play(item.data, 0)
                        if (!played) {
                            playUpNextUp()
                        }
                    }
                }
            }
        }

        fun playPrevious() {
            playlist.value?.let {
                if (it.hasPrevious()) {
                    viewModelScope.launch(ExceptionHandler()) {
                        cancelUpNextEpisode()
                        val item = it.getPreviousAndReverse()
                        val played = play(item.data, 0)
                        if (!played) {
                            playPrevious()
                        }
                    }
                }
            }
        }

        fun cancelUpNextEpisode() {
            nextUp.value = null
        }

        fun playItemInPlaylist(item: BaseItem) {
            playlist.value?.let { playlist ->
                viewModelScope.launch(ExceptionHandler()) {
                    val toPlay = playlist.advanceTo(item.id)
                    if (toPlay != null) {
                        val played = play(toPlay.data, 0)
                        if (!played) {
                            playUpNextUp()
                        }
                    } else {
                        // TODO
                    }
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            currentPlayback.value =
                currentPlayback.value?.copy(
                    tracks = checkForSupport(tracks),
                )
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Playback error")
            viewModelScope.launch(Dispatchers.Main + ExceptionHandler()) {
                loading.value = LoadingState.Error("Error during playback", error)
            }
        }

        fun release() {
            activityListener?.release()
            player.release()
        }

        fun subscribe() {
            api.webSocket
                .subscribe<PlaystateMessage>()
                .onEach { message ->
                    message.data?.let {
                        when (it.command) {
                            PlaystateCommand.STOP -> {
                                release()
                                navigationManager.goBack()
                            }

                            PlaystateCommand.PAUSE -> player.pause()
                            PlaystateCommand.UNPAUSE -> player.play()
                            PlaystateCommand.NEXT_TRACK -> playUpNextUp()
                            PlaystateCommand.PREVIOUS_TRACK -> playPrevious()
                            PlaystateCommand.SEEK -> it.seekPositionTicks?.ticks?.let { player.seekTo(it.inWholeMilliseconds) }
                            PlaystateCommand.REWIND ->
                                player.seekBack(
                                    preferences.appPreferences.playbackPreferences.skipBackMs.milliseconds,
                                )

                            PlaystateCommand.FAST_FORWARD ->
                                player.seekForward(
                                    preferences.appPreferences.playbackPreferences.skipForwardMs.milliseconds,
                                )

                            PlaystateCommand.PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
                        }
                    }
                }.launchIn(viewModelScope)
        }

        val subtitleSearch = MutableLiveData<SubtitleSearch?>(null)

        fun searchForSubtitles(language: String = Locale.current.language) {
            subtitleSearch.value = SubtitleSearch.Searching
            viewModelScope.launchIO {
                try {
                    currentItemPlayback.value?.itemId?.let {
                        Timber.v("Searching for remote subtitles for %s", it)
                        val results by api.subtitleApi.searchRemoteSubtitles(
                            itemId = it,
                            language = language,
                        )
                        subtitleSearch.setValueOnMain(SubtitleSearch.Success(results))
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception while searching for subtitles")
                    subtitleSearch.setValueOnMain(SubtitleSearch.Error(null, ex))
                }
            }
        }

        fun downloadAndSwitchSubtitles(
            subtitleId: String?,
            wasPlaying: Boolean,
        ) {
            if (subtitleId == null) {
                subtitleSearch.value = SubtitleSearch.Error("Subtitle has no ID", null)
            } else {
                subtitleSearch.value = SubtitleSearch.Downloading
                viewModelScope.launchIO {
                    try {
                        currentItemPlayback.value?.let {
                            Timber.v(
                                "Downloading remote subtitles for itemId=%s, sourceId=%s: %s",
                                it.itemId,
                                it.sourceId,
                                subtitleId,
                            )
                            api.subtitleApi.downloadRemoteSubtitles(
                                itemId = it.sourceId ?: it.itemId,
                                subtitleId = subtitleId,
                            )

                            subtitleSearch.setValueOnMain(null)
                            changeSubtitleStream(0).join()
                            withContext(Dispatchers.Main) {
                                if (wasPlaying) {
                                    player.play()
                                }
                            }
                        }
                    } catch (ex: Exception) {
                        Timber.e(ex, "Exception while downloading subtitles: $subtitleId")
                        subtitleSearch.setValueOnMain(SubtitleSearch.Error(null, ex))
                    }
                }
            }
        }

        fun cancelSubtitleSearch() {
            subtitleSearch.value = null
        }
    }

data class CurrentPlayback(
    val tracks: List<TrackSupport>,
    val playMethod: PlayMethod,
    val playSessionId: String?,
    val liveStreamId: String?,
)

sealed interface SubtitleSearch {
    data object Searching : SubtitleSearch

    data object Downloading : SubtitleSearch

    data class Success(
        val options: List<RemoteSubtitleInfo>,
    ) : SubtitleSearch

    data class Error(
        val message: String?,
        val ex: Exception?,
    ) : SubtitleSearch
}

val Format.idAsInt: Int?
    @OptIn(UnstableApi::class)
    get() =
        id?.let {
            if (it.contains(":")) {
                it.split(":").last().toIntOrNull()
            } else {
                it.toIntOrNull()
            }
        }

@OptIn(UnstableApi::class)
private fun applyTrackSelections(
    player: Player,
    source: MediaSourceInfo,
    audioIndex: Int?,
    subtitleIndex: Int?,
    externalSubtitleCount: Int,
) {
    if (source.supportsDirectPlay) {
        if (subtitleIndex != null && subtitleIndex >= 0) {
            val chosenTrack =
                player.currentTracks.groups.firstOrNull { group ->
                    group.type == C.TRACK_TYPE_TEXT && group.isSupported &&
                        (0..<group.mediaTrackGroup.length)
                            .map {
                                group.getTrackFormat(it).idAsInt
                            }.contains(subtitleIndex + 1) // Indexes are 1 based
                }
            Timber.v("Chosen subtitle track: $chosenTrack")
            chosenTrack?.let {
                player.trackSelectionParameters =
                    player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(
                            TrackSelectionOverride(
                                chosenTrack.mediaTrackGroup,
                                0,
                            ),
                        ).build()
            }
        } else {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
        }
        if (audioIndex != null) {
            val chosenTrack =
                player.currentTracks.groups.firstOrNull { group ->
                    group.type == C.TRACK_TYPE_AUDIO && group.isSupported &&
                        (0..<group.mediaTrackGroup.length)
                            .map {
                                group.getTrackFormat(it).idAsInt
                            }.contains(audioIndex - externalSubtitleCount + 1) // Indexes are 1 based
                }
            Timber.v("Chosen audio track: $chosenTrack")
            chosenTrack?.let {
                player.trackSelectionParameters =
                    player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                        .setOverrideForType(
                            TrackSelectionOverride(
                                chosenTrack.mediaTrackGroup,
                                0,
                            ),
                        ).build()
            }
        }
    }
}
