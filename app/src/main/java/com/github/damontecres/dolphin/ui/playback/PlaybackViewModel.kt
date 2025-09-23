package com.github.damontecres.dolphin.ui.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.github.damontecres.dolphin.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.extensions.ticks
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
    val url: String,
    val type: TranscodeType,
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

        init {
            addCloseable { player.release() }
        }

        fun init(
            destination: Destination.Playback,
            deviceProfile: DeviceProfile,
        ) {
            val itemId = destination.itemId
            val item = destination.item
            if (item != null) {
                title.value = item.name
                val base = item.data
                if (item.type == BaseItemKind.EPISODE) {
                    val season = base.parentIndexNumber?.toString()?.padStart(2, '0')
                    val episode = base.indexNumber?.toString()?.padStart(2, '0')
                    // TODO multi episode support
                    if (season != null && episode != null) {
                        subtitle.value =
                            buildString {
                                if (base.seriesName != null) {
                                    append(base.seriesName)
                                    append(" - ")
                                }
                                append("S${season}E$episode")
                            }
                    }
                }
            }
            viewModelScope.launch(Dispatchers.IO) {
                val response =
                    api.mediaInfoApi
                        .getPostedPlaybackInfo(
                            itemId,
                            // TODO device profile, etc
                            PlaybackInfoDto(
                                startTimeTicks = null,
                                deviceProfile = deviceProfile,
                                enableDirectStream = true,
                                enableDirectPlay = true,
                                maxAudioChannels = null,
                                audioStreamIndex = null,
                                subtitleStreamIndex = null,
                                allowVideoStreamCopy = true,
                                allowAudioStreamCopy = true,
                                autoOpenLiveStream = true,
                                mediaSourceId = null,
                            ),
                        ).content
                if (response.errorCode != null) {
                    // TODO handle error
                    throw Exception("Playback error: ${response.errorCode}")
                } else {
                    val source = response.mediaSources.firstOrNull()
                    source?.let {
                        val mediaUrl =
                            api.videosApi.getVideoStreamUrl(
                                itemId = itemId,
                                mediaSourceId = it.id,
                                static = true,
                                tag = it.eTag,
                                allowAudioStreamCopy = true,
                                allowVideoStreamCopy = true,
                            )
                        val transcodeType =
                            when {
                                it.supportsDirectPlay -> TranscodeType.DIRECT_PLAY
                                it.supportsDirectStream -> TranscodeType.DIRECT_STREAM
                                it.supportsTranscoding -> TranscodeType.TRANSCODE
                                else -> throw Exception("No supported playback method")
                            }
                        val decision = StreamDecision(itemId, mediaUrl, transcodeType)
                        api.playStateApi.reportPlaybackStart(
                            PlaybackStartInfo(
                                itemId = itemId,
                                canSeek = false,
                                isPaused = true,
                                isMuted = false,
                                playMethod =
                                    when {
                                        it.supportsDirectPlay -> PlayMethod.DIRECT_PLAY
                                        it.supportsDirectStream -> PlayMethod.DIRECT_STREAM
                                        it.supportsTranscoding -> PlayMethod.TRANSCODE
                                        else -> throw Exception("No supported playback method")
                                    },
                                repeatMode = RepeatMode.REPEAT_NONE,
                                playbackOrder = PlaybackOrder.DEFAULT,
                            ),
                        )
                        withContext(Dispatchers.Main) {
                            val activityListener =
                                TrackActivityPlaybackListener(api, itemId, player)
                            addCloseable { activityListener.release() }
                            player.addListener(activityListener)
                            duration.value = source.runTimeTicks?.ticks
                            stream.value = decision
                            player.setMediaItem(
                                decision.mediaItem,
                                if (destination.positionMs > 0) destination.positionMs else C.TIME_UNSET,
                            )
                            player.prepare()
                        }
                    }
                }
            }
        }
    }
