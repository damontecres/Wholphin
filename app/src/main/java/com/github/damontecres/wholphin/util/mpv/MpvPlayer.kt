package com.github.damontecres.wholphin.util.mpv

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.os.Process
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
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_EOF
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_ERROR
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEndFileReason.MPV_END_FILE_REASON_STOP
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEvent.MPV_EVENT_AUDIO_RECONFIG
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEvent.MPV_EVENT_END_FILE
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEvent.MPV_EVENT_FILE_LOADED
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEvent.MPV_EVENT_PLAYBACK_RESTART
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEvent.MPV_EVENT_START_FILE
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvEvent.MPV_EVENT_VIDEO_RECONFIG
import timber.log.Timber
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.update
import kotlin.time.Duration
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
    private val enableHardwareDecoding: Boolean,
    private val useGpuNext: Boolean,
) : BasePlayer(),
    MPVLib.EventObserver,
    TrackSelector.InvalidationListener,
    Handler.Callback {
    companion object {
        private const val DEBUG = false
    }

    private var surface: Surface? = null
    private val playbackState = AtomicReference<PlaybackState>(PlaybackState.EMPTY)
    private val mpvLogger = MpvLogger()

    // This looper will sent events to the main thread
    private val looper = Util.getCurrentOrMainLooper()
    private val mainHandler = Handler(looper)
    private val listeners =
        ListenerSet<Player.Listener>(
            looper,
            Clock.DEFAULT,
        ) { listener, eventFlags ->
            listener.onEvents(this@MpvPlayer, Player.Events(eventFlags))
        }
    private var availableCommands: Player.Commands = Player.Commands.Builder().build()
    private val trackSelector = DefaultTrackSelector(context)

    // This thread/looper will receive commands from the main thread to execute
    private val thread: HandlerThread =
        HandlerThread("MpvPlayer:Playback", Process.THREAD_PRIORITY_AUDIO)
            .also { it.start() }
    private val internalHandler: Handler = Handler(thread.looper, this)

    @Volatile
    var isReleased = false
        private set

    init {
        sendCommand(MpvCommand.INITIALIZE, null)
    }

    private fun init() {
        Timber.v("config-dir=${context.filesDir.path}")
        MPVLib.addLogObserver(mpvLogger)

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

        MPVLib.initialize()

        MPVLib.setOptionString("force-window", "no")
        MPVLib.setOptionString("idle", "yes")
//        MPVLib.setOptionString("sub-fonts-dir", File(context.filesDir, "fonts").absolutePath)

        MPVLib.addObserver(this@MpvPlayer)
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
        if (DEBUG) Timber.v("setMediaItems")
        throwIfReleased()
        mediaItems.firstOrNull()?.let {
            if (surface != null) {
                sendCommand(MpvCommand.LOAD_FILE, MediaAndPosition(it, C.TIME_UNSET))
            }
        }
    }

    override fun setMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ) {
        if (DEBUG) Timber.v("setMediaItems")
        throwIfReleased()
        sendCommand(
            MpvCommand.LOAD_FILE,
            MediaAndPosition(mediaItems[startIndex], startPositionMs),
        )
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
        playbackState.update {
            it.copy(
                state = Player.STATE_READY,
            )
        }
    }

    override fun getPlaybackState(): Int {
        if (DEBUG) Timber.v("getPlaybackState")
        return playbackState.load().state
    }

    override fun getPlaybackSuppressionReason(): Int = PLAYBACK_SUPPRESSION_REASON_NONE

    override fun getPlayerError(): PlaybackException? {
        TODO("Not yet implemented")
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (isReleased) return
        if (DEBUG) Timber.v("setPlayWhenReady: $playWhenReady")
        sendCommand(MpvCommand.PLAY_PAUSE, playWhenReady)
    }

    override fun getPlayWhenReady(): Boolean {
        if (DEBUG) Timber.v("getPlayWhenReady")
        if (isReleased) return false
        return !playbackState.load().isPaused
    }

    override fun setRepeatMode(repeatMode: Int) {
        if (DEBUG) Timber.v("setRepeatMode")
    }

    override fun getRepeatMode(): Int = REPEAT_MODE_OFF

    override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
        if (DEBUG) Timber.v("setShuffleModeEnabled")
    }

    override fun getShuffleModeEnabled(): Boolean = false

    override fun isLoading(): Boolean = playbackState.load().isLoadingFile

    override fun getSeekBackIncrement(): Long = 10_000

    override fun getSeekForwardIncrement(): Long = 30_000

    override fun getMaxSeekToPreviousPosition(): Long = 10_000

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        if (DEBUG) Timber.v("setPlaybackParameters")
        sendCommand(MpvCommand.SET_SPEED, playbackParameters.speed)
    }

    override fun getPlaybackParameters(): PlaybackParameters {
        if (DEBUG) Timber.v("getPlaybackParameters")
        return PlaybackParameters(playbackState.load().speed)
    }

    override fun stop() {
        if (DEBUG) Timber.v("stop")
        if (isReleased) return
        pause()
        internalHandler.removeCallbacks(updatePlaybackState)
        playbackState.update {
            PlaybackState.EMPTY
        }
        notifyListeners(EVENT_IS_PLAYING_CHANGED) { onIsPlayingChanged(false) }
        notifyListeners(EVENT_PLAYBACK_STATE_CHANGED) { onPlaybackStateChanged(STATE_IDLE) }
    }

    override fun release() {
        Timber.i("release")
        playbackState.update {
            PlaybackState.EMPTY
        }
        if (!isReleased) {
            internalHandler.removeCallbacks(updatePlaybackState)
            MPVLib.removeObserver(this@MpvPlayer)
            sendCommand(MpvCommand.DESTROY, null)
            thread.quitSafely()
        }
        isReleased = true
    }

    override fun getCurrentTracks(): Tracks {
        if (DEBUG) Timber.v("getCurrentTracks")
        if (isReleased) return Tracks.EMPTY
        return playbackState.load().tracks
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
        val tracks = playbackState.load().tracks
        if (C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes) {
            // Subtitles disabled
            Timber.v("TrackSelection: disabling subtitles")
            sendCommand(MpvCommand.SET_TRACK_SELECTION, TrackSelection("sid", "no"))
        }
        if (C.TRACK_TYPE_AUDIO in parameters.disabledTrackTypes) {
            // Audio disabled
            Timber.v("TrackSelection: disabling audio")
            sendCommand(MpvCommand.SET_TRACK_SELECTION, TrackSelection("aid", "no"))
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
                        sendCommand(
                            MpvCommand.SET_TRACK_SELECTION,
                            TrackSelection(propertyName, trackId),
                        )
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
        return playbackState.load().durationMs
    }

    override fun getCurrentPosition(): Long {
        if (DEBUG) Timber.v("getCurrentPosition")
        val state = playbackState.load()
        return state.positionMs
    }

    override fun getBufferedPosition(): Long {
        if (DEBUG) Timber.v("getBufferedPosition")
        return currentPosition + totalBufferedDuration
    }

    override fun getTotalBufferedDuration(): Long {
        if (DEBUG) Timber.v("getTotalBufferedDuration")
        if (isReleased) return 0
        return playbackState.load().bufferMs
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

    override fun mute() {
        volume = 0f
    }

    override fun unmute() {
        volume = 1f
    }

    override fun clearVideoSurface(): Unit = throw UnsupportedOperationException()

    override fun clearVideoSurface(surface: Surface?): Unit = throw UnsupportedOperationException()

    override fun setVideoSurface(surface: Surface?): Unit = throw UnsupportedOperationException()

    override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?): Unit = throw UnsupportedOperationException()

    override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?): Unit = throw UnsupportedOperationException()

    override fun setVideoSurfaceView(surfaceView: SurfaceView?) {
        throwIfReleased()
        if (DEBUG) Timber.v("setVideoSurfaceView")
        val surface = surfaceView?.holder?.surface
        if (surface != null && surface.isValid) {
            Timber.v("Queued attach")
            sendCommand(MpvCommand.ATTACH_SURFACE, surface)
        } else {
            clearVideoSurfaceView(null)
        }
    }

    override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {
        if (surface == surfaceView?.holder?.surface) {
            Timber.d("clearVideoSurfaceView")
            sendCommand(MpvCommand.ATTACH_SURFACE, null)
        } else {
            Timber.w("clearVideoSurfaceView called with different surface")
        }
    }

    override fun setVideoTextureView(textureView: TextureView?): Unit = throw UnsupportedOperationException()

    override fun clearVideoTextureView(textureView: TextureView?): Unit = throw UnsupportedOperationException()

    override fun getVideoSize(): VideoSize {
        if (DEBUG) Timber.v("getVideoSize")
        if (isReleased) return VideoSize.UNKNOWN
        return playbackState.load().videoSize
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
        sendCommand(MpvCommand.SEEK, positionMs)
    }

    override fun onTrackSelectionsInvalidated() {
        // no-op
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
            MPVProperty.POSITION -> {
                playbackState.update {
                    it.copy(positionMs = value.seconds.inWholeMilliseconds)
                }
            }
        }
    }

    override fun eventProperty(
        property: String,
        value: Boolean,
    ) {
        if (DEBUG) Timber.v("eventPropertyBoolean: $property=$value")
        when (property) {
            MPVProperty.PAUSED -> {
                playbackState.update {
                    it.copy(isPaused = value)
                }
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
            MPVProperty.DURATION -> {
                playbackState.update {
                    it.copy(durationMs = value.seconds.inWholeMilliseconds)
                }
            }
        }
    }

    override fun event(eventId: Int) {
        Timber.v("event: eventId=%s", eventId)
        when (eventId) {
            MPV_EVENT_START_FILE -> {
                internalHandler.post(updatePlaybackState)
            }

            MPV_EVENT_FILE_LOADED -> {
                playbackState.update {
                    it.copy(isLoadingFile = false)
                }
                notifyListeners(EVENT_IS_LOADING_CHANGED) { onIsLoadingChanged(false) }
                Timber.d("event: MPV_EVENT_FILE_LOADED")
                internalHandler.post(updatePlaybackState)
                playbackState.load().media?.mediaItem?.let { media ->
                    media.localConfiguration?.subtitleConfigurations?.forEach {
                        val url = it.uri.toString()
                        val title = it.label ?: "External Subtitles"
                        Timber.v("Adding external subtitle track '$title'")
                        if (it.language.isNotNullOrBlank()) {
                            MPVLib.command(arrayOf("sub-add", url, "auto", title, it.language!!))
                        } else {
                            MPVLib.command(arrayOf("sub-add", url, "auto", title))
                        }
                    }
                }
                notifyListeners(EVENT_RENDERED_FIRST_FRAME) { onRenderedFirstFrame() }
                notifyListeners(EVENT_IS_PLAYING_CHANGED) { onIsPlayingChanged(true) }
                updateTracksAndNotify()
            }

            MPV_EVENT_PLAYBACK_RESTART -> {
                Timber.d("event: MPV_EVENT_PLAYBACK_RESTART")
                updateTracksAndNotify()
            }

            MPV_EVENT_AUDIO_RECONFIG -> {
                Timber.d("event: MPV_EVENT_AUDIO_RECONFIG")
                updateTracksAndNotify()
            }

            MPV_EVENT_VIDEO_RECONFIG -> {
                Timber.d("event: MPV_EVENT_VIDEO_RECONFIG")
                updateTracksAndNotify()
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
                playbackState.update {
                    it.copy(state = Player.STATE_ENDED)
                }
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

    private fun updateTracksAndNotify() {
        val tracks = createTracks()
        playbackState.update {
            it.copy(tracks = tracks)
        }
        notifyListeners(EVENT_TRACKS_CHANGED) { onTracksChanged(tracks) }
    }

    private fun loadFile(media: MediaAndPosition) {
        Timber.v("loadFile: media=$media")
        playbackState.update {
            it.copy(
                isLoadingFile = true,
                media = media,
            )
        }
        notifyListeners(EVENT_IS_LOADING_CHANGED) { onIsLoadingChanged(true) }
        val url =
            media.mediaItem.localConfiguration
                ?.uri
                .toString()
        if (media.startPositionMs > 0) {
            MPVLib.command(
                arrayOf(
                    "loadfile",
                    url,
                    "replace",
                    "-1",
                    "start=${media.startPositionMs / 1000.0}",
                ),
            )
        } else {
            MPVLib.command(arrayOf("loadfile", url, "replace", "-1"))
        }

        if (enableHardwareDecoding) {
            MPVLib.setOptionString("vo", if (useGpuNext) "gpu-next" else "gpu")
        }
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
        mainHandler.post {
            listeners.queueEvent(eventId) {
                block.invoke(it)
            }
            listeners.flushEvents()
        }
    }

    var subtitleDelaySeconds: Double
        get() {
            if (isReleased) return 0.0
            return playbackState.load().subtitleDelay
        }
        set(value) {
            if (isReleased) return
            sendCommand(MpvCommand.SET_SUBTITLE_DELAY, value)
        }

    var subtitleDelay: Duration
        get() {
            if (isReleased) return Duration.ZERO
            return subtitleDelaySeconds.seconds
        }
        set(value) {
            if (isReleased) return
            subtitleDelaySeconds = value.inWholeMilliseconds / 1000.0
        }

    private val updatePlaybackState: Runnable =
        Runnable {
            if (playbackState.load().media == null) {
                return@Runnable
            }
            val positionMs =
                MPVLib.getPropertyDouble("time-pos/full")?.seconds?.inWholeMilliseconds
                    ?: C.TIME_UNSET
            val bufferMs =
                MPVLib.getPropertyDouble("demuxer-cache-duration")?.seconds?.inWholeMilliseconds
                    ?: C.TIME_UNSET
            val durationMs =
                MPVLib.getPropertyDouble("duration/full")?.seconds?.inWholeMilliseconds
                    ?: C.TIME_UNSET
            val speed = MPVLib.getPropertyDouble("speed")?.toFloat() ?: 1f
            val paused = MPVLib.getPropertyBoolean("pause") ?: false
            val width = MPVLib.getPropertyInt("width")
            val height = MPVLib.getPropertyInt("height")
            val videoSize =
                if (width != null && height != null) {
                    VideoSize(width, height)
                } else {
                    VideoSize.UNKNOWN
                }

            playbackState.update {
                it.copy(
                    timestamp = System.currentTimeMillis(),
                    positionMs = positionMs,
                    bufferMs = bufferMs,
                    durationMs = durationMs,
                    speed = speed,
                    isPaused = paused,
                    videoSize = videoSize,
                )
            }
        }

    private fun sendCommand(
        cmd: MpvCommand,
        obj: Any?,
    ) {
        internalHandler.obtainMessage(cmd.ordinal, obj).sendToTarget()
    }

    override fun handleMessage(msg: Message): Boolean {
        val cmd = MpvCommand.entries[msg.what]
        Timber.v("handleMessage: cmd=$cmd")
        when (cmd) {
            MpvCommand.PLAY_PAUSE -> {
                val playWhenReady = msg.obj as Boolean
                MPVLib.setPropertyBoolean("pause", !playWhenReady)
                playbackState.update {
                    it.copy(isPaused = !playWhenReady)
                }
                notifyListeners(EVENT_PLAY_WHEN_READY_CHANGED) {
                    onPlayWhenReadyChanged(
                        playWhenReady,
                        PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST,
                    )
                }
            }

            MpvCommand.SET_TRACK_SELECTION -> {
                val (propertyName, trackId) = msg.obj as TrackSelection
                MPVLib.setPropertyString(propertyName, trackId)
                updateTracksAndNotify()
            }

            MpvCommand.SEEK -> {
                val positionMs = msg.obj as Long
                MPVLib.setPropertyDouble("time-pos", positionMs / 1000.0)
                playbackState.update {
                    it.copy(positionMs = positionMs)
                }
            }

            MpvCommand.SET_SPEED -> {
                val value = msg.obj as Float
                MPVLib.setPropertyDouble("speed", value.toDouble())
                playbackState.update {
                    it.copy(speed = value)
                }
            }

            MpvCommand.SET_SUBTITLE_DELAY -> {
                val value = msg.obj as Double
                MPVLib.setPropertyDouble("sub-delay", value)
                playbackState.update {
                    it.copy(subtitleDelay = value)
                }
            }

            MpvCommand.LOAD_FILE -> {
                loadFile(msg.obj as MediaAndPosition)
            }

            MpvCommand.ATTACH_SURFACE -> {
                val surface = msg.obj as Surface?
                if (surface == null || (this.surface != null && this.surface != surface)) {
                    // If clearing or changing the surface
                    MPVLib.detachSurface()
                    MPVLib.setPropertyString("vo", "null")
                    MPVLib.setPropertyString("force-window", "no")
                }
                if (surface != null) {
                    MPVLib.attachSurface(surface)
                    this.surface = surface
                    MPVLib.setOptionString("force-window", "yes")
                    Timber.d("Attached surface")
                }
            }

            MpvCommand.INITIALIZE -> {
                init()
            }

            MpvCommand.DESTROY -> {
                clearVideoSurfaceView(null)
                MPVLib.removeLogObserver(mpvLogger)
                MPVLib.tearDown()
                Timber.d("MPVLib destroyed")
            }
        }
        return true
    }
}

fun MPVLib.setPropertyColor(
    property: String,
    color: Color,
) = setPropertyString(property, color.mpvFormat)

private val Color.mpvFormat: String get() = "$red/$green/$blue/$alpha"

@OptIn(UnstableApi::class)
private fun createTracks(): Tracks {
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
                        .apply {
                            if (isExternal) {
                                setId("$idx:e:$id")
                            } else {
                                setId("$idx:$id")
                            }
                        }.setCodecs(codec)
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
//                Timber.v("$idx=$format")

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

private data class PlaybackState(
    val timestamp: Long,
    val isLoadingFile: Boolean,
    val media: MediaAndPosition?,
    val positionMs: Long,
    val bufferMs: Long,
    val durationMs: Long,
    val isPaused: Boolean,
    val speed: Float,
    val subtitleDelay: Double,
    val videoSize: VideoSize,
    @param:Player.State val state: Int,
    val tracks: Tracks,
) {
    companion object {
        val EMPTY =
            PlaybackState(
                timestamp = C.TIME_UNSET,
                isLoadingFile = false,
                media = null,
                positionMs = C.TIME_UNSET,
                durationMs = C.TIME_UNSET,
                tracks = Tracks.EMPTY,
                bufferMs = C.TIME_UNSET,
                isPaused = false,
                speed = 1f,
                videoSize = VideoSize.UNKNOWN,
                state = Player.STATE_IDLE,
                subtitleDelay = 0.0,
            )
    }
}

private data class TrackSelection(
    val property: String,
    val trackId: String,
)

private data class MediaAndPosition(
    val mediaItem: MediaItem,
    val startPositionMs: Long,
)

enum class MpvCommand {
    PLAY_PAUSE,
    SEEK,
    SET_TRACK_SELECTION,
    SET_SPEED,
    SET_SUBTITLE_DELAY,
    LOAD_FILE,
    ATTACH_SURFACE,
    INITIALIZE,
    DESTROY,
}
