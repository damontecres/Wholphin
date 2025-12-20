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
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import coil3.imageLoader
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.ShowNextUpWhen
import com.github.damontecres.wholphin.preferences.SkipSegmentBehavior
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.services.PlaylistCreationResult
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.services.RefreshRateService
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
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
import com.github.damontecres.wholphin.util.checkForSupport
import com.github.damontecres.wholphin.util.mpv.mpvDeviceProfile
import com.github.damontecres.wholphin.util.subtitleMimeTypes
import com.github.damontecres.wholphin.util.supportItemKinds
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.trickplayApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
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
import kotlin.time.Duration.Companion.seconds

/**
 * This [ViewModel] is responsible for playing media including moving through playlists (including next up episodes)
 */
@HiltViewModel
@OptIn(markerClass = [UnstableApi::class])
class PlaybackViewModel
    @Inject
    constructor(
        @param:ApplicationContext internal val context: Context,
        internal val api: ApiClient,
        val navigationManager: NavigationManager,
        private val playlistCreator: PlaylistCreator,
        private val itemPlaybackDao: ItemPlaybackDao,
        private val serverRepository: ServerRepository,
        private val itemPlaybackRepository: ItemPlaybackRepository,
        private val playerFactory: PlayerFactory,
        private val datePlayedService: DatePlayedService,
        private val deviceInfo: DeviceInfo,
        private val deviceProfileService: DeviceProfileService,
        private val refreshRateService: RefreshRateService,
        val streamChoiceService: StreamChoiceService,
    ) : ViewModel(),
        Player.Listener,
        AnalyticsListener {
        val player by lazy {
            playerFactory.createVideoPlayer()
        }
        internal val mutex = Mutex()

        val controllerViewState =
            ControllerViewState(
                AppPreference.ControllerTimeout.defaultValue,
                true,
            )

        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)

        val currentMediaInfo = MutableLiveData<CurrentMediaInfo>(CurrentMediaInfo.EMPTY)
        val currentPlayback = MutableStateFlow<CurrentPlayback?>(null)
        val currentItemPlayback = MutableLiveData<ItemPlayback>()
        val currentSegment = EqualityMutableLiveData<MediaSegmentDto?>(null)
        private val autoSkippedSegments = mutableSetOf<UUID>()

        val subtitleCues = MutableLiveData<List<Cue>>(listOf())

        private lateinit var preferences: UserPreferences
        internal lateinit var itemId: UUID
        internal lateinit var item: BaseItem
        internal var forceTranscoding: Boolean = false
        private var activityListener: TrackActivityPlaybackListener? = null

        val nextUp = MutableLiveData<BaseItem?>()
        private var isPlaylist = false

        val playlist = MutableLiveData<Playlist>(Playlist(listOf()))
        val subtitleSearch = MutableLiveData<SubtitleSearch?>(null)
        val subtitleSearchLanguage = MutableLiveData<String>(Locale.current.language)

        init {
            viewModelScope.launch(ExceptionHandler()) { controllerViewState.observe() }
            player.addListener(this)
            (player as? ExoPlayer)?.addAnalyticsListener(this)
            addCloseable { player.removeListener(this@PlaybackViewModel) }
            addCloseable { (player as? ExoPlayer)?.removeAnalyticsListener(this@PlaybackViewModel) }
            addCloseable {
                this@PlaybackViewModel.activityListener?.let {
                    it.release()
                    player.removeListener(it)
                }
            }
            addCloseable { player.release() }
            subscribe()
            listenForTranscodeReason()
        }

        /**
         * Initialize from the UI to start playback
         */
        fun init(
            destination: Destination,
            preferences: UserPreferences,
        ) {
            nextUp.value = null
            this.preferences = preferences
            if (preferences.appPreferences.playbackPreferences.refreshRateSwitching) {
                addCloseable { refreshRateService.resetRefreshRate() }
            }
            controllerViewState.hideMilliseconds =
                preferences.appPreferences.playbackPreferences.controllerTimeoutMs
            this.forceTranscoding =
                (destination as? Destination.Playback)?.forceTranscoding ?: false
            val positionMs: Long
            val itemPlayback: ItemPlayback?
            val forceTranscoding: Boolean

            val itemId =
                when (val d = destination) {
                    is Destination.Playback -> {
                        positionMs = d.positionMs
                        itemPlayback = d.itemPlayback
                        forceTranscoding = d.forceTranscoding
                        d.itemId
                    }

                    is Destination.PlaybackList -> {
                        positionMs = 0
                        itemPlayback = null
                        forceTranscoding = false
                        d.itemId
                    }

                    else -> {
                        throw IllegalArgumentException("Destination not supported: $destination")
                    }
                }
            this.itemId = itemId
            viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loading,
                        "Error preparing for playback for $itemId",
                    ),
            ) {
                val destItem = (destination as? Destination.Playback)?.item?.data
                val queriedItem =
                    if (destItem?.mediaSources != null) {
                        destItem
                    } else {
                        api.userLibraryApi.getItem(itemId).content
                    }
                val base =
                    if (queriedItem.type.playable) {
                        queriedItem
                    } else if (destination is Destination.PlaybackList) {
                        isPlaylist = true
                        val playlistResult =
                            playlistCreator.createFrom(
                                item = queriedItem,
                                startIndex = destination.startIndex ?: 0,
                                sortAndDirection = destination.sortAndDirection,
                                shuffled = destination.shuffle,
                                recursive = destination.recursive,
                                filter = destination.filter,
                            )
                        when (val r = playlistResult) {
                            is PlaylistCreationResult.Error -> {
                                loading.setValueOnMain(LoadingState.Error(r.message, r.ex))
                                return@launch
                            }

                            is PlaylistCreationResult.Success -> {
                                if (r.playlist.items.isEmpty()) {
                                    showToast(context, "Playlist is empty", Toast.LENGTH_SHORT)
                                    navigationManager.goBack()
                                    return@launch
                                }
                                withContext(Dispatchers.Main) {
                                    this@PlaybackViewModel.playlist.value = r.playlist
                                }
                                r.playlist.items
                                    .first()
                                    .data
                            }
                        }
                    } else {
                        throw IllegalArgumentException("Item is not playable and not PlaybackList: ${queriedItem.type}")
                    }
                val item = BaseItem.from(base, api)

                val played =
                    play(
                        item,
                        positionMs,
                        itemPlayback,
                        forceTranscoding,
                    )
                if (!played) {
                    playNextUp()
                }

                if (!isPlaylist) {
                    val result = playlistCreator.createFrom(queriedItem)
                    if (result is PlaylistCreationResult.Success && result.playlist.items.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            this@PlaybackViewModel.playlist.value = result.playlist
                        }
                    }
                }
            }
        }

        /**
         * Play an item
         *
         * @param item the item to play
         * @param positionMs the starting playback position in milliseconds
         * @param itemPlayback the parameters for playback such chosen subtitle or audio streams
         * @param forceTranscoding whether the user has requested to force playback via transcoding
         */
        private suspend fun play(
            item: BaseItem,
            positionMs: Long,
            itemPlayback: ItemPlayback? = null,
            forceTranscoding: Boolean = this.forceTranscoding,
        ): Boolean =
            withContext(Dispatchers.IO) {
                Timber.i("Playing ${item.id}")

                // Starting playback, so want to invalidate the last played timestamp for this item
                datePlayedService.invalidate(item)
                // New item, so we can clear the media segment tracker & subtitle cues
                autoSkippedSegments.clear()
                this@PlaybackViewModel.subtitleCues.setValueOnMain(listOf())

                if (item.type !in supportItemKinds) {
                    showToast(
                        context,
                        "Unsupported type '${item.type}', skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }
                this@PlaybackViewModel.item = item
                this@PlaybackViewModel.itemId = item.id

                val isLiveTv = item.type == BaseItemKind.TV_CHANNEL
                val base = item.data

                // Use the provided playback parameters or else check if the database has some
                val playbackConfig =
                    itemPlayback
                        ?: serverRepository.currentUser.value?.let { user ->
                            itemPlaybackDao.getItem(user, base.id)?.let {
                                Timber.v("Fetched itemPlayback from DB: %s", it)
                                if (it.sourceId != null) {
                                    it
                                } else {
                                    null
                                }
                            }
                        }
                val mediaSource = streamChoiceService.chooseSource(base, playbackConfig)
                val plc = streamChoiceService.getPlaybackLanguageChoice(base)

                if (mediaSource == null) {
                    showToast(
                        context,
                        "Item has no media sources, skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }

                val subtitleStreams =
                    mediaSource.mediaStreams
                        ?.filter { it.type == MediaStreamType.SUBTITLE }
                        ?.map(SubtitleStream::from)
                        .orEmpty()
                val audioStreams =
                    mediaSource.mediaStreams
                        ?.filter { it.type == MediaStreamType.AUDIO }
                        ?.map(AudioStream::from)
                        ?.sortedWith(compareBy<AudioStream> { it.language }.thenByDescending { it.channels })
                        .orEmpty()

                val audioStream =
                    streamChoiceService
                        .chooseAudioStream(
                            source = mediaSource,
                            seriesId = base.seriesId,
                            itemPlayback = playbackConfig,
                            plc = plc,
                            prefs = preferences,
                        )
                val audioIndex = audioStream?.index

                val subtitleIndex =
                    streamChoiceService
                        .chooseSubtitleStream(
                            source = mediaSource,
                            audioStream = audioStream,
                            seriesId = base.seriesId,
                            itemPlayback = playbackConfig,
                            plc = plc,
                            prefs = preferences,
                        )?.index

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
                val trickPlayInfo =
                    item.data.trickplay
                        ?.get(mediaSource.id)
                        ?.values
                        ?.firstOrNull()
                trickPlayInfo?.let { trickplayInfo ->
                    mediaSource.runTimeTicks?.ticks?.let { duration ->
                        viewModelScope.launchIO {
                            prefetchTrickplay(
                                duration,
                                trickplayInfo,
                                mediaSource.id?.toUUIDOrNull(),
                            )
                        }
                    }
                }

                val chapters = Chapter.fromDto(base, api)
                withContext(Dispatchers.Main) {
                    this@PlaybackViewModel.currentItemPlayback.value = itemPlaybackToUse
                    updateCurrentMedia {
                        CurrentMediaInfo(
                            audioStreams = audioStreams,
                            subtitleStreams = subtitleStreams,
                            chapters = chapters,
                            trickPlayInfo = trickPlayInfo,
                        )
                    }

                    changeStreams(
                        item,
                        itemPlaybackToUse,
                        audioIndex,
                        subtitleIndex,
                        if (positionMs > 0) positionMs else C.TIME_UNSET,
                        itemPlayback != null, // If it was passed in, then it was not queried from the database
                        enableDirectPlay = !forceTranscoding,
                        enableDirectStream = !forceTranscoding,
                    )
                    player.prepare()
                    player.play()
                }
                listenForSegments()
                return@withContext true
            }

        /**
         * Change which streams (ie audio or subtitle) are active
         */
        @OptIn(UnstableApi::class)
        internal suspend fun changeStreams(
            item: BaseItem,
            currentItemPlayback: ItemPlayback = this@PlaybackViewModel.currentItemPlayback.value!!,
            audioIndex: Int?,
            subtitleIndex: Int?,
            positionMs: Long = 0,
            userInitiated: Boolean,
            enableDirectPlay: Boolean = !this.forceTranscoding,
            enableDirectStream: Boolean = !this.forceTranscoding,
        ) = withContext(Dispatchers.IO) {
            val itemId = item.id
            val playerBackend = preferences.appPreferences.playbackPreferences.playerBackend

            val currentPlayback = this@PlaybackViewModel.currentPlayback.value
            if (currentPlayback != null && currentPlayback.item.id == item.id && currentPlayback.playMethod == PlayMethod.DIRECT_PLAY) {
                // If direct playing, can try to switch tracks without playback restarting
                // Except for external subtitles
                // TODO there's probably no reason why we can't add external subtitles?
                Timber.v("changeStreams direct play")

                val source = currentPlayback.mediaSourceInfo
                val externalSubtitle = source.findExternalSubtitle(subtitleIndex)

                if (externalSubtitle == null) {
                    val result =
                        withContext(Dispatchers.Main) {
                            TrackSelectionUtils.applyTrackSelections(
                                player,
                                playerBackend,
                                true,
                                audioIndex,
                                subtitleIndex,
                                source,
                            )
                        }
                    if (result.bothSelected) {
                        // TODO lots of duplicate code in this block
                        Timber.d("Changes tracks audio=$audioIndex, subtitle=$subtitleIndex")
                        val itemPlayback =
                            currentItemPlayback.copy(
                                sourceId = source.id?.toUUIDOrNull(),
                                audioIndex = audioIndex ?: TrackIndex.UNSPECIFIED,
                                subtitleIndex = subtitleIndex ?: TrackIndex.DISABLED,
                            )
                        if (userInitiated) {
                            viewModelScope.launchIO {
                                Timber.v("Saving user initiated item playback: %s", itemPlayback)
                                val updated = itemPlaybackRepository.saveItemPlayback(itemPlayback)
                                withContext(Dispatchers.Main) {
                                    this@PlaybackViewModel.currentItemPlayback.value = updated
                                }
                            }
                        }
                        withContext(Dispatchers.Main) {
                            this@PlaybackViewModel.currentPlayback.update {
                                (it ?: currentPlayback).copy(
                                    tracks = checkForSupport(player.currentTracks),
                                )
                            }

                            this@PlaybackViewModel.currentItemPlayback.value = itemPlayback
                        }
                        loadSubtitleDelay()
                        return@withContext
                    }
                } else {
                    Timber.v("changeStreams direct play, external subtitle was requested")
                }
            }

            Timber.d(
                "changeStreams: userInitiated=$userInitiated, audioIndex=$audioIndex, subtitleIndex=$subtitleIndex, " +
                    "enableDirectPlay=$enableDirectPlay, enableDirectStream=$enableDirectStream, positionMs=$positionMs",
            )

            val maxBitrate =
                preferences.appPreferences.playbackPreferences.maxBitrate
                    .takeIf { it > 0 } ?: AppPreference.DEFAULT_BITRATE
            val response by
                api.mediaInfoApi
                    .getPostedPlaybackInfo(
                        itemId,
                        PlaybackInfoDto(
                            startTimeTicks = null,
                            deviceProfile =
                                if (playerBackend == PlayerBackend.EXO_PLAYER) {
                                    deviceProfileService.getOrCreateDeviceProfile(
                                        preferences.appPreferences.playbackPreferences,
                                        serverRepository.currentServer.value?.serverVersion,
                                    )
                                } else {
                                    mpvDeviceProfile
                                },
                            maxAudioChannels = null,
                            audioStreamIndex = audioIndex,
                            subtitleStreamIndex = subtitleIndex,
                            mediaSourceId = currentItemPlayback.sourceId?.toServerString(),
                            alwaysBurnInSubtitleWhenTranscoding = false,
                            maxStreamingBitrate = maxBitrate.toInt(),
                            enableDirectPlay = enableDirectPlay,
                            enableDirectStream = enableDirectStream,
                            allowVideoStreamCopy = enableDirectStream,
                            allowAudioStreamCopy = enableDirectStream,
                            enableTranscoding = true,
                            autoOpenLiveStream = true,
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
                        source.transcodingUrl?.let(api::createUrl)
                    } else {
                        source.transcodingUrl?.let(api::createUrl)
                    }
                if (mediaUrl.isNullOrBlank()) {
                    loading.setValueOnMain(
                        LoadingState.Error("Unable to get media URL from the server. Do you have permission to view and/or transcode?"),
                    )
                    return@withContext
                }
                val transcodeType =
                    when {
//                        playerBackend == PlayerBackend.MPV -> PlayMethod.DIRECT_PLAY
                        source.supportsDirectPlay -> PlayMethod.DIRECT_PLAY

                        source.supportsDirectStream -> PlayMethod.DIRECT_STREAM

                        source.supportsTranscoding -> PlayMethod.TRANSCODE

                        else -> throw Exception("No supported playback method")
                    }
                Timber.i("Playback decision for $itemId: $transcodeType")

                val externalSubtitleCount = source.externalSubtitlesCount

                val externalSubtitle =
                    source.findExternalSubtitle(subtitleIndex)?.let {
                        it.deliveryUrl?.let { deliveryUrl ->
                            var flags = 0
                            if (it.isForced) flags = flags.or(C.SELECTION_FLAG_FORCED)
                            if (it.isDefault) flags = flags.or(C.SELECTION_FLAG_DEFAULT)
                            MediaItem.SubtitleConfiguration
                                .Builder(
                                    api.createUrl(deliveryUrl).toUri(),
                                ).setId("e:${it.index}")
                                .setMimeType(subtitleMimeTypes[it.codec])
                                .setLanguage(it.language)
                                .setLabel(it.title)
                                .setSelectionFlags(flags)
                                .build()
                        }
                    }

                Timber.v("subtitleIndex=$subtitleIndex, externalSubtitleCount=$externalSubtitleCount, externalSubtitle=$externalSubtitle")

                val mediaItem =
                    MediaItem
                        .Builder()
                        .setMediaId(itemId.toString())
                        .setUri(mediaUrl.toUri())
                        .setSubtitleConfigurations(listOfNotNull(externalSubtitle))
                        .build()

                val playback =
                    CurrentPlayback(
                        item = item,
                        tracks = listOf(),
                        backend = preferences.appPreferences.playbackPreferences.playerBackend,
                        playMethod = transcodeType,
                        playSessionId = response.playSessionId,
                        liveStreamId = source.liveStreamId,
                        mediaSourceInfo = source,
                    )

                if (preferences.appPreferences.playbackPreferences.refreshRateSwitching) {
                    source.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }?.let {
                        refreshRateService.changeRefreshRate(it)
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
                            itemPlayback = currentItemPlayback,
                        )
                    player.addListener(activityListener)
                    this@PlaybackViewModel.activityListener = activityListener

                    loading.value = LoadingState.Success
                    this@PlaybackViewModel.currentPlayback.update { playback }
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
                                        val result =
                                            TrackSelectionUtils.applyTrackSelections(
                                                player,
                                                playerBackend,
                                                source.supportsDirectPlay,
                                                audioIndex.takeIf { transcodeType == PlayMethod.DIRECT_PLAY },
                                                subtitleIndex,
                                                source,
                                            )
                                        if (result.bothSelected) {
                                            player.removeListener(this)
                                        }
                                        viewModelScope.launchIO { loadSubtitleDelay() }
                                    }
                                }
                            }
                        player.addListener(onTracksChangedListener)
                    }
                }
            }
        }

        fun changeAudioStream(index: Int) {
            viewModelScope.launchIO {
                Timber.d("Changing audio track to %s", index)
                val itemPlayback =
                    itemPlaybackRepository.saveTrackSelection(
                        item = item,
                        itemPlayback = currentItemPlayback.value!!,
                        trackIndex = index,
                        type = MediaStreamType.AUDIO,
                    )
                this@PlaybackViewModel.currentItemPlayback.setValueOnMain(itemPlayback)
                changeStreams(
                    item,
                    itemPlayback,
                    index,
                    itemPlayback.subtitleIndex,
                    onMain { player.currentPosition },
                    true,
                )
            }
        }

        fun changeSubtitleStream(index: Int): Job =
            viewModelScope.launchIO {
                Timber.d("Changing subtitle track to %s", index)
                val itemPlayback =
                    itemPlaybackRepository.saveTrackSelection(
                        item = item,
                        itemPlayback = currentItemPlayback.value!!,
                        trackIndex = index,
                        type = MediaStreamType.SUBTITLE,
                    )
                this@PlaybackViewModel.currentItemPlayback.setValueOnMain(itemPlayback)
                changeStreams(
                    item,
                    itemPlayback,
                    itemPlayback.audioIndex,
                    index,
                    onMain { player.currentPosition },
                    true,
                )
            }

        private suspend fun prefetchTrickplay(
            duration: Duration,
            trickplayInfo: TrickplayInfo,
            mediaSourceId: UUID?,
        ) {
            val tilesPerImage = trickplayInfo.tileWidth * trickplayInfo.tileHeight
            val totalCount =
                (duration.inWholeMilliseconds / trickplayInfo.interval).toInt() / tilesPerImage + 1
            (0..<totalCount).forEach {
                val url = getTrickplayUrl(it, trickplayInfo, mediaSourceId)
                context.imageLoader.enqueue(
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(coil3.size.Size.ORIGINAL)
                        .build(),
                )
            }
        }

        fun getTrickplayUrl(
            index: Int,
            trickPlayInfo: TrickplayInfo? = currentMediaInfo.value?.trickPlayInfo,
            mediaSourceId: UUID? = currentItemPlayback.value?.sourceId,
        ): String? =
            trickPlayInfo?.let {
                val itemId = item.id
                return api.trickplayApi.getTrickplayTileImageUrl(
                    itemId,
                    trickPlayInfo.width,
                    index,
                    mediaSourceId,
                )
            }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                viewModelScope.launchIO {
                    val nextItem = playlist.value?.peek()
                    Timber.v("Setting next up to ${nextItem?.id}")
                    withContext(Dispatchers.Main) {
                        nextUp.value = nextItem
                        if (nextItem == null) {
                            navigationManager.goBack()
                        }
                    }
                }
            }
        }

        private var segmentJob: Job? = null

        /**
         * This sets up a coroutine to periodically check whether the current playback progress is within a media segment (intro, outro, etc)
         */
        private fun listenForSegments() {
            segmentJob?.cancel()
            segmentJob =
                viewModelScope.launchIO {
                    val prefs = preferences.appPreferences.playbackPreferences
                    val segments by api.mediaSegmentsApi.getItemSegments(itemId)
                    if (segments.items.isNotEmpty()) {
                        while (isActive) {
                            delay(500L)
                            val currentTicks =
                                onMain { player.currentPosition.milliseconds.inWholeTicks }
                            val currentSegment =
                                segments.items
                                    .firstOrNull {
                                        it.type != MediaSegmentType.UNKNOWN && currentTicks >= it.startTicks && currentTicks < it.endTicks
                                    }
                            if (currentSegment != null && autoSkippedSegments.add(currentSegment.id)) {
                                Timber.d(
                                    "Found media segment for %s: %s, %s",
                                    currentSegment.itemId,
                                    currentSegment.id,
                                    currentSegment.type,
                                )
                                val playlist = this@PlaybackViewModel.playlist.value

                                if (currentSegment.type == MediaSegmentType.OUTRO &&
                                    prefs.showNextUpWhen == ShowNextUpWhen.DURING_CREDITS &&
                                    playlist != null && playlist.hasNext()
                                ) {
                                    val nextItem = playlist.peek()
                                    Timber.v("Setting next up during outro to ${nextItem?.id}")
                                    withContext(Dispatchers.Main) {
                                        nextUp.value = nextItem
                                    }
                                } else {
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
                                                this@PlaybackViewModel.currentSegment.value =
                                                    currentSegment
                                            }

                                            else -> {
                                                this@PlaybackViewModel.currentSegment.value = null
                                            }
                                        }
                                    }
                                }
                            } else if (currentSegment == null) {
                                withContext(Dispatchers.Main) {
                                    this@PlaybackViewModel.currentSegment.value = null
                                }
                            }
                        }
                    }
                }
        }

        private fun listenForTranscodeReason() {
            viewModelScope.launchIO {
                currentPlayback.collectLatest {
                    if (it != null) {
                        try {
                            var transcodeInfo = it.transcodeInfo
                            while (isActive && it.playMethod == PlayMethod.TRANSCODE && transcodeInfo == null) {
                                delay(2.seconds)
                                transcodeInfo =
                                    api.sessionApi
                                        .getSessions(deviceId = deviceInfo.id)
                                        .content
                                        .firstOrNull()
                                        ?.transcodingInfo
                                if (transcodeInfo == null) delay(3.seconds)
                            }
                            Timber.v("transcodeInfo=$transcodeInfo")
                            currentPlayback.update { current ->
                                current?.copy(transcodeInfo = transcodeInfo)
                            }
                        } catch (ex: Exception) {
                            if (ex !is CancellationException) {
                                Timber.w(ex, "Exception trying to get session info")
                                currentPlayback.update { current ->
                                    current?.copy(transcodeInfo = null)
                                }
                            }
                        }
                    }
                }
            }
        }

        private var lastInteractionDate: Date = Date()

        /**
         * Tracks interactions with the UI for passout protection
         */
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

        fun playNextUp() {
            playlist.value?.let {
                if (it.hasNext()) {
                    viewModelScope.launchIO {
                        cancelUpNextEpisode()
                        val item = it.getAndAdvance()
                        val played = play(item, 0)
                        if (!played) {
                            playNextUp()
                        }
                    }
                }
            }
        }

        fun playPrevious() {
            playlist.value?.let {
                if (it.hasPrevious()) {
                    viewModelScope.launchIO {
                        cancelUpNextEpisode()
                        val item = it.getPreviousAndReverse()
                        val played = play(item, 0)
                        if (!played) {
                            playPrevious()
                        }
                    }
                }
            }
        }

        suspend fun cancelUpNextEpisode() {
            nextUp.setValueOnMain(null)
        }

        fun playItemInPlaylist(item: BaseItem) {
            playlist.value?.let { playlist ->
                viewModelScope.launchIO {
                    val toPlay = playlist.advanceTo(item.id)
                    if (toPlay != null) {
                        val played = play(toPlay, 0)
                        if (!played) {
                            playNextUp()
                        }
                    } else {
                        // TODO
                    }
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            currentPlayback.update {
                it?.copy(
                    tracks = checkForSupport(tracks),
                )
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            subtitleCues.value = cueGroup.cues
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Playback error")
            viewModelScope.launch(Dispatchers.Main + ExceptionHandler()) {
                currentPlayback.value?.let {
                    when (it.playMethod) {
                        PlayMethod.TRANSCODE -> {
                            loading.setValueOnMain(
                                LoadingState.Error(
                                    "Error during playback",
                                    error,
                                ),
                            )
                        }

                        PlayMethod.DIRECT_STREAM, PlayMethod.DIRECT_PLAY -> {
                            Timber.w("Playback error during ${it.playMethod}, falling back to transcoding")
                            changeStreams(
                                item,
                                currentItemPlayback.value!!,
                                currentItemPlayback.value?.audioIndex,
                                currentItemPlayback.value?.subtitleIndex,
                                player.currentPosition,
                                false,
                                enableDirectPlay = false,
                                enableDirectStream = false,
                            )
                            withContext(Dispatchers.Main) {
                                player.prepare()
                                player.play()
                            }
                        }
                    }
                }
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
                        withContext(Dispatchers.Main) {
                            when (it.command) {
                                PlaystateCommand.STOP -> {
                                    release()
                                    navigationManager.goBack()
                                }

                                PlaystateCommand.PAUSE -> {
                                    player.pause()
                                }

                                PlaystateCommand.UNPAUSE -> {
                                    player.play()
                                }

                                PlaystateCommand.NEXT_TRACK -> {
                                    playNextUp()
                                }

                                PlaystateCommand.PREVIOUS_TRACK -> {
                                    playPrevious()
                                }

                                PlaystateCommand.SEEK -> {
                                    it.seekPositionTicks?.ticks?.let {
                                        player.seekTo(
                                            it.inWholeMilliseconds,
                                        )
                                    }
                                }

                                PlaystateCommand.REWIND -> {
                                    player.seekBack(
                                        preferences.appPreferences.playbackPreferences.skipBackMs.milliseconds,
                                    )
                                }

                                PlaystateCommand.FAST_FORWARD -> {
                                    player.seekForward(
                                        preferences.appPreferences.playbackPreferences.skipForwardMs.milliseconds,
                                    )
                                }

                                PlaystateCommand.PLAY_PAUSE -> {
                                    if (player.isPlaying) player.pause() else player.play()
                                }
                            }
                        }
                    }
                }.launchIn(viewModelScope)
        }

        /**
         * Atomically update [currentMediaInfo]
         */
        internal suspend fun updateCurrentMedia(block: (CurrentMediaInfo) -> CurrentMediaInfo) =
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val newMediaInfo = block.invoke(currentMediaInfo.value!!)
                    currentMediaInfo.setValueOnMain(newMediaInfo)
                }
            }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Timber.v("onVideoDecoderInitialized: decoder=$decoderName")
            currentPlayback.update { it?.copy(videoDecoder = decoderName) }
        }

        override fun onVideoDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            Timber.d("onVideoDisabled")
            currentPlayback.update { it?.copy(videoDecoder = null) }
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            decoderReuseEvaluation?.let { decoder ->
                if (decoder.result != DecoderReuseEvaluation.REUSE_RESULT_NO) {
                    Timber.d("onVideoInputFormatChanged: decoder=${decoder.decoderName}")
                    currentPlayback.update { it?.copy(videoDecoder = decoder.decoderName) }
                }
            }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Timber.d("decoder: onAudioDecoderInitialized: decoder=$decoderName")
            currentPlayback.update { it?.copy(audioDecoder = decoderName) }
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            decoderReuseEvaluation?.let { decoder ->
                if (decoder.result != DecoderReuseEvaluation.REUSE_RESULT_NO) {
                    Timber.d("decoder: onAudioInputFormatChanged: decoder=${decoder.decoderName}")
                    currentPlayback.update { it?.copy(audioDecoder = decoder.decoderName) }
                }
            }
        }

        override fun onAudioDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            Timber.d("decoder: onAudioDisabled")
            currentPlayback.update { it?.copy(audioDecoder = null) }
        }

        private var subtitleDelaySaveJob: Job? = null

        fun updateSubtitleDelay(delta: Duration) {
            subtitleDelaySaveJob?.cancel()
            currentPlayback.update {
                it?.let {
                    val newDelay = it.subtitleDelay + delta
                    val result = it.copy(subtitleDelay = it.subtitleDelay + delta)
                    subtitleDelaySaveJob =
                        viewModelScope.launchIO {
                            // Debounce & save
                            currentItemPlayback.value?.let { item ->
                                delay(1500)
                                itemPlaybackRepository.saveTrackModifications(
                                    item.itemId,
                                    item.subtitleIndex,
                                    newDelay,
                                )
                            }
                        }
                    result
                }
            }
        }

        suspend fun loadSubtitleDelay() {
            currentItemPlayback.value?.let {
                if (it.subtitleIndexEnabled) {
                    val result =
                        itemPlaybackRepository.getTrackModifications(it.itemId, it.subtitleIndex)
                    if (result != null) {
                        Timber.v(
                            "Loading subtitle delay %s for track=%s, itemId=%s",
                            result.delayMs,
                            it.subtitleIndex,
                            it.itemId,
                        )
                        currentPlayback.update { it?.copy(subtitleDelay = result.delayMs.milliseconds) }
                    }
                }
            }
        }
    }
