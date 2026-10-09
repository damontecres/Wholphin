package com.github.damontecres.wholphin.ui.nav

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.drawable.toBitmap
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.max
import kotlin.math.roundToInt

// The heavy lifting behind the backdrop: composing the baked background and generating film grain.
// Kept apart from Backdrop.kt on purpose. While tuning values there with Live Edit, the code in
// that file runs far slower than normal, which froze the app when these loops lived in it.

/**
 * A square of random grey noise that repeats seamlessly, used as film grain. Neutral grey leaves
 * the picture untouched under an Overlay blend; lighter and darker specks lighten and darken it.
 *
 * @param contrast how far the specks stray from neutral grey, 0 to 1
 * @param clumping how much the grain gathers into larger, uneven patches instead of being evenly
 * fine, 0 to 1
 */
internal fun grainTile(
    contrast: Float,
    clumping: Float,
): ImageBitmap =
    synchronized(grainTiles) {
        grainTiles.getOrPut(contrast to clumping) { makeGrainTile(contrast, clumping) }
    }

// Tiles already generated, so each is only ever made once
private val grainTiles = HashMap<Pair<Float, Float>, ImageBitmap>()

// Large enough that the repeat is not noticeable on screen
internal const val GRAIN_TILE_SIZE = 1024

// Size, in grains, of the uneven patches that clumping adds
private const val GRAIN_CLUMP_SIZE = 4

private fun makeGrainTile(
    contrast: Float,
    clumping: Float,
): ImageBitmap {
    val size = GRAIN_TILE_SIZE
    val random = java.util.Random(7)
    val spread = 127f * contrast.coerceIn(0f, 1f) / 2f
    val clump = clumping.coerceIn(0f, 1f)

    // A coarser layer of noise, read smoothly, gives patches where the grain runs lighter or
    // darker and makes neighbouring grains vary in size
    val coarseSize = size / GRAIN_CLUMP_SIZE
    val coarse = FloatArray(coarseSize * coarseSize) { random.nextGaussian().toFloat() }

    fun coarseAt(
        x: Int,
        y: Int,
    ): Float {
        val u = x.toFloat() / GRAIN_CLUMP_SIZE
        val v = y.toFloat() / GRAIN_CLUMP_SIZE
        val x0 = u.toInt() % coarseSize
        val y0 = v.toInt() % coarseSize
        val x1 = (x0 + 1) % coarseSize
        val y1 = (y0 + 1) % coarseSize
        val fx = u - u.toInt()
        val fy = v - v.toInt()
        val top = coarse[y0 * coarseSize + x0] * (1f - fx) + coarse[y0 * coarseSize + x1] * fx
        val bottom = coarse[y1 * coarseSize + x0] * (1f - fx) + coarse[y1 * coarseSize + x1] * fx
        return top * (1f - fy) + bottom * fy
    }

    val pixels =
        IntArray(size * size) { index ->
            val fine = random.nextGaussian().toFloat()
            // The smooth reading flattens the coarse layer, so it is boosted to keep its weight
            val mixed = fine * (1f - clump) + coarseAt(index % size, index / size) * 1.6f * clump
            val grey = (128 + mixed * spread).toInt().coerceIn(0, 255)
            (0xFF shl 24) or (grey shl 16) or (grey shl 8) or grey
        }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

// Recently baked backgrounds, so going back to a backdrop that was already shown is instant
internal val bakedBackgrounds = LruCache<Any, ImageBitmap>(96)

/**
 * Composes the backdrop's background once, into a small image: the theme background and colour
 * glows, then the blurred copies of the backdrop with their blend modes, then the group opacity.
 * The result looks the same as drawing those layers live, but costs a single layer on screen.
 *
 * @return the image, or null if it could not be made
 */
internal suspend fun bakeBackdropBackground(
    context: Context,
    imageUrl: Any?,
    width: Int,
    height: Int,
    density: Density,
    layoutDirection: LayoutDirection,
    resolution: Int,
    imageAspect: Float,
    scaleX: Float,
    scaleY: Float,
    shiftX: Float,
    shiftY: Float,
    layers: List<Pair<BlendMode, Float>>,
    groupOpacity: Float,
    drawBackground: DrawScope.() -> Unit,
    drawOverlay: DrawScope.() -> Unit,
    smoothRadius: Int,
): ImageBitmap? =
    withContext(Dispatchers.Default) {
        try {
            // The tiny version of the backdrop that gets stretched into a blur
            var soft: Bitmap? = null
            if (imageUrl != null) {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(imageUrl)
                        .size(resolution, (resolution / imageAspect).toInt().coerceAtLeast(1))
                        .allowHardware(false)
                        .bitmapConfig(Bitmap.Config.ARGB_8888)
                        .build()
                val result = context.imageLoader.execute(request)
                if (result is SuccessResult) {
                    soft =
                        result.image
                            .asDrawable(context.resources)
                            .toBitmap(config = Bitmap.Config.ARGB_8888)
                }
            }
            // Make sure it really is the tiny size asked for, so the stretch below blurs it
            // exactly as the live layers do
            val tinyHeight = (resolution / imageAspect).toInt().coerceAtLeast(1)
            if (soft != null && (soft.width != resolution || soft.height != tinyHeight)) {
                soft = Bitmap.createScaledBitmap(soft, resolution, tinyHeight, true)
            }
            val blurred = soft?.asImageBitmap()

            val output = ImageBitmap(width, height)
            val outputSize = Size(width.toFloat(), height.toFloat())
            CanvasDrawScope().draw(density, layoutDirection, Canvas(output), outputSize) {
                drawBackground()
                if (blurred != null) {
                    // Fill the screen with the blurred copy, cropping what does not fit
                    val blurredAspect = blurred.width.toFloat() / blurred.height
                    val fillWidth = max(size.width, size.height * blurredAspect)
                    val fillHeight = fillWidth / blurredAspect
                    val fillOffset =
                        IntOffset(
                            ((size.width - fillWidth) / 2f).roundToInt(),
                            ((size.height - fillHeight) / 2f).roundToInt(),
                        )
                    val fillSize = IntSize(fillWidth.roundToInt(), fillHeight.roundToInt())
                    val overflow = (scaleY - 1f) / 2f
                    layers.forEach { (layerBlendMode, layerOpacity) ->
                        withTransform({
                            translate(size.width * overflow * shiftX, size.height * overflow * shiftY)
                            scale(scaleX, scaleY, pivot = center)
                        }) {
                            drawImage(
                                image = blurred,
                                dstOffset = fillOffset,
                                dstSize = fillSize,
                                alpha = layerOpacity.coerceIn(0f, 1f),
                                blendMode = layerBlendMode,
                                // The same plain stretch the live layers get on screen
                                filterQuality = FilterQuality.Low,
                            )
                        }
                    }
                    // Group opacity: the background again on top, fading the stack back towards it
                    val cover = 1f - groupOpacity.coerceIn(0f, 1f)
                    if (cover > 0f) {
                        drawIntoCanvas { canvas ->
                            canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = cover })
                            drawBackground()
                            canvas.restore()
                        }
                    }
                }
            }
            // Optional smoothing of the result, before the vignettes so they keep their shape
            boxBlur(output.asAndroidBitmap(), smoothRadius)
            CanvasDrawScope().draw(density, layoutDirection, Canvas(output), outputSize) {
                drawOverlay()
            }
            output
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: Exception) {
            Timber.w(ex, "Could not bake backdrop background")
            null
        }
    }

