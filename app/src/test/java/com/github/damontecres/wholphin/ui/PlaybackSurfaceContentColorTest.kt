package com.github.damontecres.wholphin.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.theme.colors.PurpleThemeColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Exercises TV Material3's actual content-color fallback through the two app surfaces. */
@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class PlaybackSurfaceContentColorTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun originalSurfacesProvideThemedTextColor() {
        checkColors(transparent = false, explicitContentColor = false)
    }

    @Test
    fun transparentSurfacesWithoutContentColorReproduceBlackText() {
        checkColors(transparent = true, explicitContentColor = false)
    }

    @Test
    fun transparentSurfacesWithContentColorRestoreThemedTextColor() {
        checkColors(transparent = true, explicitContentColor = true)
    }

    private fun checkColors(
        transparent: Boolean,
        explicitContentColor: Boolean,
    ) {
        var inheritedColor = Color.Unspecified
        var textColor = Color.Unspecified
        var themedColor = Color.Unspecified
        composeRule.setContent {
            MaterialTheme(colorScheme = PurpleThemeColors.darkScheme) {
                val outerColors =
                    when {
                        explicitContentColor -> {
                            SurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        transparent -> {
                            SurfaceDefaults.colors(containerColor = Color.Transparent)
                        }

                        else -> {
                            SurfaceDefaults.colors()
                        }
                    }
                Surface(colors = outerColors) {
                    // MainActivity switches to the user's theme before MainContent's surface.
                    WholphinTheme(appThemeColors = AppThemeColors.BLUE) {
                        themedColor = MaterialTheme.colorScheme.onSurface
                        val innerColors =
                            when {
                                explicitContentColor -> {
                                    SurfaceDefaults.colors(
                                        containerColor = Color.Transparent,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                    )
                                }

                                transparent -> {
                                    SurfaceDefaults.colors(containerColor = Color.Transparent)
                                }

                                else -> {
                                    SurfaceDefaults.colors()
                                }
                            }
                        Surface(colors = innerColors) {
                            inheritedColor = LocalContentColor.current
                            // Same TV Text and unspecified color/style color as the playback title.
                            Text(
                                text = "Playback title",
                                style = MaterialTheme.typography.titleLarge,
                                onTextLayout = { textColor = it.layoutInput.style.color },
                            )
                        }
                    }
                }
            }
        }
        composeRule.runOnIdle {
            val expected = if (transparent && !explicitContentColor) Color.Black else themedColor
            assertNotEquals(Color.Unspecified, themedColor)
            assertEquals(expected, inheritedColor)
            assertEquals(expected, textColor)
            assertNotEquals(Color.Black, themedColor)
        }
    }
}
