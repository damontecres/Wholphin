package com.github.damontecres.dolphin.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.dolphin.preferences.AppThemeColors
import com.github.damontecres.dolphin.ui.theme.colors.BlueThemeColors
import com.github.damontecres.dolphin.ui.theme.colors.GreenThemeColors
import com.github.damontecres.dolphin.ui.theme.colors.OrangeThemeColors
import com.github.damontecres.dolphin.ui.theme.colors.PurpleThemeColors

@Immutable
data class ColorFamily(
    val color: Color,
    val onColor: Color,
    val colorContainer: Color,
    val onColorContainer: Color,
)

val unspecified_scheme =
    ColorFamily(
        Color.Unspecified,
        Color.Unspecified,
        Color.Unspecified,
        Color.Unspecified,
    )

@Composable
fun DolphinTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    appThemeColors: AppThemeColors = AppThemeColors.PURPLE,
    content: @Composable () -> Unit,
) {
    val themeColors =
        when (appThemeColors) {
            AppThemeColors.PURPLE -> PurpleThemeColors
            AppThemeColors.BLUE -> BlueThemeColors
            AppThemeColors.GREEN -> GreenThemeColors
            AppThemeColors.ORANGE -> OrangeThemeColors
            AppThemeColors.UNRECOGNIZED -> PurpleThemeColors
        }

    val colorScheme =
        when {
            darkTheme -> themeColors.darkScheme
            else -> themeColors.lightScheme
        }
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
