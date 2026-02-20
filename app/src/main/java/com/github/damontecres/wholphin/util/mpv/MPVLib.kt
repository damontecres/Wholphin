package com.github.damontecres.wholphin.util.mpv

/*
This file is copied from https://github.com/mpv-android/mpv-android/blob/master/app/src/main/java/is/xyz/mpv/MPVLib.kt

Copyright (c) 2016 Ilya Zhuravlev
Copyright (c) 2016 sfan5 <sfan5@live.de>

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
associated documentation files (the "Software"), to deal in the Software without restriction,
including without limitation the rights to use, copy, modify, merge, publish, distribute,
sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import java.util.concurrent.CopyOnWriteArrayList

// Wrapper for native library

@Suppress("unused")
object MPVLib {
    init {
        val libs = arrayOf("mpv", "player")
        for (lib in libs) {
            System.loadLibrary(lib)
        }
    }

    external fun create(appctx: Context)

    external fun init()

    external fun destroy()

    external fun attachSurface(surface: Surface)

    external fun detachSurface()

    external fun command(cmd: Array<out String>)

    external fun setOptionString(
        name: String,
        value: String,
    ): Int

    external fun grabThumbnail(dimension: Int): Bitmap?

    external fun getPropertyInt(property: String): Int?

    external fun setPropertyInt(
        property: String,
        value: Int,
    )

    external fun getPropertyDouble(property: String): Double?

    external fun setPropertyDouble(
        property: String,
        value: Double,
    )

    external fun getPropertyBoolean(property: String): Boolean?

    external fun setPropertyBoolean(
        property: String,
        value: Boolean,
    )

    external fun getPropertyString(property: String): String?

    external fun setPropertyString(
        property: String,
        value: String,
    )

    external fun observeProperty(
        property: String,
        format: Int,
    )

    private val observers = CopyOnWriteArrayList<EventObserver>()

    @JvmStatic
    fun addObserver(o: EventObserver) {
        observers.add(o)
    }

    @JvmStatic
    fun removeObserver(o: EventObserver) {
        observers.remove(o)
    }

    @JvmStatic
    fun eventProperty(
        property: String,
        value: Long,
    ) {
        for (o in observers) {
            o.eventProperty(property, value)
        }
    }

    @JvmStatic
    fun eventProperty(
        property: String,
        value: Boolean,
    ) {
        for (o in observers) {
            o.eventProperty(property, value)
        }
    }

    @JvmStatic
    fun eventProperty(
        property: String,
        value: Double,
    ) {
        for (o in observers) {
            o.eventProperty(property, value)
        }
    }

    @JvmStatic
    fun eventProperty(
        property: String,
        value: String,
    ) {
        for (o in observers) {
            o.eventProperty(property, value)
        }
    }

    @JvmStatic
    fun eventProperty(property: String) {
        for (o in observers) {
            o.eventProperty(property)
        }
    }

    @JvmStatic
    fun event(eventId: Int) {
        for (o in observers) {
            o.event(eventId)
        }
    }

    @JvmStatic
    fun eventEndFile(
        reason: Int,
        error: Int,
    ) {
        for (o in observers) {
            o.eventEndFile(reason, error)
        }
    }

    private val log_observers = CopyOnWriteArrayList<LogObserver>()

    @JvmStatic
    fun addLogObserver(o: LogObserver) {
        log_observers.add(o)
    }

    @JvmStatic
    fun removeLogObserver(o: LogObserver) {
        log_observers.remove(o)
    }

    @JvmStatic
    fun logMessage(
        prefix: String,
        level: Int,
        text: String,
    ) {
        for (o in log_observers) {
            o.logMessage(prefix, level, text)
        }
    }

    interface EventObserver {
        fun eventProperty(property: String)

        fun eventProperty(
            property: String,
            value: Long,
        )

        fun eventProperty(
            property: String,
            value: Boolean,
        )

        fun eventProperty(
            property: String,
            value: String,
        )

        fun eventProperty(
            property: String,
            value: Double,
        )

        fun event(eventId: Int)

        fun eventEndFile(
            reason: Int,
            error: Int,
        )
    }

    interface LogObserver {
        fun logMessage(
            prefix: String,
            level: Int,
            text: String,
        )
    }

    object MpvFormat {
        const val MPV_FORMAT_NONE: Int = 0
        const val MPV_FORMAT_STRING: Int = 1
        const val MPV_FORMAT_OSD_STRING: Int = 2
        const val MPV_FORMAT_FLAG: Int = 3
        const val MPV_FORMAT_INT64: Int = 4
        const val MPV_FORMAT_DOUBLE: Int = 5
        const val MPV_FORMAT_NODE: Int = 6
        const val MPV_FORMAT_NODE_ARRAY: Int = 7
        const val MPV_FORMAT_NODE_MAP: Int = 8
        const val MPV_FORMAT_BYTE_ARRAY: Int = 9
    }

    object MpvEvent {
        const val MPV_EVENT_NONE: Int = 0
        const val MPV_EVENT_SHUTDOWN: Int = 1
        const val MPV_EVENT_LOG_MESSAGE: Int = 2
        const val MPV_EVENT_GET_PROPERTY_REPLY: Int = 3
        const val MPV_EVENT_SET_PROPERTY_REPLY: Int = 4
        const val MPV_EVENT_COMMAND_REPLY: Int = 5
        const val MPV_EVENT_START_FILE: Int = 6
        const val MPV_EVENT_END_FILE: Int = 7
        const val MPV_EVENT_FILE_LOADED: Int = 8

        @Deprecated("")
        const val MPV_EVENT_IDLE: Int = 11

        @Deprecated("")
        const val MPV_EVENT_TICK: Int = 14
        const val MPV_EVENT_CLIENT_MESSAGE: Int = 16
        const val MPV_EVENT_VIDEO_RECONFIG: Int = 17
        const val MPV_EVENT_AUDIO_RECONFIG: Int = 18
        const val MPV_EVENT_SEEK: Int = 20
        const val MPV_EVENT_PLAYBACK_RESTART: Int = 21
        const val MPV_EVENT_PROPERTY_CHANGE: Int = 22
        const val MPV_EVENT_QUEUE_OVERFLOW: Int = 24
        const val MPV_EVENT_HOOK: Int = 25
    }

    object MpvLogLevel {
        const val MPV_LOG_LEVEL_NONE: Int = 0
        const val MPV_LOG_LEVEL_FATAL: Int = 10
        const val MPV_LOG_LEVEL_ERROR: Int = 20
        const val MPV_LOG_LEVEL_WARN: Int = 30
        const val MPV_LOG_LEVEL_INFO: Int = 40
        const val MPV_LOG_LEVEL_V: Int = 50
        const val MPV_LOG_LEVEL_DEBUG: Int = 60
        const val MPV_LOG_LEVEL_TRACE: Int = 70
    }

    object MpvEndFileReason {
        const val MPV_END_FILE_REASON_EOF: Int = 0
        const val MPV_END_FILE_REASON_STOP: Int = 2
        const val MPV_END_FILE_REASON_QUIT: Int = 3
        const val MPV_END_FILE_REASON_ERROR: Int = 4
        const val MPV_END_FILE_REASON_REDIRECT: Int = 5
    }

    object MpvError {
        const val MPV_ERROR_SUCCESS = 0
        const val MPV_ERROR_EVENT_QUEUE_FULL = -1
        const val MPV_ERROR_NOMEM = -2
        const val MPV_ERROR_UNINITIALIZED = -3
        const val MPV_ERROR_INVALID_PARAMETER = -4
        const val MPV_ERROR_OPTION_NOT_FOUND = -5
        const val MPV_ERROR_OPTION_FORMAT = -6
        const val MPV_ERROR_OPTION_ERROR = -7
        const val MPV_ERROR_PROPERTY_NOT_FOUND = -8
        const val MPV_ERROR_PROPERTY_FORMAT = -9
        const val MPV_ERROR_PROPERTY_UNAVAILABLE = -10
        const val MPV_ERROR_PROPERTY_ERROR = -11
        const val MPV_ERROR_COMMAND = -12
        const val MPV_ERROR_LOADING_FAILED = -13
        const val MPV_ERROR_AO_INIT_FAILED = -14
        const val MPV_ERROR_VO_INIT_FAILED = -15
        const val MPV_ERROR_NOTHING_TO_PLAY = -16
        const val MPV_ERROR_UNKNOWN_FORMAT = -17
        const val MPV_ERROR_UNSUPPORTED = -18
        const val MPV_ERROR_NOT_IMPLEMENTED = -19
        const val MPV_ERROR_GENERIC = -20
    }
}
