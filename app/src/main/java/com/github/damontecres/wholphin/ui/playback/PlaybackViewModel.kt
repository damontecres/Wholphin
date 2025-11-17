package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.ui.text.intl.Locale
import androidx.core.net.toUri
import androidx.datastore.core.DataStore
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
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.data.model.chooseSource
import com.github.damontecres.wholphin.data.model.chooseStream
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.ShowNextUpWhen
import com.github.damontecres.wholphin.preferences.SkipSegmentBehavior
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.ui.formatDateTime
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.seasonEpisodePadded
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
import com.github.damontecres.wholphin.util.mpv.mpvDeviceProfile
import com.github.damontecres.wholphin.util.subtitleMimeTypes
import com.github.damontecres.wholphin.util.supportItemKinds
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
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
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
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

data class StreamDecision(
    val itemId: UUID,
    val type: PlayMethod,
    val url: String,
)

@HiltViewModel
@OptIn(markerClass = [UnstableApi::class])
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
        val appPreferences: DataStore<AppPreferences>,
        private val playerFactory: PlayerFactory,
        private val datePlayedService: DatePlayedService,
    ) : ViewModel(),
        Player.Listener {
        val player by lazy {
            playerFactory.createVideoPlayer()
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
        private val autoSkippedSegments = mutableSetOf<UUID>()

        private lateinit var preferences: UserPreferences
        private lateinit var deviceProfile: DeviceProfile
        private lateinit var itemId: UUID
        private lateinit var item: BaseItem
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
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loading,
                        "Error preparing for playback for ${destination.itemId}",
                    ),
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
                        if (playlist.items.isEmpty()) {
                            showToast(context, "Playlist is empty", Toast.LENGTH_SHORT)
                            navigationManager.goBack()
                            return@launch
                        }
                        withContext(Dispatchers.Main) {
                            this@PlaybackViewModel.playlist.value = playlist
                        }
                        // TODO start index
                        playlist.items.first().data
                    } else {
                        queriedItem
                    }
                val item = BaseItem.from(base, api)

                val played =
                    play(
                        item,
                        destination.positionMs,
                        destination.itemPlayback,
                        destination.forceTranscoding,
                    )
                if (!played) {
                    playNextUp()
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
            item: BaseItem,
            positionMs: Long,
            itemPlayback: ItemPlayback? = null,
            forceTranscoding: Boolean = false,
        ): Boolean =
            withContext(Dispatchers.IO) {
                Timber.i("Playing ${item.id}")
                datePlayedService.invalidate(item)
                autoSkippedSegments.clear()
                if (item.type !in supportItemKinds) {
                    showToast(
                        context,
                        "Unsupported type '${item.type}', skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }
                val isLiveTv = item.type == BaseItemKind.TV_CHANNEL

                val base = item.data
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
                        serverRepository.currentUser.value?.let { user ->
                            itemPlaybackDao.getItem(user, base.id)?.let {
                                Timber.v("Fetched itemPlayback from DB: %s", it)
                                if (it.sourceId != null) {
                                    it
                                } else {
                                    null
                                }
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
                    this@PlaybackViewModel.audioStreams.value = audioStreams
                    this@PlaybackViewModel.subtitleStreams.value = subtitleStreams

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

                    this@PlaybackViewModel.chapters.value = Chapter.fromDto(base, api)
                    Timber.v("chapters=${this@PlaybackViewModel.chapters.value?.size}")
                }
                listenForSegments()
                return@withContext true
            }

        @OptIn(UnstableApi::class)
        private suspend fun changeStreams(
            item: BaseItem,
            currentItemPlayback: ItemPlayback = this@PlaybackViewModel.currentItemPlayback.value!!,
            audioIndex: Int?,
            subtitleIndex: Int?,
            positionMs: Long = 0,
            userInitiated: Boolean,
            enableDirectPlay: Boolean = true,
            enableDirectStream: Boolean = true,
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
                            applyTrackSelections(
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
                            this@PlaybackViewModel.currentPlayback.value =
                                currentPlayback.copy(
                                    tracks = checkForSupport(player.currentTracks),
                                )
                            this@PlaybackViewModel.currentItemPlayback.value = itemPlayback
                        }

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
                                    deviceProfile
                                } else {
                                    mpvDeviceProfile
                                },
                            maxAudioChannels = null,
                            audioStreamIndex = audioIndex,
                            subtitleStreamIndex = subtitleIndex,
                            mediaSourceId = currentItemPlayback.sourceId?.toServerString(),
                            alwaysBurnInSubtitleWhenTranscoding = null,
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
                val decision = StreamDecision(itemId, transcodeType, mediaUrl)
                Timber.v("Playback decision: $decision")

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
                    this@PlaybackViewModel.currentPlayback.value = playback
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
                                        val result =
                                            applyTrackSelections(
                                                player,
                                                playerBackend,
                                                source.supportsDirectPlay,
                                                audioIndex,
                                                subtitleIndex,
                                                source,
                                            )
                                        if (result.bothSelected) {
                                            player.removeListener(this)
                                        }
                                    }
                                }
                            }
                        player.addListener(onTracksChangedListener)
                    }
                }
                val trickPlayInfo =
                    item.data.trickplay
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
            viewModelScope.launchIO {
                changeStreams(
                    item,
                    currentItemPlayback.value!!,
                    index,
                    currentItemPlayback.value?.subtitleIndex,
                    onMain { player.currentPosition },
                    true,
                )
            }
        }

        fun changeSubtitleStream(index: Int?): Job =
            viewModelScope.launchIO {
                changeStreams(
                    item,
                    currentItemPlayback.value!!,
                    currentItemPlayback.value?.audioIndex,
                    index,
                    onMain { player.currentPosition },
                    true,
                )
            }

        fun getTrickplayUrl(index: Int): String? {
            val itemId = item.id
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
                                    viewModelScope.launchIO {
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
            currentPlayback.value =
                currentPlayback.value?.copy(
                    tracks = checkForSupport(tracks),
                )
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Playback error")
            viewModelScope.launch(Dispatchers.Main + ExceptionHandler()) {
                currentPlayback.value?.let {
                    when (it.playMethod) {
                        PlayMethod.TRANSCODE ->
                            loading.setValueOnMain(
                                LoadingState.Error(
                                    "Error during playback",
                                    error,
                                ),
                            )

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
                        when (it.command) {
                            PlaystateCommand.STOP -> {
                                release()
                                navigationManager.goBack()
                            }

                            PlaystateCommand.PAUSE -> player.pause()
                            PlaystateCommand.UNPAUSE -> player.play()
                            PlaystateCommand.NEXT_TRACK -> playNextUp()
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
        val subtitleSearchLanguage = MutableLiveData<String>(Locale.current.language)

        fun searchForSubtitles(language: String = Locale.current.language) {
            subtitleSearch.value = SubtitleSearch.Searching
            subtitleSearchLanguage.value = language
            viewModelScope.launchIO {
                try {
                    currentItemPlayback.value?.itemId?.let {
                        Timber.v("Searching for remote subtitles for %s", it)
                        val results =
                            api.subtitleApi
                                .searchRemoteSubtitles(
                                    itemId = it,
                                    language = language,
                                ).content
                                .sortedWith(
                                    compareByDescending<RemoteSubtitleInfo> { it.communityRating }
                                        .thenByDescending { it.downloadCount },
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
                            val currentSubtitleStreams =
                                this@PlaybackViewModel
                                    .subtitleStreams.value
                                    .orEmpty()
                            val subtitleCount = currentSubtitleStreams.size
                            var newCount = subtitleCount
                            var maxAttempts = 4
                            var newStreams: List<SubtitleStream> = listOf()

                            // The server triggers a refresh in the background, so query periodically for the item until its updated
                            while (maxAttempts > 0 && subtitleCount == newCount) {
                                maxAttempts--
                                delay(1500)
                                item =
                                    BaseItem.from(
                                        api.userLibraryApi.getItem(itemId = item.id).content,
                                        api,
                                    )
                                val mediaSource = chooseSource(item.data, it)
                                if (mediaSource == null) {
                                    // This shouldn't happen, but just in case
                                    showToast(
                                        context,
                                        "Item is no longer playable...",
                                        Toast.LENGTH_SHORT,
                                    )
                                    return@launchIO
                                }

                                val subtitleStreams =
                                    mediaSource.mediaStreams
                                        ?.filter { it.type == MediaStreamType.SUBTITLE }
                                        .orEmpty()
                                newCount = subtitleStreams.size

                                if (subtitleCount != newCount) {
                                    newStreams =
                                        subtitleStreams.map {
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
                                        }
                                    this@PlaybackViewModel.subtitleStreams.setValueOnMain(newStreams)
                                }
                            }
                            if (maxAttempts == 0) {
                                showToast(
                                    context,
                                    context.getString(R.string.subtitle_download_too_long),
                                )
                            } else {
                                // Find the new subtitle stream
                                val newStream =
                                    newStreams
                                        .toMutableList()
                                        .apply {
                                            removeAll(currentSubtitleStreams)
                                        }.firstOrNull { it.external }
                                if (newStream != null) {
                                    var audioIndex = currentItemPlayback.value?.audioIndex
                                    if (audioIndex != null && audioIndex != TrackIndex.UNSPECIFIED) {
                                        // User has picked a specific audio track
                                        // Since, now adding a new external subtitle track, need to adjust the audio index as well
                                        Timber.v("New external subtitle, audioIndex=$audioIndex, adding 1")
                                        audioIndex += 1
                                    }
                                    changeStreams(
                                        item,
                                        currentItemPlayback.value!!,
                                        audioIndex,
                                        newStream.index,
                                        onMain { player.currentPosition },
                                        true,
                                    )
                                }
                            }
                            subtitleSearch.setValueOnMain(null)
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
    val item: BaseItem,
    val tracks: List<TrackSupport>,
    val backend: PlayerBackend,
    val playMethod: PlayMethod,
    val playSessionId: String?,
    val liveStreamId: String?,
    val mediaSourceInfo: MediaSourceInfo,
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

/**
 * Returns the number of external subtitle streams there are
 */
val MediaSourceInfo.externalSubtitlesCount: Int
    get() =
        mediaStreams
            ?.count { it.type == MediaStreamType.SUBTITLE && it.isExternal } ?: 0

/**
 * Returns the number of embedded subtitle streams there are
 */
val MediaSourceInfo.embeddedSubtitleCount: Int
    get() =
        mediaStreams
            ?.count { it.type == MediaStreamType.SUBTITLE && !it.isExternal } ?: 0

/**
 * Returns the number of video streams there are
 */
val MediaSourceInfo.videoStreamCount: Int
    get() =
        mediaStreams
            ?.count { it.type == MediaStreamType.VIDEO } ?: 0

/**
 * Returns the number of audio streams there are
 */
val MediaSourceInfo.audioStreamCount: Int
    get() =
        mediaStreams
            ?.count { it.type == MediaStreamType.AUDIO } ?: 0

/**
 * Returns the [MediaStream] for the given subtitle index iff it is external
 */
fun MediaSourceInfo.findExternalSubtitle(subtitleIndex: Int?): MediaStream? =
    subtitleIndex?.let {
        mediaStreams
            ?.firstOrNull { it.type == MediaStreamType.SUBTITLE && it.isExternal && it.index == subtitleIndex }
    }

suspend fun <T> onMain(block: suspend CoroutineScope.() -> T) = withContext(Dispatchers.Main, block)

data class TrackSelectionResult(
    val audioSelected: Boolean,
    val subtitleSelected: Boolean,
) {
    val bothSelected: Boolean = audioSelected && subtitleSelected
}

@OptIn(UnstableApi::class)
private fun applyTrackSelections(
    player: Player,
    playerBackend: PlayerBackend,
    supportsDirectPlay: Boolean,
    audioIndex: Int?,
    subtitleIndex: Int?,
    source: MediaSourceInfo,
): TrackSelectionResult {
    val videoStreamCount = source.videoStreamCount
    val audioStreamCount = source.audioStreamCount
    val embeddedSubtitleCount = source.embeddedSubtitleCount
    val externalSubtitleCount = source.externalSubtitlesCount

    val paramsBuilder = player.trackSelectionParameters.buildUpon()
    val tracks = player.currentTracks.groups

    val subtitleSelected =
        if (subtitleIndex != null && subtitleIndex >= 0) {
            val subtitleIsExternal = source.findExternalSubtitle(subtitleIndex) != null
            if (subtitleIsExternal || supportsDirectPlay) {
                val chosenTrack =
                    if (subtitleIsExternal && playerBackend == PlayerBackend.EXO_PLAYER) {
                        tracks.firstOrNull { group ->
                            group.type == C.TRACK_TYPE_TEXT && group.isSupported &&
                                (0..<group.mediaTrackGroup.length)
                                    .mapNotNull {
                                        group.getTrackFormat(it).id
                                    }.any { it.endsWith("e:$subtitleIndex") }
                        }
                    } else {
                        val indexToFind =
                            calculateIndexToFind(
                                subtitleIndex,
                                MediaStreamType.SUBTITLE,
                                playerBackend,
                                videoStreamCount,
                                audioStreamCount,
                                embeddedSubtitleCount,
                                externalSubtitleCount,
                                subtitleIsExternal,
                            )
                        Timber.v("Chosen subtitle ($subtitleIndex/$indexToFind) track")
                        // subtitleIndex - externalSubtitleCount + 1
                        tracks.firstOrNull { group ->
                            group.type == C.TRACK_TYPE_TEXT && group.isSupported &&
                                (0..<group.mediaTrackGroup.length)
                                    .map {
                                        group.getTrackFormat(it).idAsInt
                                    }.contains(indexToFind)
                        }
                    }

                Timber.v("Chosen subtitle ($subtitleIndex) track: $chosenTrack")
                chosenTrack?.let {
                    paramsBuilder
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(
                            TrackSelectionOverride(
                                chosenTrack.mediaTrackGroup,
                                0,
                            ),
                        )
                }
                chosenTrack != null
            } else {
                false
            }
        } else {
            paramsBuilder
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)

            true
        }
    val audioSelected =
        if (audioIndex != null && supportsDirectPlay) {
            val indexToFind =
                calculateIndexToFind(
                    audioIndex,
                    MediaStreamType.AUDIO,
                    playerBackend,
                    videoStreamCount,
                    audioStreamCount,
                    embeddedSubtitleCount,
                    externalSubtitleCount,
                    false,
                )
            val chosenTrack =
                tracks.firstOrNull { group ->
                    group.type == C.TRACK_TYPE_AUDIO && group.isSupported &&
                        (0..<group.mediaTrackGroup.length)
                            .map {
                                group.getTrackFormat(it).idAsInt
                            }.contains(indexToFind)
                }
            Timber.v("Chosen audio ($audioIndex/$indexToFind) track: $chosenTrack")
            chosenTrack?.let {
                paramsBuilder
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    .setOverrideForType(
                        TrackSelectionOverride(
                            chosenTrack.mediaTrackGroup,
                            0,
                        ),
                    )
            }
            chosenTrack != null
        } else {
            audioIndex == null
        }
    if (audioSelected && subtitleSelected) {
        player.trackSelectionParameters = paramsBuilder.build()
    }
    return TrackSelectionResult(audioSelected, subtitleSelected)
}

/**
 * Maps the server provided index to the track index based on the [PlayerBackend] and other stream information
 */
private fun calculateIndexToFind(
    serverIndex: Int,
    type: MediaStreamType,
    playerBackend: PlayerBackend,
    videoStreamCount: Int,
    audioStreamCount: Int,
    embeddedSubtitleCount: Int,
    externalSubtitleCount: Int,
    subtitleIsExternal: Boolean,
): Int =
    when (playerBackend) {
        PlayerBackend.EXO_PLAYER,
        PlayerBackend.UNRECOGNIZED,
        -> {
            serverIndex - externalSubtitleCount + 1
        }

        // TODO MPV could use literal indexes because they are stored in the track format ID
        PlayerBackend.MPV -> {
            when (type) {
                MediaStreamType.VIDEO -> serverIndex - externalSubtitleCount + 1
                MediaStreamType.AUDIO -> serverIndex - externalSubtitleCount - videoStreamCount + 1
                MediaStreamType.SUBTITLE -> {
                    if (subtitleIsExternal) {
                        serverIndex + embeddedSubtitleCount + 1
                    } else {
                        serverIndex - externalSubtitleCount - videoStreamCount - audioStreamCount + 1
                    }
                }
                else -> throw UnsupportedOperationException("Cannot calculate index for $type")
            }
        }
    }
