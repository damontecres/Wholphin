package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val RedThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF904A42)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFFFDAD5)
        val onPrimaryContainerLight = Color(0xFF73342C)
        val secondaryLight = Color(0xFF775652)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFFFDAD5)
        val onSecondaryContainerLight = Color(0xFF5D3F3B)
        val tertiaryLight = Color(0xFF715C2E)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFFDDFA6)
        val onTertiaryContainerLight = Color(0xFF574419)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFFFF8F7)
        val onBackgroundLight = Color(0xFF231918)
        val surfaceLight = Color(0xFFFFF8F7)
        val onSurfaceLight = Color(0xFF231918)
        val surfaceVariantLight = Color(0xFFF5DDDA)
        val onSurfaceVariantLight = Color(0xFF534341)
        val outlineLight = Color(0xFF857370)
        val outlineVariantLight = Color(0xFFD8C2BE)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF392E2C)
        val inverseOnSurfaceLight = Color(0xFFFFEDEA)
        val inversePrimaryLight = Color(0xFFFFB4AA)
        val surfaceDimLight = Color(0xFFE8D6D4)
        val surfaceBrightLight = Color(0xFFFFF8F7)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFFFF0EE)
        val surfaceContainerLight = Color(0xFFFCEAE7)
        val surfaceContainerHighLight = Color(0xFFF7E4E2)
        val surfaceContainerHighestLight = Color(0xFFF1DEDC)

        val primaryDark = Color(0xFFFFB4AA)
        val onPrimaryDark = Color(0xFF561E18)
        val primaryContainerDark = Color(0xFF73342C)
        val onPrimaryContainerDark = Color(0xFFFFDAD5)
        val secondaryDark = Color(0xFFE7BDB7)
        val onSecondaryDark = Color(0xFF442926)
        val secondaryContainerDark = Color(0xFF5D3F3B)
        val onSecondaryContainerDark = Color(0xFFFFDAD5)
        val tertiaryDark = Color(0xFFDFC38C)
        val onTertiaryDark = Color(0xFF3F2E04)
        val tertiaryContainerDark = Color(0xFF574419)
        val onTertiaryContainerDark = Color(0xFFFDDFA6)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF1A1110)
        val onBackgroundDark = Color(0xFFF1DEDC)
        val surfaceDark = Color(0xFF1A1110)
        val onSurfaceDark = Color(0xFFF1DEDC)
        val surfaceVariantDark = Color(0xFF534341)
        val onSurfaceVariantDark = Color(0xFFD8C2BE)
        val outlineDark = Color(0xFFA08C8A)
        val outlineVariantDark = Color(0xFF534341)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFF1DEDC)
        val inverseOnSurfaceDark = Color(0xFF392E2C)
        val inversePrimaryDark = Color(0xFFC0000C)
        val surfaceDimDark = Color(0xFF1A1110)
        val surfaceBrightDark = Color(0xFF423735)
        val surfaceContainerLowestDark = Color(0xFF140C0B)
        val surfaceContainerLowDark = Color(0xFF231918)
        val surfaceContainerDark = Color(0xFF271D1C)
        val surfaceContainerHighDark = Color(0xFF322826)
        val surfaceContainerHighestDark = Color(0xFF3D3231)

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
                border = inversePrimaryDark,
            )
    }
