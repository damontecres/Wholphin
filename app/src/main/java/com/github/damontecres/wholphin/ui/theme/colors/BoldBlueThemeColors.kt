package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val BoldBlueThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF405F90)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFD6E3FF)
        val onPrimaryContainerLight = Color(0xFF274777)
        val secondaryLight = Color(0xFF565F71)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFDAE2F9)
        val onSecondaryContainerLight = Color(0xFF3E4759)
        val tertiaryLight = Color(0xFF605690)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFE6DEFF)
        val onTertiaryContainerLight = Color(0xFF483F77)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFF9F9FF)
        val onBackgroundLight = Color(0xFF191C20)
        val surfaceLight = Color(0xFFFAF8FF)
        val onSurfaceLight = Color(0xFF1A1B21)
        val surfaceVariantLight = Color(0xFFE1E2EC)
        val onSurfaceVariantLight = Color(0xFF44474F)
        val outlineLight = Color(0xFF75777F)
        val outlineVariantLight = Color(0xFFC4C6D0)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF2F3036)
        val inverseOnSurfaceLight = Color(0xFFF1F0F7)
        val inversePrimaryLight = Color(0xFFAAC7FF)
        val surfaceDimLight = Color(0xFFDAD9E0)
        val surfaceBrightLight = Color(0xFFFAF8FF)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFF4F3FA)
        val surfaceContainerLight = Color(0xFFEEEDF4)
        val surfaceContainerHighLight = Color(0xFFE8E7EF)
        val surfaceContainerHighestLight = Color(0xFFE3E2E9)

        val primaryDark = Color(0xFF0C2A67)
        val onPrimaryDark = Color(0xFF558DD0)
        val primaryContainerDark = Color(0xFF192E4F)
        val onPrimaryContainerDark = Color(0xFFB4C9F6)
        val secondaryDark = Color(0xFFBEC7DC)
        val onSecondaryDark = Color(0xFF283141)
        val secondaryContainerDark = Color(0xFF3E4759)
        val onSecondaryContainerDark = Color(0xFFDAE2F9)
        val tertiaryDark = Color(0xFFBEC9FF)
        val onTertiaryDark = Color(0xFF28345F)
        val tertiaryContainerDark = Color(0xFF8CDDE8)
        val onTertiaryContainerDark = Color(0xFFE6DEFF)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF111318)
        val onBackgroundDark = Color(0xFFE2E2E9)
        val surfaceDark = Color(0xFF0A1017)
        val onSurfaceDark = Color(0xFFDAE3FA)
        val surfaceVariantDark = Color(0xFF44474F)
        val onSurfaceVariantDark = Color(0xFFC4C6D0)
        val outlineDark = Color(0xFF8E9099)
        val outlineVariantDark = Color(0xFF44474F)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFE3E2E9)
        val inverseOnSurfaceDark = Color(0xFF2F3036)
        val inversePrimaryDark = Color(0xFF152986)
        val surfaceDimDark = Color(0xFF121318)
        val surfaceBrightDark = Color(0xFF38393F)
        val surfaceContainerLowestDark = Color(0xFF0D0E13)
        val surfaceContainerLowDark = Color(0xFF1A1B21)
        val surfaceContainerDark = Color(0xFF1E1F25)
        val surfaceContainerHighDark = Color(0xFF292A2F)
        val surfaceContainerHighestDark = Color(0xFF33343A)

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
