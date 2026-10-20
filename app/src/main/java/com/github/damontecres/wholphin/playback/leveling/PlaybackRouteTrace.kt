@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.github.damontecres.wholphin.playback.leveling

import android.os.SystemClock
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import com.github.damontecres.wholphin.BuildConfig
import timber.log.Timber

/** Debug-only route timing. No URLs, credentials, buffer contents or per-buffer logging. */
class PlaybackRouteTrace : AnalyticsListener {
    override fun onPlaybackStateChanged(
        eventTime: AnalyticsListener.EventTime,
        state: Int,
    ) = event(eventTime, "state=$state")

    override fun onLoadStarted(
        eventTime: AnalyticsListener.EventTime,
        info: LoadEventInfo,
        data: MediaLoadData,
    ) = event(eventTime, "load-start id=${info.loadTaskId} type=${data.dataType}")

    override fun onLoadCompleted(
        eventTime: AnalyticsListener.EventTime,
        info: LoadEventInfo,
        data: MediaLoadData,
    ) = event(eventTime, "load-end id=${info.loadTaskId} durationMs=${info.loadDurationMs} bytes=${info.bytesLoaded}")

    override fun onAudioTrackInitialized(
        eventTime: AnalyticsListener.EventTime,
        config: AudioSink.AudioTrackConfig,
    ) = event(eventTime, "audio-track encoding=${config.encoding} offload=${config.offload} tunneling=${config.tunneling}")

    override fun onAudioPositionAdvancing(
        eventTime: AnalyticsListener.EventTime,
        playoutStartSystemTimeMs: Long,
    ) = event(eventTime, "audio-advancing")

    override fun onRenderedFirstFrame(
        eventTime: AnalyticsListener.EventTime,
        output: Any,
        renderTimeMs: Long,
    ) = event(eventTime, "first-frame renderMs=$renderTimeMs")

    override fun onAudioDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) = event(eventTime, "audio-decoder=$decoderName initMs=$initializationDurationMs")

    override fun onVideoDecoderInitialized(
        eventTime: AnalyticsListener.EventTime,
        decoderName: String,
        initializedTimestampMs: Long,
        initializationDurationMs: Long,
    ) = event(eventTime, "video-decoder=$decoderName initMs=$initializationDurationMs")

    private fun event(
        time: AnalyticsListener.EventTime,
        detail: String,
    ) = mark("$detail eventMs=${time.realtimeMs} positionMs=${time.currentPlaybackPositionMs}")

    companion object {
        fun mark(detail: String) {
            if (BuildConfig.DEBUG) Timber.tag("VolumeSwitch").d("atMs=${SystemClock.elapsedRealtime()} $detail")
        }
    }
}
