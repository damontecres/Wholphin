package com.github.damontecres.dolphin.ui.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.data.model.Chapter
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.DefaultItemFields
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.util.ApiRequestPager
import com.github.damontecres.dolphin.util.ExceptionHandler
import com.github.damontecres.dolphin.util.GetEpisodesRequestHandler
import com.github.damontecres.dolphin.util.TrackActivityPlaybackListener
import com.github.damontecres.dolphin.util.TrackSupport
import com.github.damontecres.dolphin.util.checkForSupport
import com.github.damontecres.dolphin.util.formatDateTime
import com.github.damontecres.dolphin.util.seasonEpisodePadded
import com.github.damontecres.dolphin.util.subtitleMimeTypes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.trickplayApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.jellyfin.sdk.model.api.TrickplayInfo
import org.jellyfin.sdk.model.api.request.GetEpisodesRequest
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration

enum class TranscodeType {
    DIRECT_PLAY,
    DIRECT_STREAM,
    TRANSCODE,
}

data class StreamDecision(
    val itemId: UUID,
    val type: TranscodeType,
    val url: String,
)

@HiltViewModel
class PlaybackViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        val api: ApiClient,
    ) : ViewModel() {
        val player: ExoPlayer =
            ExoPlayer
                .Builder(context)
                .build()
                .apply {
                    playWhenReady = true
                }

        val stream = MutableLiveData<StreamDecision?>(null)

        val title = MutableLiveData<String?>(null)
        val subtitle = MutableLiveData<String?>(null)
        val duration = MutableLiveData<Duration?>(null)
        val audioStreams = MutableLiveData<List<AudioStream>>(listOf())
        val subtitleStreams = MutableLiveData<List<SubtitleStream>>(listOf())
        val currentPlayback = MutableLiveData<CurrentPlayback?>(null)
        val trickplay = MutableLiveData<TrickplayInfo?>(null)
        val chapters = MutableLiveData<List<Chapter>>(listOf())

        private lateinit var preferences: UserPreferences
        private lateinit var deviceProfile: DeviceProfile
        private lateinit var itemId: UUID
        private lateinit var dto: BaseItemDto

        private val episodes = MutableLiveData<ApiRequestPager<GetEpisodesRequest>>()
        private var currentEpisodeIndex = Int.MAX_VALUE
        val nextUpEpisode = MutableLiveData<BaseItem?>()

        init {
            addCloseable { player.release() }
        }

        fun init(
            destination: Destination.Playback,
            deviceProfile: DeviceProfile,
            preferences: UserPreferences,
        ) {
            nextUpEpisode.value = null
            this.preferences = preferences
            this.deviceProfile = deviceProfile
            val itemId = destination.itemId
            this.itemId = itemId
            val item = destination.item
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                val base = item?.data ?: api.userLibraryApi.getItem(itemId).content
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
                base.mediaStreams
                    ?.filter { it.type == MediaStreamType.VIDEO }
                    ?.forEach { Timber.v("${it.videoRangeType}, ${it.videoRange}") }
                val subtitleStreams =
                    base.mediaStreams
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
                            )
                        }.orEmpty()
                val audioStreams =
                    base.mediaStreams
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

                // TODO audio selection based on channel layout, etc
                val audioLanguage = preferences.userConfig.audioLanguagePreference
                val audioIndex =
                    if (audioLanguage != null) {
                        audioStreams.firstOrNull { it.language == audioLanguage }?.index
                            ?: audioStreams.firstOrNull()?.index
                    } else {
                        audioStreams.firstOrNull()?.index
                    }
                val subtitleMode = preferences.userConfig.subtitleMode
                val subtitleLanguage = preferences.userConfig.subtitleLanguagePreference
                val subtitleIndex =
                    when (subtitleMode) {
                        SubtitlePlaybackMode.ALWAYS -> {
                            if (subtitleLanguage != null) {
                                subtitleStreams.firstOrNull { it.language == subtitleLanguage }?.index
                            } else {
                                subtitleStreams.firstOrNull()?.index
                            }
                        }

                        SubtitlePlaybackMode.ONLY_FORCED ->
                            if (subtitleLanguage != null) {
                                subtitleStreams.firstOrNull { it.language == subtitleLanguage && it.forced }?.index
                            } else {
                                subtitleStreams.firstOrNull { it.forced }?.index
                            }

                        SubtitlePlaybackMode.SMART -> {
                            if (audioLanguage != null && subtitleLanguage != null && audioLanguage != subtitleLanguage) {
                                subtitleStreams.firstOrNull { it.language == subtitleLanguage }?.index
                            } else {
                                null
                            }
                        }

                        SubtitlePlaybackMode.DEFAULT -> {
                            // TODO check for language?
                            (
                                subtitleStreams.firstOrNull { it.default && it.forced }
                                    ?: subtitleStreams.firstOrNull { it.default }
                                    ?: subtitleStreams.firstOrNull { it.forced }
                            )?.index
                        }

                        SubtitlePlaybackMode.NONE -> null
                    }

                Timber.v("base.mediaStreams=${base.mediaStreams}")
                Timber.v("subtitleTracks=$subtitleStreams")
                Timber.v("audioStreams=$audioStreams")
                Timber.d("Selected audioIndex=$audioIndex, subtitleIndex=$subtitleIndex")

                withContext(Dispatchers.Main) {
                    this@PlaybackViewModel.audioStreams.value = audioStreams
                    this@PlaybackViewModel.subtitleStreams.value = subtitleStreams
                    val activityListener =
                        TrackActivityPlaybackListener(api, itemId, player)
                    addCloseable { activityListener.release() }
                    player.addListener(activityListener)
                    changeStreams(
                        itemId,
                        audioIndex,
                        subtitleIndex,
                        if (destination.positionMs > 0) destination.positionMs else C.TIME_UNSET,
                    )
                    player.prepare()

                    this@PlaybackViewModel.chapters.value = Chapter.fromDto(dto, api)
                    Timber.v("chapters=${this@PlaybackViewModel.chapters.value}")
                }
                if (base.type == BaseItemKind.EPISODE) {
                    base.seriesId?.let(::getEpisodes)
                }
            }
        }

        @OptIn(UnstableApi::class)
        private suspend fun changeStreams(
            itemId: UUID,
            audioIndex: Int?,
            subtitleIndex: Int?,
            positionMs: Long = C.TIME_UNSET,
        ) {
            if (currentPlayback.value?.let {
                    it.itemId == itemId &&
                        it.audioIndex == audioIndex &&
                        it.subtitleIndex == subtitleIndex
                } == true
            ) {
                Timber.i("No change in playback for changeStreams")
                return
            }
            val response =
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
                            mediaSourceId = null,
                            alwaysBurnInSubtitleWhenTranscoding = null,
                        ),
                    ).content
            val source = response.mediaSources.firstOrNull()
            source?.let { source ->
                val mediaUrl =
                    if (source.supportsDirectPlay) {
                        api.videosApi.getVideoStreamUrl(
                            itemId = itemId,
                            mediaSourceId = source.id,
                            static = true,
                            tag = source.eTag,
                        )
                    } else if (source.supportsDirectStream) {
                        api.createUrl(source.transcodingUrl!!)
                    } else {
                        api.createUrl(source.transcodingUrl!!)
                    }
                val transcodeType =
                    when {
                        source.supportsDirectPlay -> TranscodeType.DIRECT_PLAY
                        source.supportsDirectStream -> TranscodeType.DIRECT_STREAM
                        source.supportsTranscoding -> TranscodeType.TRANSCODE
                        else -> throw Exception("No supported playback method")
                    }
                val decision = StreamDecision(itemId, transcodeType, mediaUrl)
                Timber.v("Playback decision: $decision")

                val externalSubtitle =
                    source.mediaStreams
                        ?.firstOrNull { it.type == MediaStreamType.SUBTITLE && it.index == subtitleIndex && it.isExternal }
                        ?.let {
                            it.deliveryUrl?.let { deliveryUrl ->
                                var flags = 0
                                if (it.isForced) flags = flags.and(C.SELECTION_FLAG_FORCED)
                                if (it.isDefault) flags = flags.and(C.SELECTION_FLAG_DEFAULT)
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
                        itemId,
                        audioIndex,
                        subtitleIndex,
                        source.id?.toUUIDOrNull(),
                        listOf(),
                    )

                withContext(Dispatchers.Main) {
                    duration.value = source.runTimeTicks?.ticks
                    stream.value = decision
                    currentPlayback.value = playback
                    player.setMediaItem(
                        mediaItem,
                        positionMs,
                    )
                    if (audioIndex != null || subtitleIndex != null) {
                        val trackActivationListener =
                            object : Player.Listener {
                                override fun onTracksChanged(tracks: Tracks) {
                                    Timber.v("onTracksChanged: $tracks")
                                    if (tracks.groups.isNotEmpty()) {
                                        applyTrackSelections(
                                            player,
                                            source,
                                            audioIndex,
                                            subtitleIndex,
                                        )
                                        currentPlayback.value =
                                            currentPlayback.value?.copy(
                                                tracks = checkForSupport(tracks),
                                            )
                                        player.removeListener(this)
                                    }
                                }
                            }
                        player.addListener(trackActivationListener)
                    }
                }
                val trickPlayInfo =
                    dto.trickplay
                        ?.get(source.id)
                        ?.values
                        ?.firstOrNull()
                Timber.v("Trickplay info: $trickPlayInfo")
                withContext(Dispatchers.Main) {
                    trickplay.value = trickPlayInfo
                }
            }
        }

        fun changeAudioStream(index: Int) {
            val itemId = currentPlayback.value?.itemId ?: return
            viewModelScope.launch(ExceptionHandler()) {
                changeStreams(
                    itemId,
                    index,
                    currentPlayback.value?.subtitleIndex,
                    player.currentPosition,
                )
            }
        }

        fun changeSubtitleStream(index: Int?) {
            val itemId = currentPlayback.value?.itemId ?: return
            viewModelScope.launch(ExceptionHandler()) {
                changeStreams(
                    itemId,
                    currentPlayback.value?.audioIndex,
                    index,
                    player.currentPosition,
                )
            }
        }

        fun getTrickplayUrl(index: Int): String? {
            val itemId = dto.id
            val mediaSourceId = currentPlayback.value?.mediaSourceId
            val trickPlayInfo = trickplay.value ?: return null
            return api.trickplayApi.getTrickplayTileImageUrl(
                itemId,
                trickPlayInfo.width,
                index,
                mediaSourceId,
            )
        }

        fun getEpisodes(seriesId: UUID) {
            viewModelScope.launch(Dispatchers.IO) {
                val episodes =
                    if (!this@PlaybackViewModel.episodes.isInitialized) {
                        val request =
                            GetEpisodesRequest(seriesId = seriesId, fields = DefaultItemFields)
                        val pager =
                            ApiRequestPager(api, request, GetEpisodesRequestHandler, viewModelScope)
                        pager.init()
                        currentEpisodeIndex = pager.indexOfBlocking { it?.id == itemId }
                        pager
                    } else {
                        this@PlaybackViewModel.episodes.value!!
                    }
                Timber.v("Current episode is $currentEpisodeIndex of ${episodes.size}")
                val nextIndex = currentEpisodeIndex + 1
                if (nextIndex < episodes.size) {
                    val listener =
                        object : Player.Listener {
                            override fun onPlaybackStateChanged(playbackState: Int) {
                                if (playbackState == Player.STATE_ENDED) {
                                    viewModelScope.launch(Dispatchers.IO) {
                                        val nextItem = episodes.getBlocking(nextIndex)
                                        Timber.v("Setting next up episode to ${nextItem?.id}")
                                        withContext(Dispatchers.Main) {
                                            nextUpEpisode.value = nextItem
                                        }
                                    }
                                    player.removeListener(this)
                                }
                            }
                        }
                    player.addListener(listener)

//                    viewModelScope.launch(Dispatchers.IO) {
//                        while (this.isActive) {
//                            delay(5.seconds)
//                            val remaining =
//                                withContext(Dispatchers.Main) {
//                                    (player.duration - player.currentPosition).milliseconds
//                                }
//                            if (remaining < 2.minutes) { // TODO time & preference
//                                val nextItem = episodes.getBlocking(nextIndex)
//                                Timber.v("Setting next up episode to ${nextItem?.id}")
//                                withContext(Dispatchers.Main) {
//                                    nextUpEpisode.value = nextItem
//                                }
//                                break
//                            }
//                        }
//                    }
                }
            }
        }

        fun playUpNextEpisode() {
            nextUpEpisode.value?.let {
                init(Destination.Playback(it.id, 0, it), deviceProfile, preferences)
            }
        }

        fun cancelUpNextEpisode() {
            nextUpEpisode.value = null
        }
    }

data class CurrentPlayback(
    val itemId: UUID,
    val audioIndex: Int?,
    val subtitleIndex: Int?,
    val mediaSourceId: UUID?,
    val tracks: List<TrackSupport>,
)

private val Format.idAsInt: Int?
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
                            }.contains(audioIndex + 1) // Indexes are 1 based
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
