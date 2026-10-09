package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.preferences.AppThemeColors

val LocalTheme =
    compositionLocalOf<AppThemeColors> { AppThemeColors.MUTED }

/**
 * The values behind the current theme, for UI that adapts to it directly (e.g. the backdrop glows)
 */
val LocalFluidTheme =
    compositionLocalOf { fluidThemeFor(AppThemeColors.MUTED) }

fun getThemeColors(
    appThemeColors: AppThemeColors,
    highlight: Color = DefaultHighlight,
): ThemeColors = fluidThemeColors(fluidThemeFor(appThemeColors), highlight)

@Composable
fun WholphinTheme(
    darkTheme: Boolean = true,
    appThemeColors: AppThemeColors = AppThemeColors.MUTED,
    highlight: Color = DefaultHighlight,
    content: @Composable () -> Unit,
) {
    val theme = appThemeColors.resolved()
    val fluidTheme = fluidThemeFor(theme)
    val themeColors = remember(fluidTheme, highlight) { fluidThemeColors(fluidTheme, highlight) }

    val colorScheme =
        when {
            darkTheme -> themeColors.darkScheme
            else -> themeColors.lightScheme
        }
    CompositionLocalProvider(
        LocalTheme provides theme,
        LocalFluidTheme provides fluidTheme,
    ) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = if (darkTheme) themeColors.darkSchemeMaterial else themeColors.lightSchemeMaterial,
            typography = androidx.compose.material3.Typography(),
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = AppTypography,
                content = content,
            )
        }
    }
}
