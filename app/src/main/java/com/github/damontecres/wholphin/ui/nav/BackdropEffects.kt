package com.github.damontecres.wholphin.ui.nav

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * One tile of the baked background: a copy of the backdrop image, usually blurred, laid over an
 * area of the screen. The background is built from any number of these, drawn in order.
 *
 * Every tile has the same settings. Each has a default, so a tile only needs to state the ones
 * that differ.
 */
internal data class Tile(
    // Where the tile's left and top edges sit, as shares of the screen: 0f, 0f is the top-left
    // corner, 1f, 1f the bottom-right. Either may be negative or above 1 to reach off screen
    val left: Float = 0f,
    val top: Float = 0f,
    // Width as a share of the screen's width, and height as a share of the screen's height. The
    // picture is squeezed or pulled to fit, so the two together set its shape
    val width: Float = 1f,
    val height: Float = 1f,
    // Mirrored left to right, in place
    val flip: Boolean = false,
    // Upside down, in place
    val flipVertical: Boolean = false,
    // Width in pixels of the version of the image that is stretched over the tile: lower = blurrier
    val resolution: Int = 64,
    // Extra blur on this tile alone, as a share of the screen's width: 0 = none
    val smooth: Float = 0f,
    // How it is drawn, bottom to top, as blend mode to opacity. Several lines combine modes
    val layers: List<Pair<BlendMode, Float>> = listOf(BlendMode.SrcOver to 1f),
    // Where it sits relative to the group. With both false it is part of the group, which is
    // blurred together as one and blended on as a whole.
    // behind = drawn first, straight onto the glows, so it ends up under the group
    // onTop = drawn last, straight onto the result, so it ends up over the group
    val behind: Boolean = false,
    val onTop: Boolean = false,
    // Soft edges: how far in from each edge the tile fades out, as a share of its own width
    // (left and right) and height (top and bottom). 0 = hard edges, 0.5 = fades all the way
    // to the middle. Null = use the default given where the tiles are set up
    val feather: Float? = null,
)

