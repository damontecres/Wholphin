package com.github.damontecres.wholphin.ui

import android.graphics.Bitmap
import coil3.size.Size
import coil3.transform.Transformation

/**
 * A Coil [Transformation] that cuts away the see-through border around an image, so a logo
 * with empty space around it is sized by what is actually drawn
 *
 * @param threshold how solid (0-255) a pixel has to be to count as part of the image, so faint
 * shadows and stray specks at the edges don't stop the trim
 */
class CoilTrimTransformation(
    private val threshold: Int = 16,
) : Transformation() {
    override val cacheKey: String
        get() = "CoilTrimTransformation_$threshold"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        if (!input.hasAlpha()) return input
        val width = input.width
        val height = input.height
        val row = IntArray(width)
        var top = height
        var bottom = -1
        var left = width
        var right = -1
        for (y in 0 until height) {
            input.getPixels(row, 0, width, 0, y, width, 1)
            var first = -1
            var last = -1
            for (x in 0 until width) {
                if ((row[x] ushr 24) > threshold) {
                    if (first < 0) first = x
                    last = x
                }
            }
            if (first >= 0) {
                if (top == height) top = y
                bottom = y
                if (first < left) left = first
                if (last > right) right = last
            }
        }
        // Nothing solid at all, or nothing to cut: leave it as it is
        if (bottom < 0) return input
        if (left == 0 && top == 0 && right == width - 1 && bottom == height - 1) return input
        return Bitmap.createBitmap(input, left, top, right - left + 1, bottom - top + 1)
    }
}
