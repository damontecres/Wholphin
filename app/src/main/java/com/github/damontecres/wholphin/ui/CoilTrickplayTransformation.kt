package com.github.damontecres.wholphin.ui

import android.graphics.Bitmap
import androidx.core.graphics.scale
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.transform.Transformation

/**
 * A Coil [Transformation] that extracts a subimage from a trickplay image
 */
class CoilTrickplayTransformation(
    val targetWidth: Int,
    val targetHeight: Int,
    val numRows: Int,
    val numColumns: Int,
    val imageIndex: Int,
    val index: Int,
) : Transformation() {
    private val x: Int = imageIndex % numColumns
    private val y: Int = imageIndex / numRows

    override val cacheKey: String
        get() = "CoilTrickplayTransformation_$index,$x,$y"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val width = input.width / numColumns
        val height = input.height / numRows
        return Bitmap
            .createBitmap(input, x * width, y * height, width, height)
            .scale(size.width.pxOrElse { targetWidth }, size.height.pxOrElse { targetHeight })
    }
}
