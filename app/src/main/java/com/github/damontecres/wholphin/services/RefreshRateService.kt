package com.github.damontecres.wholphin.services

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Display
import androidx.lifecycle.LiveData
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.EqualityMutableLiveData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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
        private val originalMode = display.mode

        private val _refreshRateMode = EqualityMutableLiveData<Display.Mode>(originalMode)
        val refreshRateMode: LiveData<Display.Mode> = _refreshRateMode

        /**
         * Find the best display mode for the given stream and signal to change to it
         */
        suspend fun changeRefreshRate(stream: MediaStream) {
            require(stream.type == MediaStreamType.VIDEO) { "Stream is not video" }
            val width = stream.width
            val height = stream.height
            val frameRate = stream.realFrameRate?.times(100)?.roundToInt()
            if (width == null || height == null || frameRate == null) {
                Timber.w("Video stream missing required info: width=%s, height=%s, frameRate=%s", width, height, frameRate)
                return
            }
            Timber.d("Getting refresh rate for: width=%s, height=%s, frameRate=%s", width, height, frameRate)
            val targetMode =
                display.supportedModes
                    .filterNot { it.physicalHeight < height || it.physicalWidth < width }
                    .filter {
                        (it.refreshRate * 100).roundToInt().let { modeRate ->
                            frameRate % modeRate == 0 || // Exact multiple
                                modeRate == (frameRate * 2.5).roundToInt() // eg 24 & 60fps
                        }
                    }.maxByOrNull { it.physicalWidth * it.physicalHeight }
            Timber.i("Found display mode: %s, current=${display.mode}", targetMode)
            if (targetMode != null && targetMode != display.mode) {
                val listener = Listener(display.displayId)
                displayManager.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
                _refreshRateMode.setValueOnMain(targetMode)
                try {
                    listener.latch.await(5, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    Timber.w("Timed out waiting for display change")
                    showToast(context, "Refresh rate switch is taking a long time")
                }
                val targetRate = (targetMode.refreshRate * 100).roundToInt()
                val isSeamless =
                    targetRate == (display.mode.refreshRate * 100).roundToInt() ||
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            display.mode.alternativeRefreshRates
                                .map { (it * 100).roundToInt() }
                                .any { targetRate % it == 0 }
                        } else {
                            false
                        }
                if (!isSeamless) {
                    Timber.v("Waiting for non-seamless switch")
                    // Wait the recommended 2 seconds (https://developer.android.com/media/optimize/performance/frame-rate)
                    delay(2.seconds)
                }
                displayManager.unregisterDisplayListener(listener)
            }
        }

        /**
         * Reset the display mode to the original
         */
        fun resetRefreshRate() {
            _refreshRateMode.value = originalMode
        }

        private class Listener(
            val displayId: Int,
        ) : DisplayManager.DisplayListener {
            val latch = CountDownLatch(1)

            override fun onDisplayAdded(displayId: Int) {
            }

            override fun onDisplayChanged(displayId: Int) {
                if (displayId == this.displayId) {
                    Timber.v("Got display change for $displayId")
                    latch.countDown()
                }
            }

            override fun onDisplayRemoved(displayId: Int) {
            }
        }
    }
