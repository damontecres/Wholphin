package com.github.damontecres.wholphin.util

import androidx.media3.ui.SubtitleView
import timber.log.Timber
import java.lang.reflect.Field

/**
 * Utility class to override private fields in media3's [SubtitleView]
 */
class Media3SubtitleOverride(
    val outlineThicknessPx: Float,
) {
    companion object {
        private lateinit var outputField: Field
        private lateinit var classCanvasSubtitleOutput: Class<*>
        private lateinit var paintersField: Field
        private lateinit var classSubtitlePainter: Class<*>
        private lateinit var outlineWidthField: Field

        var initialized: Boolean
            private set

        init {
            try {
                // This is bad times
                outputField = SubtitleView::class.java.getDeclaredField("output")
                classCanvasSubtitleOutput = Class.forName("androidx.media3.ui.CanvasSubtitleOutput")
                paintersField = classCanvasSubtitleOutput.getDeclaredField("painters")
                classSubtitlePainter = Class.forName("androidx.media3.ui.SubtitlePainter")
                outlineWidthField = classSubtitlePainter.getDeclaredField("outlineWidth")
                outputField.isAccessible = true
                outlineWidthField.isAccessible = true
                paintersField.isAccessible = true
                initialized = true
            } catch (ex: Exception) {
                Timber.w(ex, "Error initializing")
                initialized = false
            }
        }
    }

    fun apply(subtitleView: SubtitleView) {
        try {
            if (initialized) {
                // Basically hijack the field that controls the subtitle outline size
                val canvasSubtitleOutput = outputField.get(subtitleView)
                val painters = paintersField.get(canvasSubtitleOutput) as List<*>
                painters.forEach { painter ->
                    outlineWidthField.set(painter, outlineThicknessPx)
                }
            }
        } catch (ex: Exception) {
            Timber.w(ex, "Failed to apply")
        }
    }
}
