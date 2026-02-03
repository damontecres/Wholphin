package com.github.damontecres.wholphin.services

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import com.github.damontecres.wholphin.MainActivity
import com.github.damontecres.wholphin.ui.showToast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

@Singleton
class RefreshRateService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        private val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)

        val supportedDisplayModes get() = display.supportedModes.orEmpty()

        private val displayModes: List<DisplayMode> by lazy {
            display.supportedModes
                .orEmpty()
                .map { DisplayMode(it) }
                .sortedWith(
                    compareByDescending<DisplayMode>({ it.physicalWidth * it.physicalHeight })
                        .thenBy { it.refreshRateRounded },
                )
        }

        /**
         * Find the best display mode for the given stream and signal to change to it
         */
        suspend fun changeRefreshRate(
            stream: MediaStream,
            switchRefreshRate: Boolean,
            switchResolution: Boolean,
        ) = withContext(Dispatchers.IO) {
            if (!switchRefreshRate && !switchResolution) {
                Timber.v("Not switching either refresh rate nor resolution")
                return@withContext
            }
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
            /**
             * Find the best display mode for the given stream & preferences
             *
             * @param displayModes candidates that are sorted by resolution and frame rate descending
             */
            fun findDisplayMode(
                displayModes: List<DisplayMode>,
                streamWidth: Int,
                streamHeight: Int,
                targetFrameRate: Float,
                refreshRateSwitch: Boolean,
                resolutionSwitch: Boolean,
            ): DisplayMode? {
                val streamRate = targetFrameRate.times(1000).roundToInt()
//                Timber.v("display modes: %s", displayModes.joinToString("\n"))
                val candidates =
                    if (refreshRateSwitch) {
                        displayModes
                            .filterNot { it.physicalHeight < streamHeight || it.physicalWidth < streamWidth }
                            .filter {
                                it.refreshRateRounded % streamRate == 0 || // Exact multiple
                                    it.refreshRateRounded == (streamRate * 2.5).roundToInt() // eg 24 & 60fps
                            }
                    } else {
                        displayModes
                            .filterNot { it.physicalHeight < streamHeight || it.physicalWidth < streamWidth }
                    }
//                Timber.v("display modes candidates: %s", candidates.joinToString("\n"))
                return if (!resolutionSwitch) {
                    candidates.maxByOrNull { it.physicalWidth * it.physicalHeight }
                } else {
                    candidates.firstOrNull {
                        it.physicalWidth == streamWidth &&
                            it.physicalHeight == streamHeight &&
                            it.refreshRateRounded == streamRate
                    }
                        ?: candidates.firstOrNull {
                            it.physicalWidth == streamWidth &&
                                it.physicalHeight >= streamHeight &&
                                it.refreshRateRounded == streamRate
                        }
                        ?: candidates
                            .filter { it.refreshRateRounded == streamRate }
                            .maxByOrNull { it.physicalWidth * it.physicalHeight }
                }
            }
        }
    }

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
