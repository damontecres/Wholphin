package com.github.damontecres.wholphin.util.mpv

import android.content.Context
import android.os.Build
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.BasePlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED
import timber.log.Timber
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds

@kotlin.OptIn(ExperimentalAtomicApi::class)
@OptIn(UnstableApi::class)
class MpvPlayer(
    private val context: Context,
    enableHardwareDecoding: Boolean,
) : BasePlayer(),
    MPVLib.EventObserver {
    companion object {
        private const val DEBUG = false
    }

    private var surface: Surface? = null
    private val listeners = mutableListOf<Player.Listener>()
    private val looper = Util.getCurrentOrMainLooper()
    private val availableCommands: Player.Commands
    private lateinit var surfaceSize: Size

    private var mediaItem: MediaItem? = null

    private var startPositionMs: Long = 0L
    private var durationMs: Long = 0L
    private var positionMs: Long = 0L

    var isReleased = false
        private set

    init {
        MPVLib.create(context)
        MPVLib.init()
        if (enableHardwareDecoding) {
            MPVLib.setOptionString("hwdec", "mediacodec,mediacodec-copy")
            MPVLib.setOptionString("vo", "gpu")
        } else {
            MPVLib.setOptionString("hwdec", "no")
        }
        MPVLib.setOptionString("gpu-context", "android")

        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")

        MPVLib.setOptionString("force-window", "no")
        // need to idle at least once for playFile() logic to work
        MPVLib.setOptionString("idle", "once")
        MPVLib.addObserver(this)
        MPVProperty.observedProperties.forEach(MPVLib::observeProperty)

        availableCommands =
            Player.Commands
                .Builder()
                .addAll(
                    COMMAND_PLAY_PAUSE,
                    COMMAND_PREPARE,
                    COMMAND_STOP,
                    COMMAND_SET_SPEED_AND_PITCH,
                    COMMAND_SET_SHUFFLE_MODE,
                    COMMAND_SET_REPEAT_MODE,
                    COMMAND_GET_CURRENT_MEDIA_ITEM,
                    COMMAND_GET_TIMELINE,
                    COMMAND_GET_METADATA,
//                    COMMAND_SET_PLAYLIST_METADATA,
                    COMMAND_SET_MEDIA_ITEM,
//                    COMMAND_CHANGE_MEDIA_ITEMS,
                    COMMAND_GET_TRACKS,
//                    COMMAND_GET_AUDIO_ATTRIBUTES,
//                    COMMAND_SET_AUDIO_ATTRIBUTES,
//                    COMMAND_GET_VOLUME,
//                    COMMAND_SET_VOLUME,
                    COMMAND_SET_VIDEO_SURFACE,
//                    COMMAND_GET_TEXT,
                    COMMAND_RELEASE,
                ).build()
    }

    override fun getApplicationLooper(): Looper = looper

    override fun addListener(listener: Player.Listener) {
        if (DEBUG) Timber.v("addListener")
        listeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        if (DEBUG) Timber.v("removeListener")
        listeners.remove(listener)
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        resetPosition: Boolean,
    ) {
        throwIfReleased()

        if (DEBUG) Timber.v("setMediaItems")
        mediaItems.firstOrNull()?.let {
            mediaItem = it
        }
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        if (DEBUG) Timber.v("setMediaItems")
        setMediaItems(mediaItems.subList(startIndex, mediaItems.size), false)
        this.startPositionMs = startPositionMs
    }

    override fun addMediaItems(
        index: Int,
        mediaItems: List<MediaItem>,
    ): Unit = throw UnsupportedOperationException()

    override fun moveMediaItems(
        fromIndex: Int,
        toIndex: Int,
        newIndex: Int,
    ): Unit = throw UnsupportedOperationException()

    override fun replaceMediaItems(
        fromIndex: Int,
        toIndex: Int,
        mediaItems: List<MediaItem>,
    ): Unit = throw UnsupportedOperationException()

    override fun removeMediaItems(
        fromIndex: Int,
        toIndex: Int,
    ): Unit = throw UnsupportedOperationException()

    override fun getAvailableCommands(): Player.Commands = availableCommands

    override fun prepare() {
        if (DEBUG) Timber.v("prepare")
        durationMs = 0L
        positionMs = 0L
    }

    override fun getPlaybackState(): Int {
        if (DEBUG) Timber.v("getPlaybackState")
        val state = STATE_READY
        return state
    }

    override fun getPlaybackSuppressionReason(): Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE

    override fun getPlayerError(): PlaybackException? {
        TODO("Not yet implemented")
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        throwIfReleased()
        if (DEBUG) Timber.v("setPlayWhenReady")
        if (playWhenReady) {
        } else {
            MPVLib.setPropertyBoolean("pause", true)
        }
    }

    override fun getPlayWhenReady(): Boolean {
        throwIfReleased()
        if (DEBUG) Timber.v("getPlayWhenReady")
        val isPaused = MPVLib.getPropertyBoolean("pause")!!
        return !isPaused
    }

    override fun setRepeatMode(repeatMode: Int) {
        if (DEBUG) Timber.v("setRepeatMode")
    }

    override fun getRepeatMode(): Int = Player.REPEAT_MODE_OFF

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        if (DEBUG) Timber.v("setShuffleModeEnabled")
    }

    override fun getShuffleModeEnabled(): Boolean = false

    override fun isLoading(): Boolean = false

    override fun getSeekBackIncrement(): Long = 10_000

    override fun getSeekForwardIncrement(): Long = 30_000

    override fun getMaxSeekToPreviousPosition(): Long = 10_000

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        if (DEBUG) Timber.v("setPlaybackParameters")
        // TODO
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        if (DEBUG) Timber.v("getPlaybackParameters")
        // TODO
        return PlaybackParameters.DEFAULT
    }

    override fun stop() {
        // TODO
        throwIfReleased()
        if (DEBUG) Timber.v("stop")
        pause()
    }

    override fun release() {
        Timber.i("release")
        if (!isReleased) {
            MPVLib.removeObserver(this)
            clearVideoSurfaceView(null)
            MPVLib.destroy()
        }
        isReleased = true
    }

    override fun getCurrentTracks(): Tracks {
        if (DEBUG) Timber.v("getCurrentTracks")
        // TODO
        return Tracks.EMPTY
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters {
        if (DEBUG) Timber.v("getTrackSelectionParameters")
        // TODO
        return TrackSelectionParameters.Builder().build()
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        if (DEBUG) Timber.v("setTrackSelectionParameters")
        // TODO
    }

    override fun getMediaMetadata(): MediaMetadata {
        if (DEBUG) Timber.v("getMediaMetadata")
        return mediaItem!!.mediaMetadata
    }

    override fun getPlaylistMetadata(): MediaMetadata {
        if (DEBUG) Timber.v("getPlaylistMetadata")
        return MediaMetadata.EMPTY
    }

    override fun setPlaylistMetadata(mediaMetadata: MediaMetadata): Unit = throw UnsupportedOperationException()

    override fun getCurrentTimeline(): Timeline {
        if (DEBUG) Timber.v("getCurrentTimeline")
        // TODO
        return Timeline.EMPTY
    }

    override fun getCurrentPeriodIndex(): Int {
        if (DEBUG) Timber.v("getCurrentPeriodIndex")
        // TODO
        return 0
    }

    override fun getCurrentMediaItemIndex(): Int {
        if (DEBUG) Timber.v("getCurrentMediaItemIndex")
        return 0
    }

    override fun getDuration(): Long {
        throwIfReleased()
        if (DEBUG) Timber.v("getDuration")
        val duration =
            MPVLib.getPropertyDouble("duration/full")?.seconds?.inWholeMilliseconds
                ?: durationMs
        return duration
    }

    override fun getCurrentPosition(): Long {
        if (DEBUG) Timber.v("getCurrentPosition")
        if (isReleased) {
            return positionMs
        }
        val position =
            MPVLib.getPropertyDouble("time-pos/full")?.seconds?.inWholeMilliseconds
                ?: positionMs
        return position
    }

    override fun getBufferedPosition(): Long {
        if (DEBUG) Timber.v("getBufferedPosition")
        // TODO
        return currentPosition
    }

    override fun getTotalBufferedDuration(): Long {
        if (DEBUG) Timber.v("getTotalBufferedDuration")
        return bufferedPosition
    }

    override fun isPlayingAd(): Boolean {
        if (DEBUG) Timber.v("isPlayingAd")
        return false
    }

    override fun getCurrentAdGroupIndex(): Int = C.INDEX_UNSET

    override fun getCurrentAdIndexInAdGroup(): Int = C.INDEX_UNSET

    override fun getContentPosition(): Long = currentPosition

    override fun getContentBufferedPosition(): Long = bufferedPosition

    override fun getAudioAttributes(): AudioAttributes {
        TODO("Not yet implemented")
    }

    override fun setVolume(volume: Float) {
    }

    override fun getVolume(): Float = 1f

    override fun clearVideoSurface(): Unit = throw UnsupportedOperationException()

    override fun clearVideoSurface(surface: Surface?): Unit = throw UnsupportedOperationException()

    override fun setVideoSurface(surface: Surface?): Unit = throw UnsupportedOperationException()

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?): Unit = throw UnsupportedOperationException()

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?): Unit = throw UnsupportedOperationException()

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        throwIfReleased()
        if (DEBUG) Timber.v("setVideoSurfaceView")
        val surface = surfaceView?.holder?.surface
        val newSurfaceSize = if (surface == null) 0 else C.LENGTH_UNSET
        this.surfaceSize = if (surface == null) Size(0, 0) else Size.UNKNOWN
        if (surface != null) {
            this.surface = surface
            Timber.v("Queued attach")
            MPVLib.attachSurface(surface)
            MPVLib.setOptionString("force-window", "yes")
            Timber.d("Attached surface")

            val url = mediaItem!!.localConfiguration?.uri.toString()
            MPVLib.command(arrayOf("loadfile", url))
            MPVLib.setPropertyString("vo", "gpu")
            Timber.d("Called loadfile")
        } else {
            clearVideoSurfaceView(null)
        }
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        Timber.d("clearVideoSurfaceView")
        MPVLib.detachSurface()
        MPVLib.setPropertyString("vo", "null")
        MPVLib.setPropertyString("force-window", "no")
        mediaItem = null
    }

    override fun setVideoTextureView(textureView: TextureView?): Unit = throw UnsupportedOperationException()

    override fun clearVideoTextureView(textureView: TextureView?): Unit = throw UnsupportedOperationException()

    override fun getVideoSize(): VideoSize {
        throwIfReleased()
        if (DEBUG) Timber.v("getVideoSize")
        val width = MPVLib.getPropertyInt("width")
        val height = MPVLib.getPropertyInt("height")
        return if (width != null && height != null) {
            VideoSize(width, height)
        } else {
            VideoSize.UNKNOWN
        }
    }

    override fun getSurfaceSize(): Size {
        TODO("Not yet implemented")
    }

    override fun getCurrentCues(): CueGroup = CueGroup.EMPTY_TIME_ZERO

    override fun getDeviceInfo(): DeviceInfo {
        if (DEBUG) Timber.v("getDeviceInfo")
        // TODO
        return DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
    }

    override fun getDeviceVolume(): Int {
        TODO("Not yet implemented")
    }

    override fun isDeviceMuted(): Boolean {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceVolume(volume: Int) {
        TODO("Not yet implemented")
    }

    override fun setDeviceVolume(
        volume: Int,
        flags: Int,
    ) {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun increaseDeviceVolume() {
        TODO("Not yet implemented")
    }

    override fun increaseDeviceVolume(flags: Int) {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun decreaseDeviceVolume() {
        TODO("Not yet implemented")
    }

    override fun decreaseDeviceVolume(flags: Int) {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated in Java")
    override fun setDeviceMuted(muted: Boolean) {
        TODO("Not yet implemented")
    }

    override fun setDeviceMuted(
        muted: Boolean,
        flags: Int,
    ) {
        TODO("Not yet implemented")
    }

    override fun setAudioAttributes(
        audioAttributes: AudioAttributes,
        handleAudioFocus: Boolean,
    ) {
        TODO("Not yet implemented")
    }

    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
        isRepeatingCurrentItem: Boolean,
    ) {
        throwIfReleased()
        if (DEBUG) Timber.v("seekTo")
        if (mediaItemIndex == C.INDEX_UNSET) {
            return
        }
        MPVLib.setPropertyDouble("time-pos", positionMs / 1000.0)
    }

    override fun eventProperty(property: String) {
        Timber.v("eventProperty: $property")
    }

    override fun eventProperty(
        property: String,
        value: Long,
    ) {
        Timber.v("eventPropertyLong: $property=$value")
        when (property) {
            MPVProperty.POSITION -> positionMs = value.seconds.inWholeMilliseconds
        }
    }

    override fun eventProperty(
        property: String,
        value: Boolean,
    ) {
        Timber.v("eventPropertyBoolean: $property=$value")
    }

    override fun eventProperty(
        property: String,
        value: String,
    ) {
        Timber.v("eventPropertyString: $property=$value")
    }

    override fun eventProperty(
        property: String,
        value: Double,
    ) {
        Timber.v("eventPropertyDouble: $property=$value")
        when (property) {
            MPVProperty.DURATION -> durationMs = value.seconds.inWholeMilliseconds
        }
    }

    override fun event(eventId: Int) {
        Timber.v("event: $eventId")
        when (eventId) {
            MPV_EVENT_FILE_LOADED -> {
                Timber.d("Seeking to $startPositionMs")
                if (startPositionMs > 0) {
                    seekTo(startPositionMs)
                }
            }
        }
    }

    private fun throwIfReleased() {
        if (isReleased) {
            throw IllegalStateException("Cannot access MpvPlayer after it is released")
        }
    }
}
