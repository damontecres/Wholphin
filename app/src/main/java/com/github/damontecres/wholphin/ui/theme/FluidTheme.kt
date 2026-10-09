package com.github.damontecres.wholphin.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.tv.material3.darkColorScheme
import com.github.damontecres.wholphin.preferences.AppThemeColors

/**
 * What makes one theme differ from another. Every colour in the app is derived from these few
 * values plus the user's highlight colour
 */
data class FluidTheme(
    /** How much of the highlight colour's hue the backgrounds and surfaces carry (0 = neutral grey) */
    val surfaceColor: Float,
    /** Lightness of the app background (0 = pure black) */
    val backgroundLightness: Float,
    /** Strength of the colour glows behind the backdrop image, relative to the value in Backdrop.kt */
    val glow: Float,
)

/** The themes offered in settings, in display order (matches R.array.fluid_themes) */
val fluidThemes =
    listOf(
        AppThemeColors.VIVID,
        AppThemeColors.MUTED,
        AppThemeColors.DARK,
        AppThemeColors.DARKEST,
    )

fun fluidThemeFor(theme: AppThemeColors): FluidTheme =
    when (theme.resolved()) {
        AppThemeColors.VIVID -> FluidTheme(surfaceColor = 0.55f, backgroundLightness = 0.10f, glow = 1f)
        AppThemeColors.DARK -> FluidTheme(surfaceColor = 0f, backgroundLightness = 0.07f, glow = 0.4f)
        AppThemeColors.DARKEST -> FluidTheme(surfaceColor = 0f, backgroundLightness = 0f, glow = 0.2f)
        else -> FluidTheme(surfaceColor = 0.22f, backgroundLightness = 0.09f, glow = 0.65f)
    }

/** Maps the themes inherited from upstream onto the four themes of this fork */
fun AppThemeColors.resolved(): AppThemeColors =
    when (this) {
        AppThemeColors.VIVID,
        AppThemeColors.MUTED,
        AppThemeColors.DARK,
        AppThemeColors.DARKEST,
        -> this

        AppThemeColors.OLED_BLACK -> AppThemeColors.DARKEST

        else -> AppThemeColors.MUTED
    }

/** The highlight colours offered in settings, in display order (matches R.array.highlight_colors) */
val highlightColors =
    listOf(
        Color(0xFF8A4DFF), // Purple
        Color(0xFF3D8BFF), // Blue
        Color(0xFF1FC8C8), // Cyan
        Color(0xFF3DDC84), // Green
        Color(0xFFFFD23F), // Yellow
        Color(0xFFFF8A3D), // Orange
        Color(0xFFFF4D4D), // Red
        Color(0xFFFF5CA8), // Pink
        Color(0xFFF2F2F2), // White
    )

val DefaultHighlight = highlightColors[0]

/** Converts the stored preference (RGB without alpha, 0 = not set) into a colour */
fun highlightFromPreference(value: Int): Color = if (value == 0) DefaultHighlight else Color(value or 0xFF000000.toInt())

/**
 * Builds a full colour scheme from a theme and a highlight colour.
 *
 * Focus states, borders and accents use the highlight. Backgrounds and surfaces share the
 * highlight's hue, with the theme deciding how much of it shows and how dark they are.
 */
fun fluidThemeColors(
    theme: FluidTheme,
    highlight: Color,
): ThemeColors {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(highlight.toArgb(), hsl)
    val hue = hsl[0].coerceIn(0f, 360f)
    val saturation = (hsl[1] * theme.surfaceColor).coerceIn(0f, 1f)

    // A surface colour at the given lightness, tinted as much as the theme allows
    fun tone(lightness: Float) = Color.hsl(hue, saturation, lightness.coerceIn(0f, 1f))

    // Text/icon colour that stays readable on top of the given colour
    fun on(color: Color) = if (color.luminance() > 0.45f) Color(0xFF121212) else Color.White

    val background = tone(theme.backgroundLightness)
    val onBackground = tone(0.92f)
    val surfaceVariant = tone(theme.backgroundLightness + 0.18f)
    val onSurfaceVariant = tone(0.80f)
    val inverseSurface = tone(0.90f)
    val inverseOnSurface = tone(0.18f)

    val primaryContainer = lerp(highlight, Color.Black, 0.25f)
    val secondary = lerp(highlight, onBackground, 0.5f)
    val secondaryContainer = lerp(highlight, background, 0.6f)
    val tertiary = tone(0.85f)
    val tertiaryContainer = lerp(highlight, background, 0.75f)

    val error = Color(0xFFFFB4AB)
    val onError = Color(0xFF690005)
    val errorContainer = Color(0xFF93000A)
    val onErrorContainer = Color(0xFFFFDAD6)

    val material =
        androidx.compose.material3.darkColorScheme(
            primary = highlight,
            onPrimary = on(highlight),
            primaryContainer = primaryContainer,
            onPrimaryContainer = on(primaryContainer),
            secondary = secondary,
            onSecondary = on(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = on(secondaryContainer),
            tertiary = tertiary,
            onTertiary = on(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = on(tertiaryContainer),
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            background = background,
            onBackground = onBackground,
            surface = background,
            onSurface = onBackground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            scrim = Color.Black,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            inversePrimary = highlight,
        )

    val tv =
        darkColorScheme(
            primary = highlight,
            onPrimary = on(highlight),
            primaryContainer = primaryContainer,
            onPrimaryContainer = on(primaryContainer),
            secondary = secondary,
            onSecondary = on(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = on(secondaryContainer),
            tertiary = tertiary,
            onTertiary = on(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = on(tertiaryContainer),
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            background = background,
            onBackground = onBackground,
            surface = background,
            onSurface = onBackground,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            scrim = Color.Black,
            inverseSurface = inverseSurface,
            inverseOnSurface = inverseOnSurface,
            inversePrimary = highlight,
            border = highlight.copy(alpha = .75f),
        )

    // The app only runs in dark mode, so the light schemes reuse the dark ones
    return object : ThemeColors {
        override val lightSchemeMaterial = material
        override val darkSchemeMaterial = material
        override val lightScheme = tv
        override val darkScheme = tv
    }
}
