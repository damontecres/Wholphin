package com.github.damontecres.wholphin.util

import android.app.ActivityManager
import android.content.Context
import androidx.media3.common.C

/**
 * Picks how many bytes ExoPlayer may hold in its buffer.
 *
 * ExoPlayer's default target is about 140 MiB for a video with audio, regardless of bitrate. On a
 * device with around 2 GiB of RAM, that plus the 4K video surfaces leaves so little free memory
 * that the low memory killer ends up killing the app in the foreground during high bitrate
 * playback (e.g. a 60 Mbps UHD remux on a SHIELD TV 2019 tube).
 */
object PlayerBufferSize {
    /** The setting's value for "pick from the device's memory" */
    const val AUTO = 0

    /** Range of the manual setting: from the low memory size up to about ExoPlayer's default (~138 MiB) */
    const val MANUAL_MIN_MB = 64
    const val MANUAL_MAX_MB = 144
    const val MANUAL_STEP_MB = 16

    /** Devices with less total RAM than this get [LOW_MEMORY_BUFFER_MB] */
    const val LOW_MEMORY_DEVICE_BYTES = 2_560L * 1024 * 1024

    const val LOW_MEMORY_BUFFER_MB = 64

    private const val MIB = 1024 * 1024

    /**
     * @param settingMb the user's setting, [AUTO] or a size in MiB (clamped to the manual range)
     * @param deviceTotalMemBytes the device's total RAM
     * @param isLowRamDevice whether Android reports the device as low RAM
     * @return the buffer target in bytes, or [C.LENGTH_UNSET] for ExoPlayer's default
     */
    fun targetBufferBytes(
        settingMb: Int,
        deviceTotalMemBytes: Long,
        isLowRamDevice: Boolean,
    ): Int {
        val lowMemory = isLowRamDevice || deviceTotalMemBytes in 1 until LOW_MEMORY_DEVICE_BYTES
        return when {
            settingMb > 0 -> settingMb.coerceIn(MANUAL_MIN_MB, MANUAL_MAX_MB) * MIB
            lowMemory -> LOW_MEMORY_BUFFER_MB * MIB
            else -> C.LENGTH_UNSET
        }
    }

    fun targetBufferBytes(
        context: Context,
        settingMb: Int,
    ): Int {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        return targetBufferBytes(
            settingMb,
            memoryInfo.totalMem,
            activityManager?.isLowRamDevice ?: false,
        )
    }
}
