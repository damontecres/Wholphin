package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.ui.unit.Density
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.subrip.SubripParser
import androidx.media3.session.MediaSession
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.ShowNextUpWhen
import com.github.damontecres.wholphin.preferences.SkipSegmentBehavior
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.services.PlaylistCreationResult
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.services.RefreshRateService
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.ui.formatBitrate
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings.applyToMpv
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.toServerString
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.PlaybackItemState
import com.github.damontecres.wholphin.util.TrackActivityPlaybackListener
import com.github.damontecres.wholphin.util.checkForSupport
import com.github.damontecres.wholphin.util.mpv.MpvPlayer
import com.github.damontecres.wholphin.util.mpv.mpvDeviceProfile
import com.github.damontecres.wholphin.util.profile.Codec
import com.github.damontecres.wholphin.util.subtitleMimeTypes
import com.github.damontecres.wholphin.util.supportItemKinds
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
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
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.TrickplayInfo
import org.jellyfin.sdk.model.api.VideoRange
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.Date
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * This [ViewModel] is responsible for playing media including moving through playlists (including next up episodes)
 */
@HiltViewModel(assistedFactory = PlaybackViewModel.Factory::class)
@OptIn(markerClass = [UnstableApi::class])
class PlaybackViewModel
    @AssistedInject
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
        private val userPreferencesService: UserPreferencesService,
        private val imageUrlService: ImageUrlService,
        private val screensaverService: ScreensaverService,
        private val musicService: MusicService,
        @param:StandardOkHttpClient private val httpClient: OkHttpClient,
        @Assisted private val destination: Destination,
    ) : ViewModel(),
        Player.Listener,
        AnalyticsListener {
        @AssistedFactory
        interface Factory {
            fun create(destination: Destination): PlaybackViewModel
        }

        val currentPlayer = MutableStateFlow<PlayerInstance?>(null)

        internal lateinit var player: Player

        private var mediaSession: MediaSession? = null

        // Mutex & Job for changing playlist index
        private val playlistMutex = Mutex()
        private var playlistJob: Job? = null

        val controllerViewState =
            ControllerViewState(
                AppPreference.ControllerTimeout.defaultValue,
                true,
            )

        private val _state = MutableStateFlow(PlaybackState())
        val state: StateFlow<PlaybackState> = _state

        private lateinit var preferences: UserPreferences
        internal lateinit var itemId: UUID
        internal lateinit var currentItem: PlaylistItem
        internal var forceTranscoding: Boolean = false
        private var activityListener: TrackActivityPlaybackListener? = null
        private val jobs = mutableListOf<Job>()

        private val isPlaylist = destination is Destination.PlaybackList

        val subtitleSearchState = MutableStateFlow(SubtitleSearchState())

        val currentUserDto = serverRepository.currentUserDtoFlow

        init {
            viewModelScope.launchIO {
                addCloseable {
                    screensaverService.keepScreenOn(false)
                    disconnectPlayer()
                }
                init()
            }
        }

        private fun disconnectPlayer() {
            if (this@PlaybackViewModel::player.isInitialized) {
                player.removeListener(this@PlaybackViewModel)
                (player as? ExoPlayer)?.removeAnalyticsListener(this@PlaybackViewModel)

                this@PlaybackViewModel.activityListener?.let {
                    it.release()
                    player.removeListener(it)
                }
                player.release()
                mediaSession?.release()
            }
            jobs.forEach { it.cancel() }
        }

        private suspend fun createPlayer(
            isHdr: Boolean,
            is4k: Boolean,
        ) {
            val softwareDecoding =
                !preferences.appPreferences.playbackPreferences.mpvOptions.enableHardwareDecoding
            val requestBackend =
                (destination as? Destination.Playback)?.backend
                    ?: preferences.appPreferences.playbackPreferences.playerBackend
            val playerBackend =
                when (requestBackend) {
                    PlayerBackend.UNRECOGNIZED,
                    PlayerBackend.EXO_PLAYER,
                    -> PlayerBackend.EXO_PLAYER

                    PlayerBackend.MPV -> PlayerBackend.MPV

                    PlayerBackend.PREFER_MPV -> if (isHdr || (is4k && softwareDecoding)) PlayerBackend.EXO_PLAYER else PlayerBackend.MPV

                    PlayerBackend.EXTERNAL_PLAYER -> throw IllegalStateException("Cannot use this for external playback")
                }

            Timber.d("Selected backend: %s", playerBackend)
            if (currentPlayer.value?.backend != playerBackend) {
                Timber.i("Switching player backend to %s", playerBackend)
                withContext(Dispatchers.Main) {
                    disconnectPlayer()
                }

                val playerCreation =
                    playerFactory.createVideoPlayer(
                        playerBackend,
                        preferences.appPreferences.playbackPreferences,
                    )
                this.player = playerCreation.player
                currentPlayer.update {
                    PlayerInstance(playerCreation.player, playerBackend, playerCreation.assHandler)
                }
                configurePlayer()
            }
        }

        private fun configurePlayer() {
            player.addListener(this)
            (player as? ExoPlayer)?.addAnalyticsListener(this)
            jobs.add(subscribe())
            jobs.add(listenForTranscodeReason())
            val sessionPlayer =
                MediaSessionPlayer(
                    player,
                    preferences.appPreferences.playbackPreferences,
                )
            mediaSession =
                MediaSession
                    .Builder(context, sessionPlayer)
                    .build()
        }

        /**
         * Initialize from the UI to start playback
         */
        private suspend fun init() {
            musicService.stop()
            _state.update { it.copy(nextUp = null) }
            this.preferences = userPreferencesService.getCurrent()
            if (preferences.appPreferences.playbackPreferences.refreshRateSwitching) {
                addCloseable { refreshRateService.resetRefreshRate() }
            }
            controllerViewState.hideMilliseconds =
                preferences.appPreferences.playbackPreferences.controllerTimeoutMs
            this.forceTranscoding =
                (destination as? Destination.Playback)?.forceTranscoding ?: false
            val positionMs: Long
            val forceTranscoding: Boolean

            val itemId =
                when (val d = destination) {
                    is Destination.Playback -> {
                        positionMs = d.positionMs
                        forceTranscoding = d.forceTranscoding
                        d.itemId
                    }

                    is Destination.PlaybackList -> {
                        positionMs = 0
                        forceTranscoding = false
                        d.itemId
                    }

                    else -> {
                        throw IllegalArgumentException("Destination not supported: $destination")
                    }
                }
            this.itemId = itemId
            val queriedItem = api.userLibraryApi.getItem(itemId).content
            val playlistItem =
                if (queriedItem.type.playable) {
                    PlaylistItem.Media(BaseItem(queriedItem, false))
                } else if (destination is Destination.PlaybackList) {
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
                            _state.update { it.copy(loading = LoadingState.Error(r.message, r.ex)) }
                            return
                        }

                        is PlaylistCreationResult.Success -> {
                            if (r.playlist.items.isEmpty()) {
                                showToast(context, "Playlist is empty", Toast.LENGTH_SHORT)
                                navigationManager.goBack()
                                return
                            }
                            _state.update {
                                it.copy(playlist = r.playlist)
                            }
                            r.playlist.items.first()
                        }
                    }
                } else {
                    throw IllegalArgumentException("Item is not playable and not PlaybackList: ${queriedItem.type}")
                }

            viewModelScope.launch(ExceptionHandler()) { controllerViewState.observe() }

            val intros =
                // If not resuming playback & cinema mode is enabled, get potential intros
                if (positionMs == 0L && preferences.appPreferences.playbackPreferences.cinemaMode) {
                    api.userLibraryApi
                        .getIntros(
                            itemId = playlistItem.id,
                            userId = serverRepository.currentUser?.id,
                        ).content.items
                        .map {
                            PlaylistItem.Intro(BaseItem(it))
                        }
                } else {
                    emptyList()
                }
            val firstItem =
                if (intros.isNotEmpty()) {
                    Timber.v("Got %s intros", intros.size)
                    _state.update {
                        it.copy(playlist = Playlist(intros + it.playlist.items))
                    }
                    intros.first()
                } else {
                    playlistItem
                }

            val played =
                play(
                    firstItem,
                    positionMs,
                    forceTranscoding,
                )
            if (!played) {
                playNextUp()
            }

            if (!isPlaylist) {
                val result = playlistCreator.createFrom(queriedItem)
                if (result is PlaylistCreationResult.Success && result.playlist.items.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            playlist = Playlist(it.playlist.items + result.playlist.items),
                        )
                    }
                }
            }

            viewModelScope.launchDefault {
                while (isActive) {
                    delay(50)
                    if (state.value.externalCues.active) {
                        val delay = state.value.currentPlayback?.subtitleDelay ?: Duration.ZERO
                        val positionUs =
                            onMain { player.currentPosition } * 1_000 + delay.inWholeMicroseconds
                        val cues =
                            state.value.externalCues
                                .getCuesAt(positionUs)
                                .flatMap { it.cues }
                        _state.update { it.copy(subtitleCues = cues) }
                    }
                }
            }
        }

        private fun updateCurrentPlayback(block: (CurrentPlayback?) -> CurrentPlayback?) {
            _state.update {
                it.copy(currentPlayback = block.invoke(it.currentPlayback))
            }
        }

        /**
         * Play an item
         *
         * @param currentItem the item to play
         * @param positionMs the starting playback position in milliseconds
         * @param itemPlayback the parameters for playback such chosen subtitle or audio streams
         * @param forceTranscoding whether the user has requested to force playback via transcoding
         */
        private suspend fun play(
            playlistItem: PlaylistItem,
            positionMs: Long,
            forceTranscoding: Boolean = this.forceTranscoding,
        ): Boolean =
            withContext(Dispatchers.IO) {
                val item =
                    when (playlistItem) {
                        is PlaylistItem.Intro -> playlistItem.item
                        is PlaylistItem.Media -> playlistItem.item
                    }

                Timber.i("Playing ${item.id}")

                // New item, so we can clear the media segment tracker & subtitle cues
                resetSegmentState()
                _state.update { it.copy(subtitleCues = emptyList()) }

                viewModelScope.launchIO {
                    // Starting playback, so want to invalidate the last played timestamp for this item
                    datePlayedService.invalidate(item)
                }

                if (item.type !in supportItemKinds) {
                    showToast(
                        context,
                        "Unsupported type '${item.type}', skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }
                this@PlaybackViewModel.currentItem = playlistItem
                this@PlaybackViewModel.itemId = item.id

                val isLiveTv = item.type == BaseItemKind.TV_CHANNEL
                val base = item.data

                // Use the provided playback parameters or else check if the database has some
                val itemPlayback =
                    serverRepository.currentUser?.let { user ->
                        itemPlaybackDao.getItem(user, base.id)?.let {
                            Timber.v("Fetched itemPlayback from DB: %s", it)
                            if (it.sourceId != null) {
                                it
                            } else {
                                null
                            }
                        }
                    }
                val mediaSource = streamChoiceService.chooseSource(base, itemPlayback)
                val plc = streamChoiceService.getPlaybackLanguageChoice(base)

                if (mediaSource == null) {
                    showToast(
                        context,
                        "Item has no media sources, skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }

                val videoStream =
                    mediaSource.mediaStreams
                        ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                        ?.let {
                            val isHdr =
                                it.videoRange == VideoRange.HDR ||
                                    (it.videoRangeType != VideoRangeType.SDR && it.videoRangeType != VideoRangeType.UNKNOWN)
                            // Often times 4k movies have a wider aspect ratio so the height is lower even though the width is still 3840
                            val is4k = (it.width ?: 0) > 2560 || (it.height ?: 0) > 1440
                            SimpleVideoStream(it.index, isHdr, is4k)
                        }

                // Create the correct player for the media
                createPlayer(videoStream?.hdr == true, videoStream?.is4k == true)
                val subtitleLanguagePreference =
                    serverRepository.currentUserDto
                        ?.configuration
                        ?.subtitleLanguagePreference
                val subtitleStreams =
                    mediaSource.mediaStreams
                        ?.filter { it.type == MediaStreamType.SUBTITLE }
                        .let {
                            if (subtitleLanguagePreference.isNotNullOrBlank()) {
                                it?.sortedByDescending { it.language != null && subtitleLanguagePreference == it.language }
                            } else {
                                it
                            }
                        }?.map {
                            // TODO should use a string provider instead
                            SimpleMediaStream.from(context.resources, it, true)
                        }.orEmpty()

                val audioStreams =
                    mediaSource.mediaStreams
                        ?.filter { it.type == MediaStreamType.AUDIO }
                        ?.map {
                            SimpleMediaStream.from(context.resources, it, true)
                        }
//                        ?.sortedWith(compareBy<AudioStream> { it.language }.thenByDescending { it.channels })
                        .orEmpty()
                val audioStream =
                    streamChoiceService
                        .chooseAudioStream(
                            source = mediaSource,
                            seriesId = base.seriesId,
                            itemPlayback = itemPlayback,
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
                            itemPlayback = itemPlayback,
                            plc = plc,
                            prefs = preferences,
                        )?.index

                Timber.d("Selected mediaSource=${mediaSource.id}, audioIndex=$audioIndex, subtitleIndex=$subtitleIndex")

                val itemPlaybackToUse =
                    itemPlayback ?: ItemPlayback(
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
                _state.update {
                    it.copy(currentItemPlayback = itemPlaybackToUse)
                }
                updateCurrentMedia {
                    CurrentMediaInfo(
                        sourceId = mediaSource.id,
                        videoStream = videoStream,
                        audioStreams = audioStreams,
                        subtitleStreams = subtitleStreams,
                        chapters = chapters,
                        trickPlayInfo = trickPlayInfo,
                    )
                }
                withContext(Dispatchers.Main) {
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
                listenForSegments(item.id)
                return@withContext true
            }

        /**
         * Change which streams (ie audio or subtitle) are active
         */
        @OptIn(UnstableApi::class)
        internal suspend fun changeStreams(
            item: BaseItem,
            currentItemPlayback: ItemPlayback = state.value.currentItemPlayback!!,
            audioIndex: Int?,
            subtitleIndex: Int?,
            positionMs: Long = 0,
            userInitiated: Boolean,
            enableDirectPlay: Boolean = !this.forceTranscoding,
            enableDirectStream: Boolean = !this.forceTranscoding,
        ) = withContext(Dispatchers.IO) {
            _state.update { it.copy(externalCues = ExternalCues(active = false)) }
            val itemId = item.id

            val currentPlayback = state.value.currentPlayback
            if (currentPlayback != null && currentPlayback.item.id == item.id && currentPlayback.playMethod == PlayMethod.DIRECT_PLAY) {
                val wasSuccessful =
                    changeStreamsDirectPlay(
                        currentPlayback = currentPlayback,
                        currentItemPlayback = currentItemPlayback,
                        audioIndex = audioIndex,
                        subtitleIndex = subtitleIndex,
                        userInitiated = userInitiated,
                    )
                if (wasSuccessful) return@withContext
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
                                if (currentPlayer.value!!.backend == PlayerBackend.EXO_PLAYER) {
                                    deviceProfileService.getOrCreateDeviceProfile(
                                        preferences.appPreferences.playbackPreferences,
                                        serverRepository.currentServer?.serverVersion,
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
                _state.update { it.copy(loading = LoadingState.Error(response.errorCode?.serialName)) }
                return@withContext
            }
            val source = response.mediaSources.firstOrNull()
            source?.let { source ->
                val mediaUrl =
                    if (source.supportsDirectPlay) {
                        if (source.isRemote && source.path.isNotNullOrBlank()) {
                            Timber.i("Playback is remote for source: %s", source.id)
                            source.path
                        } else {
                            api.videosApi.getVideoStreamUrl(
                                itemId = itemId,
                                mediaSourceId = source.id,
                                static = true,
                                tag = source.eTag,
                                playSessionId = response.playSessionId,
                            )
                        }
                    } else if (source.supportsDirectStream) {
                        source.transcodingUrl?.let(api::createUrl)
                    } else {
                        source.transcodingUrl?.let(api::createUrl)
                    }
                if (mediaUrl.isNullOrBlank()) {
                    _state.update {
                        it.copy(
                            loading =
                                LoadingState.Error(
                                    "Unable to get media URL from the server. Do you have permission to view and/or transcode?",
                                ),
                        )
                    }
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

                val externalSubtitle = source.findExternalSubtitle(subtitleIndex)
                val isSubtitleHandledInternally =
                    subtitleIndex != null &&
                        player is ExoPlayer &&
                        externalSubtitle?.deliveryUrl != null &&
                        (externalSubtitle.codec == Codec.Subtitle.SRT || externalSubtitle.codec == Codec.Subtitle.SUBRIP)
                val subtitleConfig =
                    externalSubtitle?.let { stream ->
                        stream.deliveryUrl?.let { deliveryUrl ->
                            if (stream.codec == Codec.Subtitle.SRT || stream.codec == Codec.Subtitle.SUBRIP) {
                                Timber.i("Using built-in srt parser")
                                val request =
                                    okhttp3.Request
                                        .Builder()
                                        .url(api.createUrl(deliveryUrl))
                                        .build()
                                val bytes =
                                    httpClient.newCall(request).execute().use {
                                        if (it.isSuccessful) {
                                            it.body.bytes()
                                        } else {
                                            null
                                        }
                                    }
                                val cues = mutableListOf<CuesWithTiming>()
                                SubripParser().parse(
                                    bytes!!,
                                    SubtitleParser.OutputOptions.allCues(),
                                    cues::add,
                                )
                                Timber.v("Parsed %s external cues", cues.size)
                                val externalCues = ExternalCues(true, cues)
                                _state.update { it.copy(externalCues = externalCues) }
                                null
                            } else {
                                var flags = 0
                                if (stream.isForced) flags = flags.or(C.SELECTION_FLAG_FORCED)
                                if (stream.isDefault) flags = flags.or(C.SELECTION_FLAG_DEFAULT)
                                MediaItem.SubtitleConfiguration
                                    .Builder(
                                        api.createUrl(deliveryUrl).toUri(),
                                    ).setId("e:${stream.index}")
                                    .setMimeType(subtitleMimeTypes[stream.codec])
                                    .setLanguage(stream.language)
                                    .setLabel(stream.title)
                                    .setSelectionFlags(flags)
                                    .build()
                            }
                        }
                    }

                Timber.v("subtitleIndex=$subtitleIndex, externalSubtitleCount=$externalSubtitleCount, externalSubtitle=$externalSubtitle")

                val mediaItem =
                    MediaItem
                        .Builder()
                        .setMediaId(itemId.toString())
                        .setMediaMetadata(
                            item.toMediaMetadata(
                                imageUrlService.getItemImageUrl(
                                    item,
                                    ImageType.PRIMARY,
                                    useSeriesForPrimary = true,
                                ),
                            ),
                        ).setUri(mediaUrl.toUri())
                        .apply {
                            subtitleConfig?.let { setSubtitleConfigurations(listOf(it)) }
                            when (source.container) {
                                Codec.Container.HLS -> setMimeType(MimeTypes.APPLICATION_M3U8)
                                Codec.Container.DASH -> setMimeType(MimeTypes.APPLICATION_MPD)
                            }
                        }.build()

                val playback =
                    CurrentPlayback(
                        item = item,
                        tracks = listOf(),
                        backend = currentPlayer.value!!.backend,
                        playMethod = transcodeType,
                        playSessionId = response.playSessionId,
                        liveStreamId = source.liveStreamId,
                        mediaSourceInfo = source,
                    )

                preferences.appPreferences.playbackPreferences.let { prefs ->
                    source.mediaStreams
                        ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                        ?.let { stream ->
                            refreshRateService.changeRefreshRate(
                                stream = stream,
                                switchRefreshRate = prefs.refreshRateSwitching,
                                switchResolution = prefs.resolutionSwitching,
                            )
                        }
                }
                withContext(Dispatchers.Main) {
                    // TODO, don't need to release & recreate when switching streams
                    this@PlaybackViewModel.activityListener?.let {
                        it.release()
                        player.removeListener(it)
                    }

                    val playbackItemState = PlaybackItemState(playback, currentItemPlayback)
                    val activityListener =
                        TrackActivityPlaybackListener(
                            api = api,
                            player = player,
                            getState = { playbackItemState },
                        )
                    player.addListener(activityListener)
                    this@PlaybackViewModel.activityListener = activityListener

                    _state.update {
                        it.copy(
                            loading = LoadingState.Success,
                            currentPlayback = playback,
                        )
                    }
                    player.setMediaItem(
                        mediaItem,
                        positionMs,
                    )
                    if (audioIndex != null || !isSubtitleHandledInternally) {
                        val onTracksChangedListener =
                            object : Player.Listener {
                                override fun onTracksChanged(tracks: Tracks) {
                                    Timber.v("onTracksChanged: $tracks")
                                    if (tracks.groups.isNotEmpty()) {
                                        val result =
                                            TrackSelectionUtils.createTrackSelections(
                                                player.trackSelectionParameters,
                                                player.currentTracks,
                                                currentPlayer.value!!.backend,
                                                source.supportsDirectPlay,
                                                audioIndex.takeIf { transcodeType == PlayMethod.DIRECT_PLAY },
                                                subtitleIndex,
                                                source,
                                            )
                                        Timber.v("onTracksChanged: %s", result)
                                        if (result.bothSelected) {
                                            player.trackSelectionParameters =
                                                result.trackSelectionParameters
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

        /**
         * If direct playing, can try to switch tracks without playback restarting
         * Except for external subtitles
         */
        @OptIn(UnstableApi::class)
        private suspend fun changeStreamsDirectPlay(
            currentPlayback: CurrentPlayback,
            currentItemPlayback: ItemPlayback,
            audioIndex: Int?,
            subtitleIndex: Int?,
            userInitiated: Boolean,
        ): Boolean =
            withContext(Dispatchers.IO) {
                // TODO there's probably no reason why we can't add external subtitles?
                Timber.v("changeStreams direct play")

                val source = currentPlayback.mediaSourceInfo
                val externalSubtitle = source.findExternalSubtitle(subtitleIndex)

                if (externalSubtitle == null) {
                    val result =
                        withContext(Dispatchers.Main) {
                            TrackSelectionUtils.createTrackSelections(
                                onMain { player.trackSelectionParameters },
                                onMain { player.currentTracks },
                                currentPlayer.value!!.backend,
                                true,
                                audioIndex,
                                subtitleIndex,
                                source,
                            )
                        }
                    if (result.bothSelected) {
                        onMain { player.trackSelectionParameters = result.trackSelectionParameters }
                        // TODO lots of duplicate code in this block
                        Timber.d("Changes tracks audio=$audioIndex, subtitle=$subtitleIndex")
                        val itemPlayback =
                            currentItemPlayback.copy(
                                sourceId = source.id?.toUUIDOrNull(),
                                audioIndex = audioIndex ?: TrackIndex.UNSPECIFIED,
                                // Preserve special constants (ONLY_FORCED, DISABLED) instead of resolved index
                                subtitleIndex =
                                    if (currentItemPlayback.subtitleIndex < 0) {
                                        currentItemPlayback.subtitleIndex
                                    } else {
                                        subtitleIndex ?: TrackIndex.DISABLED
                                    },
                            )
                        if (userInitiated) {
                            viewModelScope.launchIO {
                                Timber.v("Saving user initiated item playback: %s", itemPlayback)
                                val updated = itemPlaybackRepository.saveItemPlayback(itemPlayback)
                                _state.update { it.copy(currentItemPlayback = updated) }
                            }
                        } else {
                            _state.update { it.copy(currentItemPlayback = itemPlayback) }
                        }
                        _state.update {
                            it.copy(
                                currentPlayback =
                                    (it.currentPlayback ?: currentPlayback).copy(
                                        tracks = checkForSupport(player.currentTracks),
                                    ),
                            )
                        }
                        loadSubtitleDelay()
                        return@withContext true
                    }
                } else {
                    Timber.v("changeStreams direct play, external subtitle was requested")
                }
                return@withContext false
            }

        fun changeAudioStream(index: Int) {
            viewModelScope.launchIO {
                Timber.d("Changing audio track to %s", index)
                val itemPlayback =
                    itemPlaybackRepository.saveTrackSelection(
                        item = currentItem.item,
                        itemPlayback = state.value.currentItemPlayback!!,
                        trackIndex = index,
                        type = MediaStreamType.AUDIO,
                    )
                _state.update { it.copy(currentItemPlayback = itemPlayback) }

                // Resolve ONLY_FORCED to actual track based on new audio language
                val source = state.value.currentPlayback?.mediaSourceInfo
                val resolvedSubtitleIndex =
                    if (source != null) {
                        streamChoiceService.resolveSubtitleIndex(
                            source = source,
                            audioStreamIndex = index,
                            seriesId = currentItem.item.data.seriesId,
                            subtitleIndex = itemPlayback.subtitleIndex,
                            prefs = preferences,
                        )
                    } else {
                        itemPlayback.subtitleIndex.takeIf { it >= 0 }
                    }

                changeStreams(
                    currentItem.item,
                    itemPlayback,
                    index,
                    resolvedSubtitleIndex,
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
                        item = currentItem.item,
                        itemPlayback = state.value.currentItemPlayback!!,
                        trackIndex = index,
                        type = MediaStreamType.SUBTITLE,
                    )
                _state.update { it.copy(currentItemPlayback = itemPlayback) }

                // Resolve ONLY_FORCED to actual track index for playback
                val source = state.value.currentPlayback?.mediaSourceInfo
                val resolvedIndex =
                    if (source != null) {
                        streamChoiceService.resolveSubtitleIndex(
                            source = source,
                            audioStreamIndex = itemPlayback.audioIndex,
                            seriesId = currentItem.item.data.seriesId,
                            subtitleIndex = index,
                            prefs = preferences,
                        )
                    } else {
                        index.takeIf { it >= 0 }
                    }

                changeStreams(
                    currentItem.item,
                    itemPlayback,
                    itemPlayback.audioIndex,
                    resolvedIndex,
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
                        .size(Size.ORIGINAL)
                        .build(),
                )
            }
        }

        fun getTrickplayUrl(
            index: Int,
            trickPlayInfo: TrickplayInfo? = state.value.currentMediaInfo.trickPlayInfo,
            mediaSourceId: UUID? = state.value.currentItemPlayback?.sourceId,
        ): String? =
            trickPlayInfo?.let {
                val itemId = currentItem.id
                return api.trickplayApi.getTrickplayTileImageUrl(
                    itemId,
                    trickPlayInfo.width,
                    index,
                    mediaSourceId,
                )
            }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                Timber.v("Playback state is STATE_ENDED")
                viewModelScope.launchDefault {
                    when (val nextItem = state.value.nextItem()) {
                        is PlaylistItem.Intro -> {
                            Timber.v("Next item is intro, so playing immediately")
                            playNextUp()
                        }

                        is PlaylistItem.Media -> {
                            if (currentItem is PlaylistItem.Intro) {
                                Timber.v("Current item is intro, so playing next up immediately")
                                playNextUp()
                            } else if (preferences.appPreferences.playbackPreferences.showNextUpWhen != ShowNextUpWhen.NEXT_UP_NEVER) {
                                Timber.v("Setting next up to ${nextItem.id}")
                                _state.update { it.copy(nextUp = nextItem.item) }
                            } else {
                                controllerViewState.showControls()
                            }
                        }

                        null -> {
                            Timber.v("No next up")
                            navigationManager.goBack()
                        }
                    }
                }
            }
        }

        // Variables for tracking segment state
        private var segmentJob: Job? = null
        private val autoSkippedSegments = mutableSetOf<UUID>()
        private val outroShownSegments = mutableSetOf<UUID>()

        /**
         * Cancels listening for segments and clears current segment state
         */
        private fun resetSegmentState() {
            segmentJob?.cancel()
            autoSkippedSegments.clear()
            outroShownSegments.clear()
            _state.update { it.copy(currentSegment = null) }
        }

        /**
         * This sets up a coroutine to periodically check whether the current playback progress is within a media segment (intro, outro, etc)
         */
        private fun listenForSegments(itemId: UUID) {
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
                            if (currentSegment != null &&
                                currentSegment.itemId == this@PlaybackViewModel.itemId
                            ) {
                                if (currentSegment.id !=
                                    state.value.currentSegment
                                        ?.segment
                                        ?.id
                                ) {
                                    Timber.d(
                                        "Found media segment for %s: %s, %s",
                                        currentSegment.itemId,
                                        currentSegment.id,
                                        currentSegment.type,
                                    )
                                }
                                val state = state.value

                                if (currentSegment.type == MediaSegmentType.OUTRO &&
                                    prefs.showNextUpWhen == ShowNextUpWhen.DURING_CREDITS &&
                                    state.hasNext &&
                                    outroShownSegments.add(currentSegment.id)
                                ) {
                                    val nextItem = state.nextItem()
                                    if (nextItem is PlaylistItem.Media) {
                                        Timber.v("Setting next up during outro to ${nextItem?.id}")
                                        _state.update { it.copy(nextUp = nextItem.item) }
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
                                        val newSegment =
                                            when (behavior) {
                                                SkipSegmentBehavior.AUTO_SKIP -> {
                                                    if (autoSkippedSegments.add(currentSegment.id)) {
                                                        onMain { player.seekTo(currentSegment.endTicks.ticks.inWholeMilliseconds + 1) }
                                                    }
                                                    MediaSegmentState(currentSegment, true)
                                                }

                                                SkipSegmentBehavior.ASK_TO_SKIP -> {
                                                    MediaSegmentState(
                                                        currentSegment,
                                                        autoSkippedSegments.contains(currentSegment.id),
                                                    )
                                                }

                                                else -> {
                                                    null
                                                }
                                            }
                                        _state.update { it.copy(currentSegment = newSegment) }
                                    }
                                }
                            } else if (currentSegment == null) {
                                _state.update { it.copy(currentSegment = null) }
                            }
                        }
                    }
                }
        }

        fun updateSegment(
            segmentId: UUID?,
            dismissed: Boolean,
        ) {
            viewModelScope.launchDefault {
                val segment = state.value.currentSegment?.segment
                if (segment != null && segment.id == segmentId) {
                    autoSkippedSegments.add(segment.id)
                    if (dismissed) {
                        _state.update { it.copy(currentSegment = it.currentSegment?.copy(interacted = true)) }
                    } else {
                        _state.update { it.copy(currentSegment = null) }
                        onMain { player.seekTo(segment.endTicks.ticks.inWholeMilliseconds + 1) }
                    }
                }
            }
        }

        private fun listenForTranscodeReason(): Job =
            viewModelScope.launchIO {
                state.map { it.currentPlayback }.collectLatest {
                    if (it != null && it.playMethod == PlayMethod.TRANSCODE && it.transcodeInfo == null) {
                        try {
                            var transcodeInfo = it.transcodeInfo
                            while (isActive && transcodeInfo == null) {
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
                            updateCurrentPlayback { current ->
                                current?.copy(transcodeInfo = transcodeInfo)
                            }
                        } catch (ex: Exception) {
                            if (ex !is CancellationException) {
                                Timber.w(ex, "Exception trying to get session info")
                                updateCurrentPlayback { current ->
                                    current?.copy(transcodeInfo = null)
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
            viewModelScope.launchDefault {
                playlistMutex.withLock {
                    val state = state.value
                    if (state.hasNext) {
                        cancelUpNextEpisode()
                        val nextIndex = state.playlistIndex + 1
                        val item = state.playlist.items[nextIndex]
                        _state.update { it.copy(playlistIndex = nextIndex) }
                        playlistJob?.cancel()
                        playlistJob =
                            viewModelScope.launchDefault {
                                val played = play(item, 0)
                                if (!played) {
                                    playNextUp()
                                }
                            }
                    } else {
                        Timber.w("Attempting to play next, but there are no more items")
                        return@launchDefault
                    }
                }
            }
        }

        fun playPrevious() {
            viewModelScope.launchDefault {
                playlistMutex.withLock {
                    val state = state.value
                    if (state.hasPrevious) {
                        cancelUpNextEpisode()
                        val previousIndex = state.playlistIndex - 1
                        val item = state.playlist.items[previousIndex]
                        _state.update { it.copy(playlistIndex = previousIndex) }
                        playlistJob?.cancel()
                        playlistJob =
                            viewModelScope.launchDefault {
                                val played = play(item, 0)
                                if (!played) {
                                    playPrevious()
                                }
                            }
                    } else {
                        Timber.w("Attempting to play next, but there are no more items")
                        return@launchDefault
                    }
                }
            }
        }

        suspend fun cancelUpNextEpisode() {
            _state.update { it.copy(nextUp = null) }
        }

        fun playItemInPlaylist(item: BaseItem) {
            viewModelScope.launchDefault {
                playlistMutex.withLock {
                    val state = state.value
                    val index = state.playlist.items.indexOfFirst { it.id == item.id }
                    if (index in state.playlist.items.indices) {
                        val toPlay = state.playlist.items[index]
                        _state.update { it.copy(playlistIndex = index) }
                        playlistJob?.cancel()
                        playlistJob =
                            viewModelScope.launchDefault {
                                val played = play(toPlay, 0)
                                if (!played) {
                                    playNextUp()
                                }
                            }
                    } else {
                        Timber.w("Item not found in playlist %s", item.id)
                        return@launchDefault
                    }
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateCurrentPlayback {
                it?.copy(
                    tracks = checkForSupport(tracks),
                )
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            _state.update { it.copy(subtitleCues = cueGroup.cues) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Playback error")
            viewModelScope.launch(Dispatchers.Main + ExceptionHandler()) {
                state.value.currentPlayback?.let {
                    when (it.playMethod) {
                        PlayMethod.TRANSCODE -> {
                            _state.update {
                                it.copy(
                                    loading =
                                        LoadingState.Error(
                                            "Error during playback",
                                            error,
                                        ),
                                )
                            }
                        }

                        PlayMethod.DIRECT_STREAM, PlayMethod.DIRECT_PLAY -> {
                            Timber.w("Playback error during ${it.playMethod}, falling back to transcoding")
                            val currentItemPlayback = state.value.currentItemPlayback!!
                            changeStreams(
                                currentItem.item,
                                currentItemPlayback,
                                currentItemPlayback.audioIndex,
                                currentItemPlayback.subtitleIndex,
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
            Timber.v("release")
            disconnectPlayer()
            activityListener = null
        }

        fun subscribe(): Job =
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

        /**
         * Atomically update [currentMediaInfo]
         */
        internal suspend fun updateCurrentMedia(block: (CurrentMediaInfo) -> CurrentMediaInfo) =
            withContext(Dispatchers.IO) {
                _state.update {
                    it.copy(currentMediaInfo = block.invoke(it.currentMediaInfo))
                }
            }

        private fun updateDecoder(
            decoderName: String,
            type: MediaType,
        ) {
            viewModelScope.launchDefault {
                val codecInfo =
                    MediaCodecList(MediaCodecList.ALL_CODECS)
                        .codecInfos
                        .firstOrNull { !it.isEncoder && it.name == decoderName }
                val decoderString =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (codecInfo?.isHardwareAccelerated == true) {
                            "$decoderName (HW)"
                        } else {
                            decoderName
                        }
                    } else {
                        decoderName
                    }
                updateCurrentPlayback {
                    when (type) {
                        MediaType.VIDEO -> it?.copy(videoDecoder = decoderString)
                        MediaType.AUDIO -> it?.copy(audioDecoder = decoderString)
                        else -> throw IllegalArgumentException("Unsupported type: $type")
                    }
                }
            }
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Timber.v("onVideoDecoderInitialized: decoder=$decoderName")
            updateDecoder(decoderName, MediaType.VIDEO)
        }

        override fun onVideoDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            Timber.d("onVideoDisabled")
            updateCurrentPlayback { it?.copy(videoDecoder = null) }
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            decoderReuseEvaluation?.let { decoder ->
                if (decoder.result != DecoderReuseEvaluation.REUSE_RESULT_NO) {
                    Timber.d("onVideoInputFormatChanged: decoder=${decoder.decoderName}")
                    updateDecoder(decoder.decoderName, MediaType.VIDEO)
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
            updateDecoder(decoderName, MediaType.AUDIO)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            decoderReuseEvaluation?.let { decoder ->
                if (decoder.result != DecoderReuseEvaluation.REUSE_RESULT_NO) {
                    Timber.d("decoder: onAudioInputFormatChanged: decoder=${decoder.decoderName}")
                    updateDecoder(decoder.decoderName, MediaType.AUDIO)
                }
            }
        }

        override fun onAudioDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            Timber.d("decoder: onAudioDisabled")
            updateCurrentPlayback { it?.copy(audioDecoder = null) }
        }

        private var subtitleDelaySaveJob: Job? = null

        fun updateSubtitleDelay(delta: Duration) {
            subtitleDelaySaveJob?.cancel()
            updateCurrentPlayback {
                it?.let {
                    val newDelay = it.subtitleDelay + delta
                    val result = it.copy(subtitleDelay = it.subtitleDelay + delta)
                    subtitleDelaySaveJob =
                        viewModelScope.launchIO {
                            // Debounce & save
                            // TODO listen and debounce in flows instead
                            state.value.currentItemPlayback?.let { item ->
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
            state.value.currentItemPlayback?.let {
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
                        updateCurrentPlayback { it?.copy(subtitleDelay = result.delayMs.milliseconds) }
                    }
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            screensaverService.keepScreenOn(isPlaying)
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            val player = this@PlaybackViewModel.player
            if (availableCommands.contains(Player.COMMAND_PREPARE) && player is MpvPlayer) {
                // MpvPlayer is initialized, so configure subtitles
                Timber.i("Applying subtitle config to MPV")
                viewModelScope.launchDefault {
                    val configuration = context.resources.configuration
                    val density = Density(context.resources.displayMetrics.density)
                    preferences.appPreferences.interfacePreferences.subtitlesPreferences.applyToMpv(
                        configuration,
                        density,
                    )
                }
            }
        }

        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) {
            Timber.v(
                "onBandwidthEstimate: totalLoadTimeMs=%s, totalBytesLoaded=%s, bitrateEstimate=%s",
                totalLoadTimeMs,
                totalBytesLoaded,
                bitrateEstimate,
            )
            if (totalLoadTimeMs > 0 && totalBytesLoaded > 0) {
                _state.update {
                    it.copy(
                        analyticsState =
                            it.analyticsState.copy(
                                bitrate = formatBitrate((totalBytesLoaded.toDouble() / (totalLoadTimeMs / 1000.0) * 8).roundToInt()),
                                bitrateEstimate = formatBitrate(bitrateEstimate.toInt()),
                            ),
                    )
                }
            }
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            _state.update {
                it.copy(
                    analyticsState =
                        it.analyticsState.copy(
                            droppedFrames = it.analyticsState.droppedFrames + droppedFrames,
                        ),
                )
            }
        }
    }
