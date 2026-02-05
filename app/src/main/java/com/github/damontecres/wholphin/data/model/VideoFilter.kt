package com.github.damontecres.wholphin.data.model

import android.graphics.ColorMatrix
import androidx.annotation.IntRange
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.GlEffect
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.room.Ignore

/**
 * Modifications to a video playback
 */
data class VideoFilter(
    val rotation: Int = 0,
    @param:IntRange(0, 200) val brightness: Int = COLOR_DEFAULT,
    @param:IntRange(0, 200) val contrast: Int = COLOR_DEFAULT,
    @param:IntRange(0, 200) val saturation: Int = COLOR_DEFAULT,
    @param:IntRange(0, 360) val hue: Int = HUE_DEFAULT,
    @param:IntRange(0, 200) val red: Int = COLOR_DEFAULT,
    @param:IntRange(0, 200) val green: Int = COLOR_DEFAULT,
    @param:IntRange(0, 200) val blue: Int = COLOR_DEFAULT,
    @param:IntRange(0, 250) val blur: Int = 0,
) {
    @Ignore
    val colorMatrix: androidx.compose.ui.graphics.ColorMatrix = createComposeColorMatrix()

    companion object {
        const val COLOR_DEFAULT = 100
        const val HUE_DEFAULT = 0
    }

    fun isRotated(): Boolean = rotation != 0 && rotation % 360 != 0

    fun hasRgb(): Boolean = red != COLOR_DEFAULT || green != COLOR_DEFAULT || blue != COLOR_DEFAULT

    fun hasBrightness(): Boolean = brightness != COLOR_DEFAULT

    fun hasContrast(): Boolean = contrast != COLOR_DEFAULT

    fun hasHsl(): Boolean = hue != HUE_DEFAULT || saturation != COLOR_DEFAULT

    fun hasBlur(): Boolean = blur > 0

    fun hasImageFilter(): Boolean = hasRgb() || hasBrightness() || hasContrast() || saturation != COLOR_DEFAULT

    @OptIn(UnstableApi::class)
    private fun rgbAdjustment(): RgbAdjustment =
        RgbAdjustment
            .Builder()
            .setRedScale(red / COLOR_DEFAULT.toFloat())
            .setGreenScale(green / COLOR_DEFAULT.toFloat())
            .setBlueScale(blue / COLOR_DEFAULT.toFloat())
            .build()

    /**
     * Create the list of effects to apply
     */
    @OptIn(UnstableApi::class)
    fun createEffectList(): List<GlEffect> =
        buildList {
            if (isRotated()) {
                add(
                    ScaleAndRotateTransformation
                        .Builder()
                        .setRotationDegrees(rotation.toFloat())
                        .build(),
                )
            }
            if (hasRgb()) {
                add(rgbAdjustment())
            }
            if (hasBrightness()) {
                add(Brightness((brightness - 100) / 100f))
            }
            if (hasContrast()) {
                add(Contrast((contrast - 100) / 100f))
            }
            if (hasHsl()) {
                add(
                    HslAdjustment
                        .Builder()
                        .adjustHue(hue.toFloat())
                        .adjustSaturation((saturation - 100).toFloat())
                        .build(),
                )
            }
            if (hasBlur()) {
                add(GaussianBlur(blur / 10f))
            }
        }

    private fun saturationMatrix(): FloatArray {
        val rF = 0.2999f
        val gF = 0.587f
        val bF = 0.114f
        val s = saturation / 100.0f

        val ms = 1.0f - s
        val rT = rF * ms
        val gT = gF * ms
        val bT = bF * ms

        val m =
            FloatArray(20) {
                when (it) {
                    0 -> (rT + s)
                    1 -> gT
                    2 -> bT
                    5 -> rT
                    6 -> (gT + s)
                    7 -> bT
                    10 -> rT
                    11 -> gT
                    12 -> (bT + s)
                    18 -> 1f
                    else -> 0f
                }
            }
        return m
    }

    @OptIn(UnstableApi::class)
    fun createColorMatrix(): ColorMatrix {
        val matrix = ColorMatrix()
        val tempMatrix = ColorMatrix()

        if (saturation != COLOR_DEFAULT) {
            matrix.set(saturationMatrix())
        }
        if (hasRgb()) {
            val colorMatrix = rgbAdjustment().getMatrix(0L, false)
            val m = FloatArray(20)
            m[0] = colorMatrix[0]
            m[1] = colorMatrix[1]
            m[2] = colorMatrix[2]
            m[3] = colorMatrix[3]
            m[4] = 0f
            m[5] = colorMatrix[4]
            m[6] = colorMatrix[5]
            m[7] = colorMatrix[6]
            m[8] = colorMatrix[7]
            m[9] = 0f
            m[10] = colorMatrix[8]
            m[11] = colorMatrix[9]
            m[12] = colorMatrix[10]
            m[13] = colorMatrix[11]
            m[14] = 0f
            m[15] = colorMatrix[12]
            m[16] = colorMatrix[13]
            m[17] = colorMatrix[14]
            m[18] = colorMatrix[15]
            m[19] = 0f
            tempMatrix.set(m)
            matrix.postConcat(tempMatrix)
        }
        if (hasContrast()) {
            val scale = contrast / 100.0f
            tempMatrix.setScale(scale, scale, scale, 1f)
            matrix.postConcat(tempMatrix)
        }
        if (hasBrightness()) {
            val b = brightness / 100.0f
            val m = FloatArray(20)
            m[0] = b
            m[6] = b
            m[12] = b
            m[18] = 1f
            tempMatrix.set(m)
            matrix.postConcat(tempMatrix)
        }
        // TODO hue
        // TODO blur
        return matrix
    }

    @OptIn(UnstableApi::class)
    fun createComposeColorMatrix(): androidx.compose.ui.graphics.ColorMatrix {
        val matrix =
            androidx.compose.ui.graphics
                .ColorMatrix()

        if (saturation != COLOR_DEFAULT) {
            matrix.setToSaturation(saturation / 100f)
        }
        if (hasRgb()) {
            matrix.setToScale(
                redScale = red / 100f,
                greenScale = green / 100f,
                blueScale = blue / 100f,
                alphaScale = 1f,
            )
        }
        if (hasContrast()) {
            val scale = contrast / 100.0f
            val tempMatrix =
                androidx.compose.ui.graphics
                    .ColorMatrix()
            tempMatrix.setToScale(scale, scale, scale, 1f)
            matrix.timesAssign(tempMatrix)
        }
        if (hasBrightness()) {
            val b = brightness / 100.0f
            val m = FloatArray(20)
            m[0] = b
            m[6] = b
            m[12] = b
            m[18] = 1f
            val tempMatrix =
                androidx.compose.ui.graphics
                    .ColorMatrix(m)
            matrix.timesAssign(tempMatrix)
        }
        // TODO hue
        // TODO blur
        return matrix
    }
}
