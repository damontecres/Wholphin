package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val BrownThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF8A5022)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFFFDCC6)
        val onPrimaryContainerLight = Color(0xFF6D390C)
        val secondaryLight = Color(0xFF755845)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFFFDCC6)
        val onSecondaryContainerLight = Color(0xFF5B412F)
        val tertiaryLight = Color(0xFF5F6135)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFE5E6AE)
        val onTertiaryContainerLight = Color(0xFF47491F)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFFFF8F5)
        val onBackgroundLight = Color(0xFF221A15)
        val surfaceLight = Color(0xFFFFF8F5)
        val onSurfaceLight = Color(0xFF221A15)
        val surfaceVariantLight = Color(0xFFF3DED3)
        val onSurfaceVariantLight = Color(0xFF52443B)
        val outlineLight = Color(0xFF84746A)
        val outlineVariantLight = Color(0xFFD7C3B7)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF382F29)
        val inverseOnSurfaceLight = Color(0xFFFEEDE4)
        val inversePrimaryLight = Color(0xFFFFB784)
        val surfaceDimLight = Color(0xFFE7D7CE)
        val surfaceBrightLight = Color(0xFFFFF8F5)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFFFF1EA)
        val surfaceContainerLight = Color(0xFFFBEBE2)
        val surfaceContainerHighLight = Color(0xFFF6E5DC)
        val surfaceContainerHighestLight = Color(0xFFF0DFD6)

        val primaryDark = Color(0xFFFFB784)
        val onPrimaryDark = Color(0xFF4F2500)
        val primaryContainerDark = Color(0xFF6D390C)
        val onPrimaryContainerDark = Color(0xFFFFDCC6)
        val secondaryDark = Color(0xFFE4BFA8)
        val onSecondaryDark = Color(0xFF422B1B)
        val secondaryContainerDark = Color(0xFF5B412F)
        val onSecondaryContainerDark = Color(0xFFFFDCC6)
        val tertiaryDark = Color(0xFFC8CA94)
        val onTertiaryDark = Color(0xFF30330B)
        val tertiaryContainerDark = Color(0xFF47491F)
        val onTertiaryContainerDark = Color(0xFFE5E6AE)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF19120D)
        val onBackgroundDark = Color(0xFFF0DFD6)
        val surfaceDark = Color(0xFF19120D)
        val onSurfaceDark = Color(0xFFF0DFD6)
        val surfaceVariantDark = Color(0xFF52443B)
        val onSurfaceVariantDark = Color(0xFFD7C3B7)
        val outlineDark = Color(0xFF9F8D83)
        val outlineVariantDark = Color(0xFF52443B)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFF0DFD6)
        val inverseOnSurfaceDark = Color(0xFF382F29)
        val inversePrimaryDark = Color(0xFF6D3B07)
        val surfaceDimDark = Color(0xFF19120D)
        val surfaceBrightDark = Color(0xFF413731)
        val surfaceContainerLowestDark = Color(0xFF140D08)
        val surfaceContainerLowDark = Color(0xFF221A15)
        val surfaceContainerDark = Color(0xFF261E18)
        val surfaceContainerHighDark = Color(0xFF312822)
        val surfaceContainerHighestDark = Color(0xFF3C332D)

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
