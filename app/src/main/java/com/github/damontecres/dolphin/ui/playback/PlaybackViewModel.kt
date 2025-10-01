package com.github.damontecres.dolphin.ui.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.util.TrackActivityPlaybackListener
import com.github.damontecres.dolphin.util.profile.PlaybackListener
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
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.TrickplayInfo
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
) {
    val mediaItem: MediaItem
        get() =
            MediaItem
                .Builder()
                .setMediaId(itemId.toString())
                .setUri(url.toUri())
                .build()
}

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

        private lateinit var deviceProfile: DeviceProfile
        private lateinit var dto: BaseItemDto

        init {
            addCloseable { player.release() }
        }

        fun init(
            destination: Destination.Playback,
            deviceProfile: DeviceProfile,
            preferences: UserPreferences,
        ) {
            this.deviceProfile = deviceProfile
            val itemId = destination.itemId
            val item = destination.item
            viewModelScope.launch(Dispatchers.IO) {
                val base = item?.data ?: api.userLibraryApi.getItem(itemId).content
                dto = base

                val title = base.name
                val subtitle =
                    if (base.type == BaseItemKind.EPISODE) {
                        val season = base.parentIndexNumber?.toString()?.padStart(2, '0')
                        val episode = base.indexNumber?.toString()?.padStart(2, '0')
                        // TODO multi episode support
                        if (season != null && episode != null) {
                            buildString {
                                if (base.seriesName != null) {
                                    append(base.seriesName)
                                    append(" - ")
                                }
                                append("S${season}E$episode")
                            }
                        } else {
                            null
                        }
                    } else {
                        null
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
                            SubtitleStream(it.index, it.language, it.title, it.codec, it.codecTag)
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
                // TODO use preferences to select audio/subtitle
                // TODO audio selection based on channel layout, etc
                val subtitleIndex = subtitleStreams.firstOrNull { it.language == "eng" }?.index
                val audioIndex =
                    audioStreams.firstOrNull { it.language == "eng" }?.index
                        ?: audioStreams.firstOrNull()?.index

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
                    player.addListener(PlaybackListener())
                    changeStreams(
                        itemId,
                        audioIndex,
                        subtitleIndex,
                        if (destination.positionMs > 0) destination.positionMs else C.TIME_UNSET,
                    )
                    player.prepare()
                }
            }
        }

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
                        // TODO device profile, etc
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

                withContext(Dispatchers.Main) {
                    duration.value = source.runTimeTicks?.ticks
                    stream.value = decision
                    currentPlayback.value =
                        CurrentPlayback(
                            itemId,
                            audioIndex,
                            subtitleIndex,
                            source.id?.toUUIDOrNull(),
                        )
                    player.setMediaItem(
                        decision.mediaItem,
                        positionMs,
                    )
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
            viewModelScope.launch {
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
            viewModelScope.launch {
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
    }

data class CurrentPlayback(
    val itemId: UUID,
    val audioIndex: Int?,
    val subtitleIndex: Int?,
    val mediaSourceId: UUID?,
)
