package com.github.damontecres.wholphin.services

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.lifecycle.LiveData
import com.github.damontecres.wholphin.util.EqualityMutableLiveData
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

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
        fun changeRefreshRate(stream: MediaStream) {
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
            Timber.i("Found display mode: %s", targetMode)
            if (targetMode != null) {
                _refreshRateMode.value = targetMode
            }
        }

        /**
         * Reset the display mode to the original
         */
        fun resetRefreshRate() {
            _refreshRateMode.value = originalMode
        }
    }
