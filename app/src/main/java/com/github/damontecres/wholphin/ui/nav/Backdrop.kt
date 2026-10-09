@file:OptIn(ExperimentalCoilApi::class)

package com.github.damontecres.wholphin.ui.nav

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.useExistingImageAsPlaceholder
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.damontecres.wholphin.preferences.BackdropStyle
import com.github.damontecres.wholphin.services.BackdropResult
import com.github.damontecres.wholphin.services.ExtractedColors
import com.github.damontecres.wholphin.ui.CrossFadeFactory
import com.github.damontecres.wholphin.ui.theme.LocalFluidTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shows the current backdrop images provided by [com.github.damontecres.wholphin.services.BackdropService]
 */
@Composable
fun Backdrop(
    drawerIsOpen: Boolean,
    backdropStyle: BackdropStyle,
    viewModel: ApplicationContentViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    useExistingImageAsPlaceholder: Boolean = false,
    crossfadeDuration: Duration = 800.milliseconds,
) {
    val backdrop by viewModel.backdropService.backdropFlow.collectAsStateWithLifecycle()
    Backdrop(
        backdrop = backdrop,
        drawerIsOpen = drawerIsOpen,
        backdropStyle = backdropStyle,
        modifier = modifier,
        enableTopScrim = enableTopScrim,
        useExistingImageAsPlaceholder = useExistingImageAsPlaceholder,
        crossfadeDuration = crossfadeDuration,
        prebakeColors = viewModel.backdropService::extractColorsFromBackdrop,
    )
}

/**
 * Shows the current backdrop images provided by the [BackdropResult]
 */
