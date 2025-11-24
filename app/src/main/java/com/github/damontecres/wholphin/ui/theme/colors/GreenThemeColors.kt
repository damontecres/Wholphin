package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val GreenThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF316A42)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFB3F1BF)
        val onPrimaryContainerLight = Color(0xFF16512C)
        val secondaryLight = Color(0xFF506352)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFD2E8D3)
        val onSecondaryContainerLight = Color(0xFF394B3C)
        val tertiaryLight = Color(0xFF3A656E)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFBDEAF5)
        val onTertiaryContainerLight = Color(0xFF204D56)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFF6FBF3)
        val onBackgroundLight = Color(0xFF181D18)
        val surfaceLight = Color(0xFFF6FBF3)
        val onSurfaceLight = Color(0xFF181D18)
        val surfaceVariantLight = Color(0xFFDDE5DA)
        val onSurfaceVariantLight = Color(0xFF414941)
        val outlineLight = Color(0xFF717971)
        val outlineVariantLight = Color(0xFFC1C9BF)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF2D322D)
        val inverseOnSurfaceLight = Color(0xFFEEF2EA)
        val inversePrimaryLight = Color(0xFF98D5A4)
        val surfaceDimLight = Color(0xFFD7DBD4)
        val surfaceBrightLight = Color(0xFFF6FBF3)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFF0F5ED)
        val surfaceContainerLight = Color(0xFFEBEFE7)
        val surfaceContainerHighLight = Color(0xFFE5EAE2)

        val primaryDark = Color(0xFF98D5A4)
        val onPrimaryDark = Color(0xFF00391A)
        val primaryContainerDark = Color(0xFF16512C)
        val onPrimaryContainerDark = Color(0xFFB3F1BF)
        val secondaryDark = Color(0xFFB7CCB7)
        val onSecondaryDark = Color(0xFF223526)
        val secondaryContainerDark = Color(0xFF394B3C)
        val onSecondaryContainerDark = Color(0xFFD2E8D3)
        val tertiaryDark = Color(0xFF81D29C)
        val onTertiaryDark = Color(0xFF01363F)
        val tertiaryContainerDark = Color(0xFF204D56)
        val onTertiaryContainerDark = Color(0xFFBDEAF5)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF101510)
        val onBackgroundDark = Color(0xFFDFE4DC)
        val surfaceDark = Color(0xFF101510)
        val onSurfaceDark = Color(0xFFDFE4DC)
        val surfaceVariantDark = Color(0xFF414941)
        val onSurfaceVariantDark = Color(0xFFC1C9BF)
        val outlineDark = Color(0xFF8B938A)
        val outlineVariantDark = Color(0xFF414941)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFDFE4DC)
        val inverseOnSurfaceDark = Color(0xFF2D322D)
        val inversePrimaryDark = Color(0xFF39912F)
        val surfaceDimDark = Color(0xFF101510)
        val surfaceBrightDark = Color(0xFF353A35)
        val surfaceContainerLowestDark = Color(0xFF0B0F0B)
        val surfaceContainerLowDark = Color(0xFF181D18)
        val surfaceContainerDark = Color(0xFF1C211C)
        val surfaceContainerHighDark = Color(0xFF262B26)
        val surfaceContainerHighestDark = Color(0xFF313631)

        override val lightSchemeMaterial: ColorScheme =
            androidx.compose.material3.lightColorScheme(
                primary = primaryLight,
                onPrimary = onPrimaryLight,
                primaryContainer = primaryContainerLight,
                onPrimaryContainer = onPrimaryContainerLight,
                secondary = secondaryLight,
                onSecondary = onSecondaryLight,
                secondaryContainer = secondaryContainerLight,
                onSecondaryContainer = onSecondaryContainerLight,
                tertiary = tertiaryLight,
                onTertiary = onTertiaryLight,
                tertiaryContainer = tertiaryContainerLight,
                onTertiaryContainer = onTertiaryContainerLight,
                error = errorLight,
                onError = onErrorLight,
                errorContainer = errorContainerLight,
                onErrorContainer = onErrorContainerLight,
                background = backgroundLight,
                onBackground = onBackgroundLight,
                surface = surfaceLight,
                onSurface = onSurfaceLight,
                surfaceVariant = surfaceVariantLight,
                onSurfaceVariant = onSurfaceVariantLight,
                scrim = scrimLight,
                inverseSurface = inverseSurfaceLight,
                inverseOnSurface = inverseOnSurfaceLight,
                inversePrimary = inversePrimaryLight,
            )

        override val lightScheme =
            lightColorScheme(
                primary = primaryLight,
                onPrimary = onPrimaryLight,
                primaryContainer = primaryContainerLight,
                onPrimaryContainer = onPrimaryContainerLight,
                secondary = secondaryLight,
                onSecondary = onSecondaryLight,
                secondaryContainer = secondaryContainerLight,
                onSecondaryContainer = onSecondaryContainerLight,
                tertiary = tertiaryLight,
                onTertiary = onTertiaryLight,
                tertiaryContainer = tertiaryContainerLight,
                onTertiaryContainer = onTertiaryContainerLight,
                error = errorLight,
                onError = onErrorLight,
                errorContainer = errorContainerLight,
                onErrorContainer = onErrorContainerLight,
                background = backgroundLight,
                onBackground = onBackgroundLight,
                surface = surfaceLight,
                onSurface = onSurfaceLight,
                surfaceVariant = surfaceVariantLight,
                onSurfaceVariant = onSurfaceVariantLight,
                scrim = scrimLight,
                inverseSurface = inverseSurfaceLight,
                inverseOnSurface = inverseOnSurfaceLight,
                inversePrimary = inversePrimaryLight,
                border = inversePrimaryLight,
            )

        override val darkSchemeMaterial =
            androidx.compose.material3.darkColorScheme(
                primary = primaryDark,
                onPrimary = onPrimaryDark,
                primaryContainer = primaryContainerDark,
                onPrimaryContainer = onPrimaryContainerDark,
                secondary = secondaryDark,
                onSecondary = onSecondaryDark,
                secondaryContainer = secondaryContainerDark,
                onSecondaryContainer = onSecondaryContainerDark,
                tertiary = tertiaryDark,
                onTertiary = onTertiaryDark,
                tertiaryContainer = tertiaryContainerDark,
                onTertiaryContainer = onTertiaryContainerDark,
                error = errorDark,
                onError = onErrorDark,
                errorContainer = errorContainerDark,
                onErrorContainer = onErrorContainerDark,
                background = backgroundDark,
                onBackground = onBackgroundDark,
                surface = surfaceDark,
                onSurface = onSurfaceDark,
                surfaceVariant = surfaceVariantDark,
                onSurfaceVariant = onSurfaceVariantDark,
                scrim = scrimDark,
                inverseSurface = inverseSurfaceDark,
                inverseOnSurface = inverseOnSurfaceDark,
                inversePrimary = inversePrimaryDark,
            )

        override val darkScheme =
            darkColorScheme(
                primary = primaryDark,
                onPrimary = onPrimaryDark,
                primaryContainer = primaryContainerDark,
                onPrimaryContainer = onPrimaryContainerDark,
                secondary = secondaryDark,
                onSecondary = onSecondaryDark,
                secondaryContainer = secondaryContainerDark,
                onSecondaryContainer = onSecondaryContainerDark,
                tertiary = tertiaryDark,
                onTertiary = onTertiaryDark,
                tertiaryContainer = tertiaryContainerDark,
                onTertiaryContainer = onTertiaryContainerDark,
                error = errorDark,
                onError = onErrorDark,
                errorContainer = errorContainerDark,
                onErrorContainer = onErrorContainerDark,
                background = backgroundDark,
                onBackground = onBackgroundDark,
                surface = surfaceDark,
                onSurface = onSurfaceDark,
                surfaceVariant = surfaceVariantDark,
                onSurfaceVariant = onSurfaceVariantDark,
                scrim = scrimDark,
                inverseSurface = inverseSurfaceDark,
                inverseOnSurface = inverseOnSurfaceDark,
                inversePrimary = inversePrimaryDark,
                border = inversePrimaryDark.copy(alpha = .75f),
            )
    }