/**
 * Composes the backdrop's background once, into a small image: the theme background and colour
 * glows, then each tile with its blend modes, then
 * optional smoothing, then the overlay (vignettes).
 * The result costs a single layer on screen.
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
    imageAspect: Float,
    tiles: List<Tile>,
    drawBackground: DrawScope.() -> Unit,
    drawOverlay: DrawScope.() -> Unit,
    groupSmoothRadius: Int,
    groupLayers: List<Pair<BlendMode, Float>>,
    groupOpacity: Float,
    smoothRadius: Int,
    outlineCopies: Boolean,
): ImageBitmap? =
    withContext(Dispatchers.Default) {
        try {
            // The tiny versions of the backdrop that get stretched into blurs, one per resolution
            val tinyVersions = HashMap<Int, ImageBitmap?>()
            if (imageUrl != null) {
                tiles.map { it.resolution }.distinct().forEach { resolution ->
                    val tinyHeight = (resolution / imageAspect).toInt().coerceAtLeast(1)
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(imageUrl)
                            .size(resolution, tinyHeight)
                            .allowHardware(false)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build()
                    val result = context.imageLoader.execute(request)
                    var soft: Bitmap? = null
                    if (result is SuccessResult) {
                        soft =
                            result.image
                                .asDrawable(context.resources)
                                .toBitmap(config = Bitmap.Config.ARGB_8888)
                    }
                    // Make sure it really is the tiny size asked for, so the stretch blurs it
                    // as intended
                    if (soft != null && (soft.width != resolution || soft.height != tinyHeight)) {
                        soft = Bitmap.createScaledBitmap(soft, resolution, tinyHeight, true)
                    }
                    tinyVersions[resolution] = soft?.asImageBitmap()
                }
            }

            val output = ImageBitmap(width, height)
            val outputSize = Size(width.toFloat(), height.toFloat())
            CanvasDrawScope().draw(density, layoutDirection, Canvas(output), outputSize) {
                drawBackground()
            }
            // Three passes. First the tiles marked behind go straight onto the glows. Then the
            // group is drawn on a see-through sheet of its own, blurred together as one, and
            // that sheet is blended on top. Then the tiles marked onTop go straight onto the
            // result. Last, everything is smoothed together
            listOf(
                Triple(tiles.filter { it.behind && !it.onTop }, 0, false),
                Triple(tiles.filter { !it.behind && !it.onTop }, groupSmoothRadius, true),
                Triple(tiles.filter { it.onTop }, 0, false),
            ).forEach { (passTiles, passRadius, isGroup) ->
                val target = if (isGroup) ImageBitmap(width, height) else output
                CanvasDrawScope().draw(density, layoutDirection, Canvas(target), outputSize) {
                    passTiles.forEach { copy ->
                        // As a share of the screen's height
                        val tileHeight = copy.height
                        val blurred = tinyVersions[copy.resolution]
                        if (blurred != null) {
                            val offset =
                                IntOffset(
                                    (size.width * copy.left).roundToInt(),
                                    (size.height * copy.top).roundToInt(),
                                )
                            val area =
                                IntSize(
                                    (size.width * copy.width).roundToInt().coerceAtLeast(1),
                                    (size.height * tileHeight).roundToInt().coerceAtLeast(1),
                                )
                            // This copy's own blur: stretch the tiny version to its final size first,
                            // blur that, and draw the result in place of the plain stretch
                            val smoothRadius = (size.width * copy.smooth).roundToInt()
                            val source =
                                if (smoothRadius >= 1) {
                                    // Kept to a sensible size; the blur radius shrinks along with it
                                    val shrink = minOf(1f, 2048f / maxOf(area.width, area.height))
                                    val stretched =
                                        Bitmap
                                            .createScaledBitmap(
                                                blurred.asAndroidBitmap(),
                                                (area.width * shrink).roundToInt().coerceAtLeast(1),
                                                (area.height * shrink).roundToInt().coerceAtLeast(1),
                                                true,
                                            ).copy(Bitmap.Config.ARGB_8888, true)
                                    boxBlur(stretched, (smoothRadius * shrink).roundToInt().coerceAtLeast(1))
                                    stretched.asImageBitmap()
                                } else {
                                    blurred
                                }
                            val feather = (copy.feather ?: 0f).coerceIn(0f, 0.5f)
                            val drawn =
                                if (feather > 0f) {
                                    val shrink = minOf(1f, 2048f / maxOf(area.width, area.height))
                                    featheredCopy(
                                        source,
                                        (area.width * shrink).roundToInt().coerceAtLeast(1),
                                        (area.height * shrink).roundToInt().coerceAtLeast(1),
                                        feather,
                                        density,
                                        layoutDirection,
                                    )
                                } else {
                                    source
                                }
                            // Mirrored about the copy's own middle, so it stays where it is
                            val middle = Offset(offset.x + area.width / 2f, offset.y + area.height / 2f)
                            withTransform({
                                if (copy.flip || copy.flipVertical) {
                                    scale(
                                        scaleX = if (copy.flip) -1f else 1f,
                                        scaleY = if (copy.flipVertical) -1f else 1f,
                                        pivot = middle,
                                    )
                                }
                            }) {
                                copy.layers.forEach { (layerBlendMode, layerOpacity) ->
                                    drawImage(
                                        image = drawn,
                                        dstOffset = offset,
                                        dstSize = area,
                                        alpha = layerOpacity.coerceIn(0f, 1f),
                                        blendMode = layerBlendMode,
                                        // A plain stretch, which is what turns the tiny version into a blur
                                        filterQuality = FilterQuality.Low,
                                    )
                                }
                            }
                        }
                    }
                }
                boxBlur(target.asAndroidBitmap(), passRadius)
                val sheetOpacity = groupOpacity.coerceIn(0f, 1f)
                if (isGroup && passTiles.isNotEmpty() && sheetOpacity > 0f) {
                    // The group's sheet onto the glows, with the group's own blend modes
                    val before =
                        if (sheetOpacity < 1f) {
                            output.asAndroidBitmap().copy(Bitmap.Config.ARGB_8888, false).asImageBitmap()
                        } else {
                            null
                        }
                    CanvasDrawScope().draw(density, layoutDirection, Canvas(output), outputSize) {
                        groupLayers.forEach { (layerBlendMode, layerOpacity) ->
                            drawImage(
                                image = target,
                                alpha = layerOpacity.coerceIn(0f, 1f),
                                blendMode = layerBlendMode,
                            )
                        }
                        if (before != null) drawImage(image = before, alpha = 1f - sheetOpacity)
                    }
                }
            }
            // The final smoothing, before the vignettes so they keep their shape
            boxBlur(output.asAndroidBitmap(), smoothRadius)
            CanvasDrawScope().draw(density, layoutDirection, Canvas(output), outputSize) {
                drawOverlay()
                if (outlineCopies) {
                    // Development aid: a coloured frame around each tile, drawn last so
                    // nothing hides it. In order: magenta, cyan, yellow, green
                    val frameColors = listOf(Color.Magenta, Color.Cyan, Color.Yellow, Color.Green)
                    tiles.forEachIndexed { index, copy ->
                        val tileHeight = copy.height
                        drawRect(
                            color = frameColors[index % frameColors.size],
                            topLeft = Offset(size.width * copy.left, size.height * copy.top),
                            size = Size(size.width * copy.width, size.height * tileHeight),
                            style = Stroke(width = 2f),
                        )
                    }
                }
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
 * The image stretched to the given size, with its edges fading out to transparent. The fades
 * cover [feather] of the width at the left and right, and of the height at the top and bottom
 */
