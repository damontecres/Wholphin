package com.github.damontecres.wholphin.services

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import androidx.datastore.core.DataStore
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.theme.getThemeColors
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(FlowPreview::class)
class BackdropService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val imageUrlService: ImageUrlService,
        private val preferences: DataStore<AppPreferences>,
    ) {
        private val extractedColorCache = LruCache<String, ExtractedColors>(50)

        private val _backdropFlow =
            MutableStateFlow<BackdropRequest>(
                BackdropRequest(
                    null,
                    false,
                ),
            )

        val backdropFlow =
            _backdropFlow
                .map {
                    val prefs =
                        preferences.data.firstOrNull() ?: AppPreferences.getDefaultInstance()
                    val theme = getThemeColors(prefs.interfacePreferences.appThemeColors).darkScheme
                    BackdropResult(
                        item = it.item,
                        imageUrl = imageUrlService.getItemImageUrl(it.item, ImageType.BACKDROP),
                        fillWidth = if (it.small) .7f else .85f,
                        fillHeight = if (it.small) .7f else 1f,
                    )
                }

        suspend fun submit(
            item: BaseItem,
            small: Boolean = true,
        ) = withContext(Dispatchers.IO) {
            val imageUrl = imageUrlService.getItemImageUrl(item, ImageType.BACKDROP)
            if (backdropFlow.firstOrNull()?.imageUrl != imageUrl) {
                _backdropFlow.emit(
                    BackdropRequest(
                        null,
                        small,
                    ),
                )
                extractColors(item, small)
            }
        }

        private suspend fun extractColors(
            item: BaseItem,
            small: Boolean,
        ) {
            delay(500)
            val colors =
                extractColorsFromBackdrop(imageUrlService.getItemImageUrl(item, ImageType.BACKDROP))
            _backdropFlow.emit(
                BackdropRequest(
                    item,
                    small,
                    dynamicColorPrimary = colors?.primary ?: Color.Unspecified,
                    dynamicColorSecondary = colors?.secondary ?: Color.Unspecified,
                    dynamicColorTertiary = colors?.tertiary ?: Color.Unspecified,
                ),
            )
        }

        private suspend fun extractColorsFromBackdrop(imageUrl: String?): ExtractedColors? =
            withContext(Dispatchers.IO) {
                if (imageUrl.isNullOrBlank()) {
                    return@withContext null
                }
                extractedColorCache.get(imageUrl)?.let {
                    return@withContext it
                }

                try {
                    val loader = context.imageLoader
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(imageUrl)
                            .allowHardware(false)
                            .bitmapConfig(Bitmap.Config.ARGB_8888)
                            .build()

                    val result = loader.execute(request)
                    if (result is SuccessResult) {
                        val drawable = result.image.asDrawable(context.resources)
                        val bitmap = drawable.toBitmap(config = Bitmap.Config.ARGB_8888)
                        extractColorsFromBitmap(bitmap)?.let {
                            extractedColorCache.put(imageUrl, it)
                        }
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error extracting colors from URL: $imageUrl")
                    null
                }
            }

        /**
         * Helper function to determine if a color is "cool" (blue/purple/green) vs "warm" (red/orange/yellow)
         *
         * Cool colors have more blue/green than red
         */
        private val Palette.Swatch.coolColor: Boolean
            get() {
                val r = (rgb shr 16) and 0xFF
                val g = (rgb shr 8) and 0xFF
                val b = rgb and 0xFF
                return b > r && (b + g) > (r * 1.5f)
            }

        private fun toColor(
            swatch: Palette.Swatch?,
            alpha: Float,
        ): Color = swatch?.rgb?.let(::Color)?.copy(alpha = alpha) ?: Color.Transparent

        /**
         * Extracts colors from a bitmap using Android's Palette API.
         *
         * - Primary (Bottom-Right): darkVibrant -> darkMuted -> default
         * - Secondary (Top-Left): Smart selection based on color temperature (prefers cool colors)
         * - Tertiary (Top-Right): vibrant -> lightVibrant -> default
         *
         * @param bitmap The bitmap to extract colors from
         * @return ExtractedColors containing primary, secondary, and tertiary colors
         */
        private suspend fun extractColorsFromBitmap(bitmap: Bitmap): ExtractedColors? =
            try {
                val palette = Palette.from(bitmap).generate()

                val vibrant = palette.vibrantSwatch
                val darkVibrant = palette.darkVibrantSwatch
                val lightVibrant = palette.lightVibrantSwatch
                val muted = palette.mutedSwatch
                val darkMuted = palette.darkMutedSwatch
                val lightMuted = palette.lightMutedSwatch
                val dominant = palette.dominantSwatch

                // Primary (Bottom-Right)
                val primaryColor = toColor(darkVibrant ?: darkMuted, .4f)

                // Secondary (Top-Left): Smart selection based on color properties
                // If Vibrant is cool (blue/purple), use it. If Vibrant is warm (yellow/orange) and Muted is cool, use Muted.
                // This ensures we get cool tones (blue/purple) for top-left when available
                val secondaryColor =
                    when {
                        vibrant != null && vibrant.coolColor -> vibrant
                        muted != null && muted.coolColor -> muted
                        vibrant != null -> vibrant
                        muted != null -> muted
                        else -> null
                    }.let { toColor(it, .4f) }

                // Tertiary (Top-Right under image)
                val tertiaryColor = toColor(vibrant ?: lightVibrant, .35f)

                Timber.v(
                    "ColorExtractor: Primary=%X (alpha=0.4), Secondary=%X (alpha=0.4), Tertiary=%X (alpha=0.35)",
                    primaryColor,
                    secondaryColor,
                    tertiaryColor,
                )
                Timber.v(
                    "ColorExtractor: Palette: Vibrant=%X, DarkVibrant=%X, LightVibrant=%X, Muted=%X, DarkMuted=%X, LightMuted=%X, Dominant=%X",
                    vibrant?.rgb,
                    darkVibrant?.rgb,
                    lightVibrant?.rgb,
                    muted?.rgb,
                    darkMuted?.rgb,
                    lightMuted?.rgb,
                    dominant?.rgb,
                )
                ExtractedColors(primaryColor, secondaryColor, tertiaryColor)
            } catch (e: Exception) {
                Timber.e(e, "ColorExtractor: Error extracting palette colors")
                null
            }

        fun clearColorCache() {
            extractedColorCache.evictAll()
            Timber.d("ColorExtractor: Cache cleared")
        }
    }

data class BackdropRequest(
    val item: BaseItem?,
    val small: Boolean,
    val dynamicColorPrimary: Color = Color.Unspecified,
    val dynamicColorSecondary: Color = Color.Unspecified,
    val dynamicColorTertiary: Color = Color.Unspecified,
)

data class BackdropResult(
    val item: BaseItem?,
    val imageUrl: String?,
    val fillWidth: Float,
    val fillHeight: Float,
    val dynamicColorPrimary: Color = Color.Unspecified,
    val dynamicColorSecondary: Color = Color.Unspecified,
    val dynamicColorTertiary: Color = Color.Unspecified,
)

data class ExtractedColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
)