/**
 * Blurs an opaque bitmap in place. Three passes of a box blur come very close to a Gaussian blur
 * and are fast enough for the small baked image
 */
private fun boxBlur(
    bitmap: Bitmap,
    radius: Int,
) {
    if (radius < 1) return
    val width = bitmap.width
    val height = bitmap.height
    var pixels = IntArray(width * height)
    var scratch = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    repeat(3) {
        boxBlurPass(pixels, scratch, width, height, radius, horizontal = true)
        boxBlurPass(scratch, pixels, width, height, radius, horizontal = false)
    }
    bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
}

private fun boxBlurPass(
    input: IntArray,
    output: IntArray,
    width: Int,
    height: Int,
    radius: Int,
    horizontal: Boolean,
) {
    val length = if (horizontal) width else height
    val lines = if (horizontal) height else width
    val window = 2 * radius + 1
    for (line in 0 until lines) {
        // Position in the pixel array of the i-th pixel along this row or column, clamped at the ends
        fun at(i: Int): Int {
            val clamped = i.coerceIn(0, length - 1)
            return if (horizontal) line * width + clamped else clamped * width + line
        }
        var red = 0
        var green = 0
        var blue = 0
        for (i in -radius..radius) {
            val color = input[at(i)]
            red += (color shr 16) and 0xFF
            green += (color shr 8) and 0xFF
            blue += color and 0xFF
        }
        for (i in 0 until length) {
            output[at(i)] =
                (0xFF shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
            val leaving = input[at(i - radius)]
            val entering = input[at(i + radius + 1)]
            red += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}
