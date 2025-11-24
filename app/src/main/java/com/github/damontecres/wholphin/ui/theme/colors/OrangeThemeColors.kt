package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val OrangeThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF725C0C)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFFFE086)
        val onPrimaryContainerLight = Color(0xFF574500)
        val secondaryLight = Color(0xFF685E3F)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFF1E1BB)
        val onSecondaryContainerLight = Color(0xFF50462A)
        val tertiaryLight = Color(0xFF855317)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFFFDCBD)
        val onTertiaryContainerLight = Color(0xFF693C00)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFFFF8F0)
        val onBackgroundLight = Color(0xFF1E1B13)
        val surfaceLight = Color(0xFFFFF8F0)
        val onSurfaceLight = Color(0xFF1E1B13)
        val surfaceVariantLight = Color(0xFFEBE2CF)
        val onSurfaceVariantLight = Color(0xFF4C4639)
        val outlineLight = Color(0xFF7D7667)
        val outlineVariantLight = Color(0xFFCEC6B4)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF343027)
        val inverseOnSurfaceLight = Color(0xFFF8F0E2)
        val inversePrimaryLight = Color(0xFFE1C46D)
        val surfaceDimLight = Color(0xFFE1D9CC)
        val surfaceBrightLight = Color(0xFFFFF8F0)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFFBF3E5)
        val surfaceContainerLight = Color(0xFFF5EDDF)
        val surfaceContainerHighLight = Color(0xFFEFE7D9)
        val surfaceContainerHighestLight = Color(0xFFEAE2D4)

        val primaryDark = Color(0xFFB67F12)
        val onPrimaryDark = Color(0xFFE0CB7F)
        val primaryContainerDark = Color(0xFF574500)
        val onPrimaryContainerDark = Color(0xFFFFE086)
        val secondaryDark = Color(0xFFD4C5A1)
        val onSecondaryDark = Color(0xFF383016)
        val secondaryContainerDark = Color(0xFF524127)
        val onSecondaryContainerDark = Color(0xFFF1E1BB)
        val tertiaryDark = Color(0xFFFCE574)
        val onTertiaryDark = Color(0xFF492900)
        val tertiaryContainerDark = Color(0xFF693C00)
        val onTertiaryContainerDark = Color(0xFFFFDCBD)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF1C1715)
        val onBackgroundDark = Color(0xFFEAE2D4)
        val surfaceDark = backgroundDark
        val onSurfaceDark = Color(0xFFEAE2D4)
        val surfaceVariantDark = Color(0xFF4C4639)
        val onSurfaceVariantDark = Color(0xFFCEC6B4)
        val outlineDark = Color(0xFF989080)
        val outlineVariantDark = Color(0xFF4C4639)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFEAE2D4)
        val inverseOnSurfaceDark = Color(0xFF343027)
        val inversePrimaryDark = Color(0xFFE39012)
        val surfaceDimDark = Color(0xFF16130B)
        val surfaceBrightDark = Color(0xFF3D392F)
        val surfaceContainerLowestDark = Color(0xFF110E07)
        val surfaceContainerLowDark = Color(0xFF1E1B13)
        val surfaceContainerDark = Color(0xFF231F17)
        val surfaceContainerHighDark = Color(0xFF2D2A21)
        val surfaceContainerHighestDark = Color(0xFF38342B)

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
