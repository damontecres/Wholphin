package com.github.damontecres.wholphin.services

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.github.damontecres.wholphin.MainActivity
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.seconds

/**
 * Handles determining whether the refresh rate and/or resolution of the display need to changed when playing media
 */
@Singleton
class RefreshRateService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /**
         * Find the best display mode for the given stream and signal to change to it
         */
        suspend fun changeRefreshRate(
            stream: MediaStream,
            switchRefreshRate: Boolean,
            switchResolution: Boolean,
        ) = withContext(WholphinDispatchers.IO) {
            if (!switchRefreshRate && !switchResolution) {
                Timber.v("Not switching either refresh rate nor resolution")
                return@withContext
            }
            val displayManager =
                MainActivity.instance.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            val displayModes =
                display.supportedModes
                    .orEmpty()
                    .map { DisplayMode(it) }
                    .sortedWith(
                        compareByDescending<DisplayMode>({ it.physicalWidth * it.physicalHeight })
                            .thenBy { it.refreshRateRounded },
                    )

            val currentDisplayMode = display.mode
            require(stream.type == MediaStreamType.VIDEO) { "Stream is not video" }
            val width = stream.width
            val height = stream.height
            val frameRate =
                if (switchRefreshRate) stream.realFrameRate else currentDisplayMode.refreshRate
            if (width == null || height == null || frameRate == null) {
                Timber.w("Video stream missing required info: width=%s, height=%s, frameRate=%s", width, height, frameRate)
                return@withContext
            }
            Timber.d("Getting refresh rate for: width=%s, height=%s, frameRate=%s", width, height, frameRate)
            val targetMode =
                findDisplayMode(
                    displayModes = displayModes,
                    streamWidth = width,
                    streamHeight = height,
                    targetFrameRate = frameRate,
                    refreshRateSwitch = switchRefreshRate,
                    resolutionSwitch = switchResolution,
                )
            Timber.i("Found display mode: %s, current=%s", targetMode, currentDisplayMode)
            if (targetMode != null && targetMode.modeId != currentDisplayMode.modeId) {
                val listener = Listener(display.displayId)
                displayManager.registerDisplayListener(
                    listener,
                    Handler(Looper.myLooper() ?: Looper.getMainLooper()),
                )
                try {
                    MainActivity.instance.changeDisplayMode(targetMode.modeId)
                    val result =
                        withTimeoutOrNull(5.seconds) {
                            listener.deferred.await()
                        }
                    if (result == null) {
                        Timber.w("Timed out waiting for display change")
                        showToast(context, "Refresh rate switch is taking a long time")
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Exception waiting for refresh rate switch")
                } finally {
                    displayManager.unregisterDisplayListener(listener)
                }
                val targetRate = (targetMode.refreshRate * 1000).roundToInt()
                val isSeamless =
                    targetRate == (currentDisplayMode.refreshRate * 1000).roundToInt() ||
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            currentDisplayMode.alternativeRefreshRates
                                .map { (it * 1000).roundToInt() }
                                .any { it % targetRate == 0 }
                        } else {
                            false
                        }
                if (!isSeamless) {
                    Timber.v("Waiting for non-seamless switch")
                    // Wait the recommended 2 seconds (https://developer.android.com/media/optimize/performance/frame-rate)
                    delay(2.seconds)
                }
            }
        }

        /**
         * Reset the display mode to the original
         */
        fun resetRefreshRate() {
            MainActivity.instance.changeDisplayMode(0)
        }

        /**
         * Listens for the display to change so we known the refresh rate or resolution is updated
         */
        private class Listener(
            val displayId: Int,
        ) : DisplayManager.DisplayListener {
            val deferred = CompletableDeferred<Unit>()

            override fun onDisplayAdded(displayId: Int) {
            }

            override fun onDisplayChanged(displayId: Int) {
                if (displayId == this.displayId) {
                    Timber.v("Got display change for $displayId")
                    deferred.complete(Unit)
                }
            }

            override fun onDisplayRemoved(displayId: Int) {
            }
        }

        companion object {
            // Slightly wider than the difference between integer and 1000/1001 rates.
            private const val FRAME_RATE_TOLERANCE = 0.0011

            /**
             * Find the best display mode for the given stream & preferences
             *
             * @param displayModes candidates sorted by descending pixel count and ascending refresh rate
             */
            fun findDisplayMode(
                displayModes: List<DisplayMode>,
                streamWidth: Int,
                streamHeight: Int,
                targetFrameRate: Float,
                refreshRateSwitch: Boolean,
                resolutionSwitch: Boolean,
            ): DisplayMode? {
                if (!targetFrameRate.isFinite()) return null
                val streamRate = targetFrameRate.times(1000).roundToInt()
                if (streamRate <= 0) return null
                val tolerance = if (refreshRateSwitch) FRAME_RATE_TOLERANCE else 0.0
                val candidates = displayModes.filter { it.physicalWidth >= streamWidth && it.physicalHeight >= streamHeight }

                fun oneToOneError(mode: DisplayMode) = frameRateError(mode.refreshRateRounded, streamRate.toDouble(), tolerance)

                fun cadenceError(mode: DisplayMode) = cadenceError(mode.refreshRateRounded, streamRate, tolerance)

                val oneToOne = candidates.filter { oneToOneError(it).isFinite() }
                val compatible =
                    if (refreshRateSwitch) {
                        candidates.filter { frameRateMatches(it.refreshRateRounded, streamRate) }
                    } else {
                        candidates.filter { cadenceError(it).isFinite() }
                    }
                if (!resolutionSwitch) {
                    return oneToOne.closestAtResolution(oneToOne.largestMode(), ::oneToOneError)
                        ?: if (refreshRateSwitch) {
                            compatible.closestAtResolution(compatible.largestMode(), ::cadenceError)
                        } else {
                            candidates.largestMode()
                        }
                }

                val exactResolution = oneToOne.firstOrNull { it.physicalWidth == streamWidth && it.physicalHeight == streamHeight }
                return oneToOne.closestAtResolution(exactResolution, ::oneToOneError)
                    ?: oneToOne.asReversed().closestAtResolution(oneToOne.lastOrNull(), ::oneToOneError)
                    ?: compatible.asReversed().closestAtResolution(compatible.lastOrNull(), ::cadenceError)
                    ?: candidates.takeUnless { refreshRateSwitch }?.largestMode()
            }

            private fun List<DisplayMode>.largestMode(): DisplayMode? = maxByOrNull { it.physicalWidth * it.physicalHeight }

            private fun List<DisplayMode>.closestAtResolution(
                preferred: DisplayMode?,
                error: (DisplayMode) -> Double,
            ): DisplayMode? {
                if (preferred == null) return null
                return asSequence()
                    .filter { it.physicalWidth == preferred.physicalWidth && it.physicalHeight == preferred.physicalHeight }
                    .minByOrNull(error)
            }

            fun frameRateMatches(
                refreshRateRounded: Int,
                streamRate: Int,
            ): Boolean = cadenceError(refreshRateRounded, streamRate, FRAME_RATE_TOLERANCE).isFinite()

            private fun cadenceError(
                refreshRate: Int,
                streamRate: Int,
                tolerance: Double,
            ): Double {
                if (streamRate <= 0) return Double.POSITIVE_INFINITY
                val multiple = (refreshRate.toDouble() / streamRate).roundToInt()
                val integerTarget = streamRate.toDouble() * multiple
                val pulldownTarget = (streamRate * 2.5).roundToLong().toDouble()
                return minOf(
                    frameRateError(refreshRate, integerTarget, tolerance),
                    frameRateError(refreshRate, pulldownTarget, tolerance),
                )
            }

            private fun frameRateError(
                refreshRate: Int,
                targetRate: Double,
                tolerance: Double,
            ): Double {
                if (refreshRate <= 0 || targetRate <= 0) return Double.POSITIVE_INFINITY
                val error = abs(refreshRate - targetRate)
                return if (error <= targetRate * tolerance) error else Double.POSITIVE_INFINITY
            }
        }
    }

/**
 * Wrapper for a [Display.Mode]
 */
data class DisplayMode(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float,
) {
    val refreshRateRounded: Int = (refreshRate * 1000).roundToInt()

    constructor(mode: Display.Mode) : this(
        mode.modeId,
        mode.physicalWidth,
        mode.physicalHeight,
        mode.refreshRate,
    )
}
