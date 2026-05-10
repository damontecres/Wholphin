package com.github.damontecres.wholphin.mpv

import android.content.Context
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import kotlin.time.Duration

/**
 * Stubs out MpvPlayer in wholphin-mpv
 */
@OptIn(UnstableApi::class)
class MpvPlayer(
    private val context: Context,
    private val enableHardwareDecoding: Boolean,
    private val useGpuNext: Boolean,
) : SimpleBasePlayer(Looper.getMainLooper()) {
    override fun getState(): State = throw UnsupportedOperationException("This implementation is non-functional")

    var subtitleDelay: Duration = Duration.ZERO
}
