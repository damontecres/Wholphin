package com.github.damontecres.dolphin.ui.playback

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import java.util.UUID
import javax.inject.Inject

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

        init {
            addCloseable { player.release() }
        }

        fun init(itemId: UUID) {
            viewModelScope.launch(Dispatchers.IO) {
                val response =
                    api.mediaInfoApi
                        .getPostedPlaybackInfo(
                            itemId,
                            // TODO device profile, etc
                            PlaybackInfoDto(
                                startTimeTicks = null,
                                deviceProfile = null,
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
                        withContext(Dispatchers.Main) {
                            stream.value = decision
                            player.setMediaItem(decision.mediaItem)
                            player.prepare()
                        }
                    }
                }
            }
        }
    }
