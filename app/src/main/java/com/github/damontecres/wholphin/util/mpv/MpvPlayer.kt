package com.github.damontecres.wholphin.util.mpv

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.media3.common.AudioAttributes
import androidx.media3.common.BasePlayer
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Clock
import androidx.media3.common.util.ListenerSet
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_EOF
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_ERROR
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_STOP
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEvent.MPV_EVENT_AUDIO_RECONFIG
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEvent.MPV_EVENT_END_FILE
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG
import timber.log.Timber
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds

/**
 * This is barebones implementation of a [Player] which plays content using libmpv
 *
 * It doesn't support every feature or emit every event
 */
@kotlin.OptIn(ExperimentalAtomicApi::class)
@OptIn(UnstableApi::class)
class MpvPlayer(
    private val context: Context,
    enableHardwareDecoding: Boolean,
    useGpuNext: Boolean,
) : BasePlayer(),
    MPVLib.EventObserver,
    TrackSelector.InvalidationListener {
    companion object {
        private const val DEBUG = false
    }

    private var isPaused: Boolean = true
    private var surface: Surface? = null

    private val looper = Util.getCurrentOrMainLooper()
    private val handler = Handler(looper)
    private val listeners =
        ListenerSet<Player.Listener>(
            looper,
            Clock.DEFAULT,
        ) { listener, eventFlags ->
            listener.onEvents(this@MpvPlayer, Player.Events(eventFlags))
        }
    private val availableCommands: Player.Commands
    private val trackSelector = DefaultTrackSelector(context)

    private var mediaItem: MediaItem? = null
    private var startPositionMs: Long = 0L
    private var durationMs: Long = 0L
    private var positionMs: Long = -1L
    private var playbackState: Int = STATE_READY

    @Volatile
    var isReleased = false
        private set

    @Volatile
    private var isLoadingFile = false

    init {
        Timber.v("config-dir=${context.filesDir.path}")
        MPVLib.create(context)
        MPVLib.setOptionString("config", "yes")
        MPVLib.setOptionString("config-dir", context.filesDir.path)

        if (enableHardwareDecoding) {
            MPVLib.setOptionString("hwdec", "mediacodec,mediacodec-copy")
            MPVLib.setOptionString("vo", if (useGpuNext) "gpu-next" else "gpu")
        } else {
            MPVLib.setOptionString("hwdec", "no")
        }
        MPVLib.setOptionString("gpu-context", "android")

        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")

        MPVLib.init()

        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("idle", "yes")
//        MPVLib.setOptionString("sub-fonts-dir", File(context.filesDir, "fonts").absolutePath)

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
        trackSelector.init(this, DefaultBandwidthMeter.getSingletonInstance(context))
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
            if (surface != null) {
                loadFile(it)
            }
        }
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        if (DEBUG) Timber.v("setMediaItems")
        this.startPositionMs = startPositionMs
        setMediaItems(mediaItems.subList(startIndex, mediaItems.size), false)
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
        positionMs = -1L
        playbackState = STATE_READY
    }

    override fun getPlaybackState(): Int {
        if (DEBUG) Timber.v("getPlaybackState")
        return playbackState
    }

    override fun getPlaybackSuppressionReason(): Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE

    override fun getPlayerError(): PlaybackException? {
        TODO("Not yet implemented")
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (isReleased) return
        if (DEBUG) Timber.v("setPlayWhenReady")
        if (playWhenReady) {
            MPVLib.setPropertyBoolean("pause", false)
        } else {
            MPVLib.setPropertyBoolean("pause", true)
        }
        notifyListeners(EVENT_PLAY_WHEN_READY_CHANGED) {
            onPlayWhenReadyChanged(
                playWhenReady,
                PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
            )
        }
    }

    override fun getPlayWhenReady(): Boolean {
        if (DEBUG) Timber.v("getPlayWhenReady")
        if (isReleased) return false
        val isPaused = MPVLib.getPropertyBoolean("pause") ?: this.isPaused
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

    override fun isLoading(): Boolean = isLoadingFile

    override fun getSeekBackIncrement(): Long = 10_000

    override fun getSeekForwardIncrement(): Long = 30_000

    override fun getMaxSeekToPreviousPosition(): Long = 10_000

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        if (DEBUG) Timber.v("setPlaybackParameters")
        MPVLib.setPropertyDouble("speed", playbackParameters.speed.toDouble())
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        if (DEBUG) Timber.v("getPlaybackParameters")
        val speed = MPVLib.getPropertyDouble("speed")?.toFloat() ?: 1f
        return PlaybackParameters(speed)
    }

    override fun stop() {
        if (DEBUG) Timber.v("stop")
        if (isReleased) return
        pause()
        mediaItem = null
        positionMs = -1L
        durationMs = 0L
        playbackState = STATE_IDLE
        notifyListeners(EVENT_IS_PLAYING_CHANGED) { onIsPlayingChanged(false) }
        notifyListeners(EVENT_PLAYBACK_STATE_CHANGED) { onPlaybackStateChanged(STATE_IDLE) }
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
        if (isReleased) return Tracks.EMPTY
        return getTracks()
    }

    override fun getTrackSelectionParameters(): TrackSelectionParameters {
        if (DEBUG) Timber.v("getTrackSelectionParameters")

        return TrackSelectionParameters
            .Builder()
            .build()
    }

    override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {
        Timber.v("TrackSelection: setTrackSelectionParameters %s", parameters)
        if (isReleased) return
        val tracks = getTracks()
        if (C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes) {
            // Subtitles disabled
            Timber.v("TrackSelection: disabling subtitles")
            MPVLib.setPropertyString("sid", "no")
        }
        if (C.TRACK_TYPE_AUDIO in parameters.disabledTrackTypes) {
            // Audio disabled
            Timber.v("TrackSelection: disabling audio")
            MPVLib.setPropertyString("aid", "no")
        }
        Timber.v("TrackSelection: Got ${parameters.overrides.size} overrides")
        parameters.overrides.forEach { (trackGroup, trackSelectionOverride) ->
            val result =
                tracks.groups.firstOrNull { it.mediaTrackGroup == trackGroup }?.let {
                    val id = it.mediaTrackGroup.getFormat(0).id
                    val splits = id?.split(":")
                    val trackId = splits?.getOrNull(1)
                    val propertyName =
                        when (it.mediaTrackGroup.type) {
                            C.TRACK_TYPE_AUDIO -> "aid"
                            C.TRACK_TYPE_VIDEO -> "vid"
                            C.TRACK_TYPE_TEXT -> "sid"
                            else -> null
                        }
                    Timber.v("TrackSelection: activating %s %s '%s'", propertyName, trackId, id)
                    if (trackId != null && propertyName != null) {
                        MPVLib.setPropertyString(propertyName, trackId)
                        true
                    } else {
                        false
                    }
                }
            if (result != true) {
                Timber.w(
                    "Did not find track to select for type=%s, id=%s",
                    trackGroup.type,
                    trackGroup.getFormat(0).id,
                )
            }
        }
    }

    override fun getMediaMetadata(): MediaMetadata {
        if (DEBUG) Timber.v("getMediaMetadata")
        return MediaMetadata.EMPTY
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
        if (DEBUG) Timber.v("getDuration")
        if (isReleased) {
            return durationMs
        }
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
        return currentPosition + totalBufferedDuration
    }

    override fun getTotalBufferedDuration(): Long {
        if (DEBUG) Timber.v("getTotalBufferedDuration")
        if (isReleased) return 0
        return MPVLib.getPropertyDouble("demuxer-cache-duration")?.seconds?.inWholeMilliseconds ?: 0
    }

    override fun isPlayingAd(): Boolean {
        if (DEBUG) Timber.v("isPlayingAd")
        return false
    }

    override fun getCurrentAdGroupIndex(): Int = C.INDEX_UNSET

    override fun getCurrentAdIndexInAdGroup(): Int = C.INDEX_UNSET

    override fun getContentPosition(): Long = currentPosition

    override fun getContentBufferedPosition(): Long = bufferedPosition

    override fun getAudioAttributes(): AudioAttributes = throw UnsupportedOperationException()

    override fun setVolume(volume: Float): Unit = throw UnsupportedOperationException()

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
        if (surface != null) {
            this.surface = surface
            Timber.v("Queued attach")
            MPVLib.attachSurface(surface)
            MPVLib.setOptionString("force-window", "yes")
            Timber.d("Attached surface")
            mediaItem?.let(::loadFile)
            if (mediaItem == null) {
                Timber.w("mediaItem is null in setVideoSurfaceView")
            }
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
        if (DEBUG) Timber.v("getVideoSize")
        if (isReleased) return VideoSize.UNKNOWN
        val width = MPVLib.getPropertyInt("width")
        val height = MPVLib.getPropertyInt("height")
        return if (width != null && height != null) {
            VideoSize(width, height)
        } else {
            VideoSize.UNKNOWN
        }
    }

    override fun getSurfaceSize(): Size = throw UnsupportedOperationException()

    override fun getCurrentCues(): CueGroup = CueGroup.EMPTY_TIME_ZERO

    override fun getDeviceInfo(): DeviceInfo {
        if (DEBUG) Timber.v("getDeviceInfo")
        return DeviceInfo.Builder(DeviceInfo.PLAYBACK_TYPE_REMOTE).build()
    }

    override fun getDeviceVolume(): Int = throw UnsupportedOperationException()

    override fun isDeviceMuted(): Boolean = throw UnsupportedOperationException()

    @Deprecated("Deprecated in Java")
    override fun setDeviceVolume(volume: Int): Unit = throw UnsupportedOperationException()

    override fun setDeviceVolume(
        volume: Int,
        flags: Int,
    ): Unit = throw UnsupportedOperationException()

    @Deprecated("Deprecated in Java")
    override fun increaseDeviceVolume(): Unit = throw UnsupportedOperationException()

    override fun increaseDeviceVolume(flags: Int): Unit = throw UnsupportedOperationException()

    @Deprecated("Deprecated in Java")
    override fun decreaseDeviceVolume(): Unit = throw UnsupportedOperationException()

    override fun decreaseDeviceVolume(flags: Int): Unit = throw UnsupportedOperationException()

    @Deprecated("Deprecated in Java")
    override fun setDeviceMuted(muted: Boolean): Unit = throw UnsupportedOperationException()

    override fun setDeviceMuted(
        muted: Boolean,
        flags: Int,
    ): Unit = throw UnsupportedOperationException()

    override fun setAudioAttributes(
        audioAttributes: AudioAttributes,
        handleAudioFocus: Boolean,
    ): Unit = throw UnsupportedOperationException()

    override fun seekTo(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
        isRepeatingCurrentItem: Boolean,
    ) {
        if (DEBUG) Timber.v("seekTo")
        if (isReleased) {
            Timber.w("seekTo called after release")
            return
        }
        if (mediaItemIndex == C.INDEX_UNSET) {
            return
        }
        MPVLib.setPropertyDouble("time-pos", positionMs / 1000.0)
    }

    override fun eventProperty(property: String) {
        if (DEBUG) Timber.v("eventProperty: $property")
    }

    override fun eventProperty(
        property: String,
        value: Long,
    ) {
        if (DEBUG) Timber.v("eventPropertyLong: $property=$value")
        when (property) {
            MPVProperty.POSITION -> positionMs = value.seconds.inWholeMilliseconds
        }
    }

    override fun eventProperty(
        property: String,
        value: Boolean,
    ) {
        if (DEBUG) Timber.v("eventPropertyBoolean: $property=$value")
        when (property) {
            MPVProperty.PAUSED -> {
                isPaused = value
                notifyListeners(EVENT_IS_PLAYING_CHANGED) { onIsPlayingChanged(!value) }
            }
        }
    }

    override fun eventProperty(
        property: String,
        value: String,
    ) {
        if (DEBUG) Timber.v("eventPropertyString: $property=$value")
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
        when (eventId) {
//            MPV_EVENT_START_FILE -> {
//            }
            MPV_EVENT_FILE_LOADED -> {
                isLoadingFile = false
                notifyListeners(EVENT_IS_LOADING_CHANGED) { onIsLoadingChanged(false) }
                Timber.d("event: MPV_EVENT_FILE_LOADED")
                mediaItem?.let {
                    it.localConfiguration?.subtitleConfigurations?.forEach {
                        val url = it.uri.toString()
                        val title = it.label ?: "External Subtitles"
                        Timber.v("Adding external subtitle track '$title'")
                        MPVLib.command(arrayOf("sub-add", url, "auto", title))
                    }
                }
                notifyListeners(EVENT_RENDERED_FIRST_FRAME) { onRenderedFirstFrame() }
                notifyListeners(EVENT_IS_PLAYING_CHANGED) { onIsPlayingChanged(true) }
                getTracks().let {
                    notifyListeners(EVENT_TRACKS_CHANGED) { onTracksChanged(it) }
                }
            }

            MPV_EVENT_PLAYBACK_RESTART -> {
                Timber.d("event: MPV_EVENT_PLAYBACK_RESTART")
                getTracks().let {
                    notifyListeners(EVENT_TRACKS_CHANGED) { onTracksChanged(it) }
                }
            }

            MPV_EVENT_AUDIO_RECONFIG -> {
                Timber.d("event: MPV_EVENT_AUDIO_RECONFIG")
                getTracks().let {
                    notifyListeners(EVENT_TRACKS_CHANGED) { onTracksChanged(it) }
                }
            }

            MPV_EVENT_VIDEO_RECONFIG -> {
                Timber.d("event: MPV_EVENT_VIDEO_RECONFIG")
                getTracks().let {
                    notifyListeners(EVENT_TRACKS_CHANGED) { onTracksChanged(it) }
                }
            }

            MPV_EVENT_END_FILE -> {
                Timber.d("event: MPV_EVENT_END_FILE")
                // Handled by eventEndFile
            }

            else -> {
                Timber.v("event: $eventId")
            }
        }
    }

    override fun eventEndFile(
        reason: Int,
        error: Int,
    ) {
        Timber.d("MPV_EVENT_END_FILE: %s %s", reason, error)
        notifyListeners(EVENT_IS_PLAYING_CHANGED) { onIsPlayingChanged(false) }
        when (reason) {
            MPV_END_FILE_REASON_EOF -> {
                notifyListeners(EVENT_PLAYBACK_STATE_CHANGED) {
                    onPlaybackStateChanged(STATE_ENDED)
                }
            }

            MPV_END_FILE_REASON_STOP -> {
                // User initiated (eg stop, play next, etc)
            }

            MPV_END_FILE_REASON_ERROR -> {
                Timber.e("libmpv error, error=%s", error)
                notifyListeners(EVENT_PLAYER_ERROR) {
                    onPlayerError(
                        PlaybackException(
                            "libmpv error",
                            null,
                            error,
                        ),
                    )
                }
            }

            else -> {
                // no-op
            }
        }
    }

    private fun loadFile(mediaItem: MediaItem) {
        isLoadingFile = true
        notifyListeners(EVENT_IS_LOADING_CHANGED) { onIsLoadingChanged(true) }
        val url = mediaItem.localConfiguration?.uri.toString()
        if (startPositionMs > 0) {
            MPVLib.command(
                arrayOf(
                    "loadfile",
                    url,
                    "replace",
                    "-1",
                    "start=${startPositionMs / 1000.0}",
                ),
            )
        } else {
            MPVLib.command(arrayOf("loadfile", url, "replace", "-1"))
        }

        MPVLib.setPropertyString("vo", "gpu")
        Timber.d("Called loadfile")
    }

    private fun throwIfReleased() {
        if (isReleased) {
            throw IllegalStateException("Cannot access MpvPlayer after it is released")
        }
    }

    private fun notifyListeners(
        eventId: Int,
        block: Player.Listener.() -> Unit,
    ) {
        handler.post {
            listeners.queueEvent(eventId) {
                block.invoke(it)
            }
            listeners.flushEvents()
        }
    }

    private fun getTracks(): Tracks {
        val trackCount = MPVLib.getPropertyInt("track-list/count") ?: return Tracks.EMPTY
        val groups =
            (0..<trackCount).mapNotNull { idx ->
                val type = MPVLib.getPropertyString("track-list/$idx/type")
                val id = MPVLib.getPropertyInt("track-list/$idx/id")
                val lang = MPVLib.getPropertyString("track-list/$idx/lang")
                val codec = MPVLib.getPropertyString("track-list/$idx/codec")
                val codecDescription = MPVLib.getPropertyString("track-list/$idx/codec-desc")
                val isDefault = MPVLib.getPropertyBoolean("track-list/$idx/default") ?: false
                val isForced = MPVLib.getPropertyBoolean("track-list/$idx/forced") ?: false
                val isExternal = MPVLib.getPropertyBoolean("track-list/$idx/external") ?: false
                val isSelected = MPVLib.getPropertyBoolean("track-list/$idx/selected") ?: false
                val channelCount = MPVLib.getPropertyInt("track-list/$idx/demux-channel-count")
                val title = MPVLib.getPropertyString("track-list/$idx/title")

                if (type != null && id != null) {
                    // TODO do we need the real mimetypes?
                    val mimeType =
                        when (type) {
                            "video" -> MimeTypes.BASE_TYPE_VIDEO + "/todo"
                            "audio" -> MimeTypes.BASE_TYPE_AUDIO + "/todo"
                            "sub" -> MimeTypes.BASE_TYPE_TEXT + "/todo"
                            else -> "unknown/todo"
                        }
                    var flags = 0
                    if (isDefault) flags = flags or C.SELECTION_FLAG_DEFAULT
                    if (isForced) flags = flags or C.SELECTION_FLAG_FORCED
                    val builder =
                        Format
                            .Builder()
                            .setId("$idx:$id")
                            .setCodecs(codec)
                            .setSampleMimeType(mimeType)
                            .setLanguage(lang)
                            .setLabel(listOfNotNull(title, codecDescription).joinToString(","))
                            .setSelectionFlags(flags)
                    if (type == "video" && isSelected) {
                        builder.setWidth(MPVLib.getPropertyInt("width") ?: -1)
                        builder.setHeight(MPVLib.getPropertyInt("height") ?: -1)
                    }
                    channelCount?.let(builder::setChannelCount)
                    val format = builder.build()

                    val trackGroup = TrackGroup(format)
                    val group =
                        Tracks.Group(
                            trackGroup,
                            false,
                            intArrayOf(C.FORMAT_HANDLED),
                            booleanArrayOf(isSelected),
                        )
                    group
                } else {
                    null
                }
            }
        return Tracks(groups)
    }

    override fun onTrackSelectionsInvalidated() {
        // no-op
    }

    var subtitleDelay: Double
        get() {
            if (isReleased) return 0.0
            return MPVLib.getPropertyDouble("sub-delay") ?: 0.0
        }
        set(value) {
            if (isReleased) return
            MPVLib.setPropertyDouble("sub-delay", value)
        }
}

fun MPVLib.setPropertyColor(
    property: String,
    color: Color,
) = MPVLib.setPropertyString(property, color.mpvFormat)

private val Color.mpvFormat: String get() = "$red/$green/$blue/$alpha"