private fun featheredCopy(
    source: ImageBitmap,
    width: Int,
    height: Int,
    feather: Float,
    density: Density,
    layoutDirection: LayoutDirection,
): ImageBitmap {
    val result = ImageBitmap(width, height)
    val size = Size(width.toFloat(), height.toFloat())
    CanvasDrawScope().draw(density, layoutDirection, Canvas(result), size) {
        // A plain stretch, so a tiny source still turns into the same blur
        drawImage(
            image = source,
            dstSize = IntSize(width, height),
            filterQuality = FilterQuality.Low,
        )
        val fade =
            arrayOf(
                0f to Color.Transparent,
                feather to Color.Black,
                (1f - feather) to Color.Black,
                1f to Color.Transparent,
            )
        // Keep the image only where these are solid: once across, once down
        drawRect(brush = Brush.horizontalGradient(colorStops = fade), blendMode = BlendMode.DstIn)
        drawRect(brush = Brush.verticalGradient(colorStops = fade), blendMode = BlendMode.DstIn)
    }
    return result
}

/**
 * Blurs a bitmap in place, see-through parts included. Three passes of a box blur come very close to a Gaussian blur
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
    // Colours are weighted by how solid they are while blurring, so see-through areas do not
    // bleed black into their surroundings
    for (i in pixels.indices) {
        val color = pixels[i]
        val alpha = color ushr 24
        if (alpha != 0xFF) {
            pixels[i] =
                (alpha shl 24) or
                (((color shr 16) and 0xFF) * alpha / 255 shl 16) or
                (((color shr 8) and 0xFF) * alpha / 255 shl 8) or
                ((color and 0xFF) * alpha / 255)
        }
    }
    repeat(3) {
        boxBlurPass(pixels, scratch, width, height, radius, horizontal = true)
        boxBlurPass(scratch, pixels, width, height, radius, horizontal = false)
    }
    for (i in pixels.indices) {
        val color = pixels[i]
        val alpha = color ushr 24
        if (alpha != 0xFF && alpha != 0) {
            pixels[i] =
                (alpha shl 24) or
                (minOf(255, ((color shr 16) and 0xFF) * 255 / alpha) shl 16) or
                (minOf(255, ((color shr 8) and 0xFF) * 255 / alpha) shl 8) or
                minOf(255, (color and 0xFF) * 255 / alpha)
        }
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
        var alpha = 0
        var red = 0
        var green = 0
        var blue = 0
        for (i in -radius..radius) {
            val color = input[at(i)]
            alpha += color ushr 24
            red += (color shr 16) and 0xFF
            green += (color shr 8) and 0xFF
            blue += color and 0xFF
        }
        for (i in 0 until length) {
            output[at(i)] =
                ((alpha / window) shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
            val leaving = input[at(i - radius)]
            val entering = input[at(i + radius + 1)]
            alpha += (entering ushr 24) - (leaving ushr 24)
            red += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}