@Composable
fun Backdrop(
    backdrop: BackdropResult,
    drawerIsOpen: Boolean,
    backdropStyle: BackdropStyle,
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    useExistingImageAsPlaceholder: Boolean = false,
    crossfadeDuration: Duration = 800.milliseconds,
    prebakeColors: (suspend (String?) -> ExtractedColors?)? = null,
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val baseBackgroundColor = MaterialTheme.colorScheme.background

    // How one backdrop gives way to the next, with timings as shares of crossfadeDuration (0.8 s
    // unless a page asks for something else).
    // When focus moves, the background darkens slightly; the sharp image stays as it is. When the
    // new backdrop is ready, the background crossfades to the new one while the darkening lifts,
    // and the sharp image crossfades to the new one a moment later
    val fadeOutShare = 0.4f // how long the background takes to darken
    val fadeInShare = 3f // how long each crossfade takes, and the darkening takes to lift
    val imageDelayShare = 2f // how long the sharp image waits after the background starts
    // How dark the background gets in between: 0 = not at all, 1 = fully black
    val dipDepth = 1f
    val dipColor = Color.Black
    val imageDelay = crossfadeDuration * imageDelayShare.toDouble()
    val fadeOut = crossfadeDuration * fadeOutShare.toDouble()
    val fadeIn = crossfadeDuration * fadeInShare.toDouble()
    // Used by the live (not baked) layers
    val imageFade = fadeIn
    val backgroundFade = fadeIn
    val showGlows =
        backdrop.hasColors &&
            (backdropStyle == BackdropStyle.BACKDROP_DYNAMIC_COLOR || backdropStyle == BackdropStyle.UNRECOGNIZED)
    val animPrimary by animateColorAsState(
        backdrop.primaryColor.takeOrElse { Color.Transparent },
        animationSpec = tween(1250),
        label = "dynamic_backdrop_primary",
    )
    val animSecondary by animateColorAsState(
        backdrop.secondaryColor.takeOrElse { Color.Transparent },
        animationSpec = tween(1250),
        label = "dynamic_backdrop_secondary",
    )
    val animTertiary by animateColorAsState(
        backdrop.tertiaryColor.takeOrElse { Color.Transparent },
        animationSpec = tween(1250),
        label = "dynamic_backdrop_tertiary",
    )
    // Strength of the colour glows behind the image: 1 = upstream's look, higher = more colour.
    // Kept local so Live Edit picks up changes
    // The theme scales this: full strength on Vivid, down to a fraction on Darkest
    val glowIntensity = 2f * LocalFluidTheme.current.glow

    // Dark patch in the bottom corner: how much wider than tall it is (1 = round), and how far
    // its centre sits below the screen edge, as a share of the screen height
    val darkCornerWiden = 3f
    val darkCornerDrop = 0.5f

    fun Color.intensified() = if (isSpecified) copy(alpha = (alpha * glowIntensity).coerceIn(0f, 1f)) else this
    val glowPrimary = animPrimary.intensified()
    val glowSecondary = animSecondary.intensified()
    val glowTertiary = animTertiary.intensified()
    // Paints the theme background and the colour glows in the given colours. Kept as a function
    // so the same drawing serves the live layers and the baked background image
    fun glowPainter(
        glowPrimary: Color,
        glowSecondary: Color,
        glowTertiary: Color,
    ): DrawScope.() -> Unit =
        {
            drawRect(color = baseBackgroundColor)
            val start = if (isRtl) size.width else 0f
            val end = if (isRtl) 0f else size.width
            // Top Left (Vibrant/Muted)
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(glowSecondary, Color.Transparent),
                        center = Offset(start, 0f),
                        radius = size.width * 0.8f,
                    ),
            )
            // Bottom Right (DarkVibrant/DarkMuted)
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(glowPrimary, Color.Transparent),
                        center = Offset(end, size.height),
                        radius = size.width * 0.8f,
                    ),
            )
            // Bottom Left (Dark / Bridge), stretched sideways and pushed down
            val darkCenter = Offset(start, size.height * (1f + darkCornerDrop))
            scale(scaleX = darkCornerWiden, scaleY = 1f, pivot = darkCenter) {
                drawRect(
                    brush =
                        Brush.radialGradient(
                            colors =
                                listOf(
                                    baseBackgroundColor,
                                    Color.Transparent,
                                ),
                            center = darkCenter,
                            radius = size.width * 0.8f,
                        ),
                )
            }
            // Top Right (Under Image - Vibrant/Bright)
            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(glowTertiary, Color.Transparent),
                        center = Offset(end, 0f),
                        radius = size.width * 0.8f,
                    ),
            )
        }
    // Live version, following the animated colours
    val drawGlows = glowPainter(glowPrimary, glowSecondary, glowTertiary)
    // Plain background for when there are no glows to show
    val drawPlain: DrawScope.() -> Unit = { drawRect(color = baseBackgroundColor) }

    // true = the background (glows + blurred layers) is composed once per backdrop into a small
    // image and shown as a single layer, which is much cheaper to draw.
    // false = every layer is drawn live, so the values below respond to Live Edit while tuning
    val bakeBackground = true

    if (showGlows && !bakeBackground) {
        Box(modifier = modifier.fillMaxSize().drawBehind(drawGlows))
    }
    if (backdropStyle != BackdropStyle.BACKDROP_NONE) {
        BoxWithConstraints(
            modifier = modifier.fillMaxSize(),
        ) {
            // Tuning values, kept local so Live Edit picks up changes.
            // Sizes work like CSS viewport units: vw = share of screen width, vh = share of
            // screen height (1f = 100)
            val vw = maxWidth
            val vh = maxHeight
            // shape of the backdrop images themselves (Jellyfin backdrops are 16:9)
            val imageAspect = 16f / 9f

            // width: clamp(min, target, max), with the target tied to the screen's height
            val widthMin = vw * 0.3f
            val widthTarget = vh * 1.2f
            val widthMax = vw * 0.69f
            // tallest the image may get; anything beyond this is cropped
            val heightMax = vh * 1.5f

            val imageWidth = widthTarget.coerceIn(widthMin, widthMax)
            val imageHeight = (imageWidth / imageAspect).coerceAtMost(heightMax)
            // Blurred, oversized copy of the image, between the colour glows and the image itself.
            // The blur comes from loading a tiny version and stretching it over the screen, which
            // works on every Android version; blurRadius adds a real blur on Android 12 and newer
            val blurResolution = 16 // width in pixels of the tiny version: lower = blurrier
            val blurScale = 1.33f // how oversized it is: 1 = fills the screen exactly
            val blurShiftX = 1.5f // how much of the sideways overflow to shift right by
            val blurShiftY = 0.9f // how much of the upward overflow to shift down by
            val blurWiden = 1.2f // extra horizontal stretch: 1 = none
            // Opacity of the whole blurred stack as one group: 0 = glows only, 1 = full effect.
            // The stack keeps its look; this only fades between the two
            val blurOpacity = 0.43f
            // Animated titles (by genre): how much of the blurred copy they keep.
            // 1 = same as everything else, 0 = no blurred copy, just the glows
            val blurOnAnimation = 0f

            fun blurOpacityFor(animated: Boolean) = if (animated) blurOpacity * blurOnAnimation else blurOpacity
            val blurRadius = 30.dp
            // One copy is drawn per line, bottom to top, as "blend mode to opacity". Stack several
            // to combine modes, e.g. SrcOver (normal), Screen, Overlay, Softlight, Hardlight,
            // Multiply, Plus, Luminosity, Color
            val blurLayers =
                listOf(
                    BlendMode.Modulate to 0.9f,
                    BlendMode.Softlight to 0.6f,
                    BlendMode.Hardlight to 0.3f,
                    BlendMode.Darken to 0.3f,
                    BlendMode.Multiply to 1f,
                )
            // Vignettes over the background and the sharp image: clear at the centre, darkening
            // outwards. Each one has:
            // - centerX, centerY: the centre as a share of the screen (0f, 0f is the top-left
            //   corner, 1f, 0f the top-right)
            // - radius: as a share of the screen's diagonal (1 reaches the opposite corner).
            //   With a widthToHeight other than 1 this is the vertical radius
            // - widthToHeight: the shape. 1 = a circle, 2 = twice as wide as tall, 0.5 = twice as
            //   tall as wide
            // - stops: "position to darkness", where position is the share of the way from the
            //   centre to the radius and darkness is how black it gets there (0f = untouched,
            //   1f = black)
            // They are drawn in order, each on top of the one before
            val vignettes =
                listOf(
                    // spotlight on the image
                    Vignette(
                        centerX = 0.72f,
                        centerY = 0.31f,
                        radius = 0.3f,
                        widthToHeight = 1.35f,
                        stops =
                            listOf(
                                0f to 0f,
                                0.48f to 0f,
                                0.55f to 0.05f,
                                0.66f to 0.1f,
                                0.96f to 0.1f,
                                1f to 0.1f,
                            ),
                    ),
                    // edge shade
                    Vignette(
                        centerX = 0.56f,
                        centerY = 0.41f,
                        radius = 1f,
                        widthToHeight = 0.5f,
                        stops =
                            listOf(
                                0f to 0f,
                                0.4f to 0f,
                                1f to 0.88f,
                            ),
                    ),
                )
            // Draws the vignette for a screen of the given size. origin is where the area being
            // drawn into sits on that screen, so a smaller layer (the sharp image) gets the part
            // of the vignette that falls on it
            fun DrawScope.vignette(
                screen: Size,
                origin: Offset,
                mode: BlendMode,
            ) {
                val diagonal = sqrt(screen.width * screen.width + screen.height * screen.height)
                vignettes.forEach { vignette ->
                    val centerX = if (isRtl) 1f - vignette.centerX else vignette.centerX
                    val center =
                        Offset(
                            screen.width * centerX - origin.x,
                            screen.height * vignette.centerY - origin.y,
                        )
                    val stretch = vignette.widthToHeight.coerceAtLeast(0.01f)
                    // Stretch a circle sideways into an ellipse. The rectangle is sized so that,
                    // once stretched, it still covers the whole area
                    scale(scaleX = stretch, scaleY = 1f, pivot = center) {
                        val left = center.x - center.x / stretch
                        val right = center.x + (size.width - center.x) / stretch
                        drawRect(
                            brush =
                                Brush.radialGradient(
                                    colorStops =
                                        vignette.stops
                                            .map { (position, darkness) ->
                                                position to Color.Black.copy(alpha = darkness)
                                            }.toTypedArray(),
                                    center = center,
                                    radius = diagonal * vignette.radius,
                                ),
                            topLeft = Offset(left, 0f),
                            size = Size(right - left, size.height),
                            blendMode = mode,
                        )
                    }
                }
            }
            val drawVignette: DrawScope.() -> Unit = { vignette(size, Offset.Zero, BlendMode.SrcOver) }
            val screenWidthPx = constraints.maxWidth.toFloat()
            val screenHeightPx = constraints.maxHeight.toFloat()
            // What is on screen right now. It trails behind the backdrop that has been asked for:
            // the change only happens once the new background is ready and the dimming is done
            val shownImageUrl = remember { mutableStateOf(backdrop.imageUrl) }
            val shownBackground = remember { mutableStateOf<ImageBitmap?>(null) }
            // The sharp image that has finished loading, to know when it is safe to lift the black
            val loadedImageUrl = remember { mutableStateOf<String?>(null) }
            // How much black is over the backdrop at the moment, 0 to 1
            val dip = remember { Animatable(0f) }
            if (bakeBackground) {
                val context = LocalContext.current
                val density = LocalDensity.current
                val layoutDirection = LocalLayoutDirection.current
                // Size of the baked image; it is soft everywhere, so it can be small
                val bakeWidth = 480
                // Extra smoothing of the baked glows and blur, as a share of the screen width:
                // 0 = none, 0.01 = a soft blur about 1% of the screen wide. Only applies when baked
                val bakeSmooth = 0.0099f
                val bakeHeight = (bakeWidth * (maxHeight / maxWidth)).toInt().coerceAtLeast(1)
                // The baked version uses the colours the glows are heading for, not the animated
                // ones, so it is composed once per backdrop instead of on every animation frame
                fun targetGlow(color: Color) = color.takeOrElse { Color.Transparent }.intensified()

                // Everything a baked image depends on; also its key in the cache
                fun bakeKeyFor(
                    imageUrl: String?,
                    primary: Color,
                    secondary: Color,
                    tertiary: Color,
                    glows: Boolean,
                    animated: Boolean,
                ): List<Any?> =
                    listOf(
                        imageUrl,
                        listOf(primary, secondary, tertiary, baseBackgroundColor),
                        listOf(glows, isRtl, bakeWidth, bakeHeight, darkCornerWiden, darkCornerDrop),
                        listOf(blurResolution, blurScale, blurWiden, blurShiftX, blurShiftY, blurOpacityFor(animated)),
                        blurLayers,
                        vignettes,
                        bakeSmooth,
                    )

                suspend fun bakeFor(
                    imageUrl: String?,
                    primary: Color,
                    secondary: Color,
                    tertiary: Color,
                    glows: Boolean,
                    animated: Boolean,
                ): ImageBitmap? =
                    bakeBackdropBackground(
                        context = context,
                        imageUrl = imageUrl,
                        width = bakeWidth,
                        height = bakeHeight,
                        density = density,
                        layoutDirection = layoutDirection,
                        resolution = blurResolution,
                        imageAspect = imageAspect,
                        scaleX = blurScale * blurWiden,
                        scaleY = blurScale,
                        shiftX = blurShiftX,
                        shiftY = blurShiftY,
                        layers = blurLayers,
                        groupOpacity = blurOpacityFor(animated),
                        drawBackground = if (glows) glowPainter(primary, secondary, tertiary) else drawPlain,
                        drawOverlay = drawVignette,
                        smoothRadius = (bakeWidth * bakeSmooth).roundToInt(),
                    )

                val targetPrimary = targetGlow(backdrop.primaryColor)
                val targetSecondary = targetGlow(backdrop.secondaryColor)
                val targetTertiary = targetGlow(backdrop.tertiaryColor)
                val bakeKey =
                    bakeKeyFor(
                        backdrop.imageUrl,
                        targetPrimary,
                        targetSecondary,
                        targetTertiary,
                        showGlows,
                        backdrop.isAnimated,
                    )
                // The finished background, together with the image it was made for
                val baked by produceState<Pair<String?, ImageBitmap>?>(null, bakeKey, backdrop.colorsPending) {
                    if (backdrop.imageUrl == null) {
                        // No image: either the page was left (clear the background) or a new
                        // backdrop is on its way (keep showing the current one until it arrives)
                        if (!backdrop.hasColors) value = null
                        return@produceState
                    }
                    // The colours for this image are not in yet; wait for them
                    if (backdrop.colorsPending) return@produceState
                    // Already made for this backdrop
                    bakedBackgrounds.get(bakeKey)?.let {
                        value = backdrop.imageUrl to it
                        return@produceState
                    }
                    val result =
                        bakeFor(
                            backdrop.imageUrl,
                            targetPrimary,
                            targetSecondary,
                            targetTertiary,
                            showGlows,
                            backdrop.isAnimated,
                        )
                    // Keep showing the previous background if this one could not be made
                    if (result != null) {
                        bakedBackgrounds.put(bakeKey, result)
                        value = backdrop.imageUrl to result
                    }
                }

                // Runs the change from one backdrop to the next. It restarts whenever the
                // backdrop or its background changes, and picks up from however dark it is
                LaunchedEffect(backdrop.itemId, backdrop.imageUrl, backdrop.colorsPending, baked) {
                    val imageUrl = backdrop.imageUrl
                    val depth = dipDepth.coerceIn(0f, 1f)
                    val dimming = tween<Float>(fadeOut.inWholeMilliseconds.toInt())
                    val lifting = tween<Float>(fadeIn.inWholeMilliseconds.toInt())
                    val ready = baked
                    if (backdrop.colorsPending) {
                        // Focus has moved and the new backdrop is not complete yet: darken the
                        // background and wait
                        dip.animateTo(depth, dimming)
                    } else if (imageUrl == null) {
                        // Nothing to show (the page was left, or the item has no backdrop)
                        dip.animateTo(depth, dimming)
                        shownImageUrl.value = null
                        if (!backdrop.hasColors) shownBackground.value = null
                        dip.animateTo(0f, lifting)
                    } else if (ready != null && ready.first == imageUrl) {
                        if (shownBackground.value !== ready.second) {
                            dip.animateTo(depth, dimming)
                            shownBackground.value = ready.second
                        }
                        coroutineScope {
                            // The sharp image follows a moment after the background
                            launch {
                                if (shownImageUrl.value != imageUrl) {
                                    delay(imageDelay)
                                    shownImageUrl.value = imageUrl
                                }
                            }
                            dip.animateTo(0f, lifting)
                        }
                    }
                    // Otherwise the background for this image is still being made; this runs
                    // again when it arrives
                }

                // Preparing backgrounds ahead of time, for the items whose cards are on screen.
                // The colours are found the same way as when an item gets focus, so what is
                // prepared here is exactly what the lookup above will ask for later
                if (prebakeColors != null) {
                    val dynamicColors =
                        backdropStyle == BackdropStyle.BACKDROP_DYNAMIC_COLOR ||
                            backdropStyle == BackdropStyle.UNRECOGNIZED
                    val prebake: suspend (String, Boolean) -> Unit = { imageUrl, animated ->
                        val colors = if (dynamicColors) prebakeColors(imageUrl) else ExtractedColors.DEFAULT
                        if (colors != null) {
                            val glows =
                                dynamicColors &&
                                    (colors.primary.isSpecified || colors.secondary.isSpecified || colors.tertiary.isSpecified)
                            val primary = targetGlow(colors.primary)
                            val secondary = targetGlow(colors.secondary)
                            val tertiary = targetGlow(colors.tertiary)
                            val key = bakeKeyFor(imageUrl, primary, secondary, tertiary, glows, animated)
                            if (bakedBackgrounds.get(key) == null) {
                                bakeFor(imageUrl, primary, secondary, tertiary, glows, animated)?.let {
                                    bakedBackgrounds.put(key, it)
                                }
                            }
                        }
                    }
                    SideEffect { BackdropPrebaker.setBaker(prebake) }
                    DisposableEffect(Unit) {
                        onDispose { BackdropPrebaker.setBaker(null) }
                    }
                }
                Crossfade(
                    targetState = shownBackground.value,
                    animationSpec = tween(fadeIn.inWholeMilliseconds.toInt()),
                    label = "baked_backdrop_background",
                ) { image ->
                    if (image != null) {
                        Image(
                            bitmap = image,
                            contentDescription = null,
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (showGlows) {
                        // Until the first image is ready, show the plain glows
                        Box(modifier = Modifier.fillMaxSize().drawBehind(drawGlows))
                    }
                }
            } else {
                val blurRequest =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(backdrop.imageUrl)
                        .size(blurResolution, (blurResolution / imageAspect).toInt().coerceAtLeast(1))
                        .transitionFactory(CrossFadeFactory(backgroundFade))
                        .build()
                blurLayers.forEach { (layerBlendMode, layerOpacity) ->
                    AsyncImage(
                        model = blurRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    // negative = mirrored left to right
                                    scaleX = blurScale * blurWiden
                                    scaleY = blurScale
                                    // The scaling makes it overflow each edge by this share of its
                                    // size. Shift it right and down by a factor of that overflow:
                                    // 1 lines its left/top edge up with the screen's, 0 leaves it centred
                                    val overflow = (blurScale - 1f) / 2f
                                    translationX = size.width * overflow * blurShiftX
                                    translationY = size.height * overflow * blurShiftY
                                    alpha = layerOpacity
                                    blendMode = layerBlendMode
                                }.blur(blurRadius),
                    )
                }
                // Group opacity: the background and glows drawn again over the blurred stack, fading
                // it back towards the plain glows as blurOpacity goes down
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = 1f - blurOpacityFor(backdrop.isAnimated).coerceIn(0f, 1f) }
                            .drawBehind {
                                if (showGlows) drawGlows() else drawRect(color = baseBackgroundColor)
                            },
                )
                Box(modifier = Modifier.fillMaxSize().drawBehind(drawVignette))
            }
            // The darkening that comes over the background while the backdrop changes. It sits under
            // the sharp image, so the image itself is not darkened
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            if (dip.value > 0f) drawRect(color = dipColor, alpha = dip.value.coerceIn(0f, 1f))
                        },
            )
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        // When baked, the image changes together with the background (see above)
                        // and crossfades from the previous one
                        .data(if (bakeBackground) shownImageUrl.value else backdrop.imageUrl)
                        .useExistingImageAsPlaceholder(bakeBackground || useExistingImageAsPlaceholder)
                        .transitionFactory(CrossFadeFactory(imageFade))
                        .build(),
                onSuccess = { loadedImageUrl.value = it.result.request.data as? String },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(imageWidth, imageHeight)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            val start = if (isRtl) size.width else 0f
                            drawContent()
                            if (drawerIsOpen) {
                                drawRect(
                                    brush = SolidColor(Color.Black),
                                    alpha = .75f,
                                    // Only darken the image itself, not the empty box before it loads
                                    blendMode = BlendMode.SrcAtop,
                                )
                            }
                            // Subtle top scrim for system UI readability (clock, tabs)
                            if (enableTopScrim) {
                                drawRect(
                                    brush =
                                        Brush.verticalGradient(
                                            colorStops =
                                                arrayOf(
                                                    0f to Color.Black.copy(alpha = TOP_SCRIM_ALPHA),
                                                    TOP_SCRIM_END_FRACTION to Color.Transparent,
                                                ),
                                        ),
                                    // Only darken the image itself, not the empty box before it loads
                                    blendMode = BlendMode.SrcAtop,
                                )
                            }
                            // The same vignette as on the background, continued over the image.
                            // SrcAtop darkens only the image itself, not the empty box around it
                            vignette(
                                screen = Size(screenWidthPx, screenHeightPx),
                                origin = Offset(if (isRtl) 0f else screenWidthPx - size.width, 0f),
                                mode = BlendMode.SrcAtop,
                            )
                            // The image's mask, written like CSS gradient stops: each line is
                            // "position to opacity", where position is the share of the way
                            // across (0f = 0%, 1f = 100%) and opacity is how visible the image is
                            // there (0f = invisible, 1f = solid). Add as many stops as you like,
                            // in order of position.

                            // Side fade, measured from the image's inner edge towards the screen edge
                            val horizontalFade =
                                listOf(
                                    0f to 0f,
                                    0.18f to 0.3f,
                                    0.35f to 0.5f,
                                    0.45f to 0.8f,
                                    0.75f to 0.68f,
                                    1f to 0.9f,
                                )
                            // Vertical fade, measured from the top of the image to the bottom
                            val verticalFade =
                                listOf(
                                    0f   to 1f,
                                    0.8f to 1f,
                                    0.92f to 0.4f,
                                    1f   to 0f,
                                )

                            fun List<Pair<Float, Float>>.asMask() =
                                map { (position, opacity) -> position to Color.Black.copy(alpha = opacity) }
                                    .toTypedArray()
                            drawRect(
                                brush =
                                    Brush.horizontalGradient(
                                        colorStops = horizontalFade.asMask(),
                                        startX = start,
                                        endX = size.width - start,
                                    ),
                                blendMode = BlendMode.DstIn,
                            )
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colorStops = verticalFade.asMask(),
                                        startY = 0f,
                                        endY = size.height,
                                    ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
            )
            // Film grain over everything: the baked background and the sharp image.
            // It has to be drawn at full screen resolution, so it is its own layer on top and
            // cannot be part of the small baked image
            val grainOpacity = 0.4f // strength: 0 = none
            // Animated titles (by genre): how much grain they keep. 1 = same as everything else,
            // 0 = none. The change is faded so it does not pop when moving between titles
            val grainOnAnimation = 0f
            val grainFactor by animateFloatAsState(
                targetValue = if (backdrop.isAnimated) grainOnAnimation else 1f,
                animationSpec = tween(backgroundFade.inWholeMilliseconds.toInt()),
                label = "grain_on_animation",
            )
            val grainSize = 0.25f // size of one grain in screen pixels: higher = coarser
            val grainContrast = 0.2f // range of greys: 0 = all neutral (no grain), 1 = black to white
            // how much the grain gathers into uneven patches: 0 = evenly fine, 1 = all patches
            val grainClumping = 0.45f
            val grainBlendMode = BlendMode.Softlight
            val grainBrush =
                remember(grainContrast, grainClumping) {
                    ShaderBrush(
                        ImageShader(
                            grainTile(grainContrast, grainClumping),
                            TileMode.Repeated,
                            TileMode.Repeated,
                        ),
                    )
                }
            // The pattern is moved to a new random position whenever a new backdrop arrives, so
            // each backdrop gets what looks like fresh grain at no extra cost
            val grainShift = remember { mutableStateOf(Offset.Zero) }
            val grainBackdrop = if (bakeBackground) shownImageUrl.value else backdrop.imageUrl
            LaunchedEffect(grainBackdrop) {
                if (grainBackdrop != null) {
                    grainShift.value =
                        Offset(
                            Random.nextFloat() * GRAIN_TILE_SIZE,
                            Random.nextFloat() * GRAIN_TILE_SIZE,
                        )
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .drawBehind {
                            if (grainOpacity * grainFactor > 0f) {
                                val grain = grainSize.coerceAtLeast(0.25f)
                                val shift = grainShift.value
                                scale(scaleX = grain, scaleY = grain, pivot = Offset.Zero) {
                                    translate(left = -shift.x, top = -shift.y) {
                                    drawRect(
                                        brush = grainBrush,
                                        topLeft = shift,
                                        size = Size(size.width / grain, size.height / grain),
                                        alpha = (grainOpacity * grainFactor).coerceIn(0f, 1f),
                                        blendMode = grainBlendMode,
                                    )
                                    }
                                }
                            }
                        },
            )
        }
    }
}

/**
 * One vignette: see the list of them in [Backdrop] for what the values mean
 */
private data class Vignette(
    val centerX: Float,
    val centerY: Float,
    val radius: Float,
    val widthToHeight: Float,
    val stops: List<Pair<Float, Float>>,
)
