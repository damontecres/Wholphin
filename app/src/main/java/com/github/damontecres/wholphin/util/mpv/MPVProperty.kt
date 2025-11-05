package com.github.damontecres.wholphin.util.mpv

import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvFormat.MPV_FORMAT_DOUBLE
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvFormat.MPV_FORMAT_FLAG
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvFormat.MPV_FORMAT_INT64
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvFormat.MPV_FORMAT_NONE
import com.github.damontecres.wholphin.util.mpv.MPVLib.MpvFormat.MPV_FORMAT_STRING

object MPVProperty {
    const val POSITION = "time-pos"
    const val DURATION = "duration/full"
    const val PAUSED = "pause"
    const val PAUSED_FOR_CACHE = "paused-for-cache"
    const val SPEED = "speed"
    const val TRACK_LIST = "track-list"
    const val ASPECT = "video-params/aspect"
    const val ROTATE = "video-params/rotate"
    const val TRACK_VIDEO = "current-tracks/video/image"
    const val METADATA = "metadata"
    const val HWDEC = "hwdec-current"
    const val MUTE = "mute"
    const val TRACK_AUDIO = "current-tracks/audio/selected"

    val observedProperties =
        mapOf(
            POSITION to MPV_FORMAT_INT64,
            DURATION to MPV_FORMAT_DOUBLE,
            PAUSED to MPV_FORMAT_FLAG,
            PAUSED_FOR_CACHE to MPV_FORMAT_FLAG,
            SPEED to MPV_FORMAT_STRING,
            TRACK_LIST to MPV_FORMAT_NONE,
            ASPECT to MPV_FORMAT_DOUBLE,
            ROTATE to MPV_FORMAT_DOUBLE,
            TRACK_VIDEO to MPV_FORMAT_NONE,
            METADATA to MPV_FORMAT_NONE,
            HWDEC to MPV_FORMAT_NONE,
            MUTE to MPV_FORMAT_FLAG,
            TRACK_AUDIO to MPV_FORMAT_NONE,
        )
}
