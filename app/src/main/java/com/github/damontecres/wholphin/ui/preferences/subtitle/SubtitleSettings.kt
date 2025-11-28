package com.github.damontecres.wholphin.ui.preferences.subtitle

import android.content.res.Configuration
import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppChoicePreference
import com.github.damontecres.wholphin.preferences.AppClickablePreference
import com.github.damontecres.wholphin.preferences.AppSliderPreference
import com.github.damontecres.wholphin.preferences.AppSwitchPreference
import com.github.damontecres.wholphin.preferences.BackgroundStyle
import com.github.damontecres.wholphin.preferences.EdgeStyle
import com.github.damontecres.wholphin.preferences.SubtitlePreferences
import com.github.damontecres.wholphin.preferences.updateSubtitlePreferences
import com.github.damontecres.wholphin.ui.indexOfFirstOrNull
import com.github.damontecres.wholphin.ui.preferences.PreferenceGroup
import com.github.damontecres.wholphin.util.mpv.MPVLib
import com.github.damontecres.wholphin.util.mpv.setPropertyColor
import timber.log.Timber

object SubtitleSettings {
    val FontSize =
        AppSliderPreference(
            title = R.string.font_size,
            defaultValue = 24,
            min = 8,
            max = 70,
            interval = 2,
            getter = {
                it.interfacePreferences.subtitlesPreferences.fontSize
                    .toLong()
            },
            setter = { prefs, value ->
                prefs.updateSubtitlePreferences { fontSize = value.toInt() }
            },
            summarizer = { value -> value?.toString() },
        )

    private val colorList =
        listOf(
            Color.White,
            Color.Black,
            Color.LightGray,
            Color.DarkGray,
            Color.Red,
            Color.Yellow,
            Color.Green,
            Color.Cyan,
            Color.Blue,
            Color.Magenta,
        )

    val FontColor =
        AppChoicePreference<Color>(
            title = R.string.font_color,
            defaultValue = Color.White,
            getter = { Color(it.interfacePreferences.subtitlesPreferences.fontColor) },
            setter = { prefs, value ->
                prefs.updateSubtitlePreferences { fontColor = value.toArgb().and(0x00FFFFFF) }
            },
            displayValues = R.array.font_colors,
            indexToValue = { colorList.getOrNull(it) ?: Color.White },
            valueToIndex = { value ->
                val color = value.toArgb().and(0x00FFFFFF)
                colorList.indexOfFirstOrNull { color == it.toArgb().and(0x00FFFFFF) } ?: 0
            },
        )

    val FontBold =
        AppSwitchPreference(
            title = R.string.bold_font,
            defaultValue = false,
            getter = { it.interfacePreferences.subtitlesPreferences.fontBold },
            setter = { prefs, value ->
                prefs.updateSubtitlePreferences { fontBold = value }
            },
        )
    val FontItalic =
        AppSwitchPreference(
            title = R.string.italic_font,
            defaultValue = false,
            getter = { it.interfacePreferences.subtitlesPreferences.fontItalic },
            setter = { prefs, value ->
                prefs.updateSubtitlePreferences { fontItalic = value }
            },
        )

    val FontOpacity =
        AppSliderPreference(
            title = R.string.font_opacity,
            defaultValue = 100,
            min = 10,
            max = 100,
            interval = 10,
            getter = {
                it.interfacePreferences.subtitlesPreferences.fontOpacity
                    .toLong()
            },
            setter = { prefs, value ->
                prefs.updateSubtitlePreferences { fontOpacity = value.toInt() }
            },
            summarizer = { value -> value?.let { "$it%" } },
        )

    val EdgeStylePref =
        AppChoicePreference<EdgeStyle>(
            title =
                R.string.edge_style,
            defaultValue = EdgeStyle.EDGE_SOLID,
            getter = { it.interfacePreferences.subtitlesPreferences.edgeStyle },
            setter = { prefs, value ->
                prefs.updateSubtitlePreferences { edgeStyle = value }
            },
            displayValues = R.array.subtitle_edge,
            indexToValue = { EdgeStyle.forNumber(it) },
            valueToIndex = { it.number },
        )

    val EdgeColor =
        AppChoicePreference<Color>(
            title = R.string.edge_color,
            defaultValue = Color.Black,
            getter = { Color(it.interfacePreferences.subtitlesPreferences.edgeColor) },
            setter = { prefs, value ->
                prefs.updateSubtitlePreferences { edgeColor = value.toArgb().and(0x00FFFFFF) }
            },
            displayValues = R.array.font_colors,
            indexToValue = { colorList.getOrNull(it) ?: Color.White },
            valueToIndex = { value ->
                val color = value.toArgb().and(0x00FFFFFF)
                colorList.indexOfFirstOrNull { color == it.toArgb().and(0x00FFFFFF) } ?: 0
            },
        )

    val BackgroundColor =
        AppChoicePreference<Color>(
            title = R.string.background_color,
            defaultValue = Color.Transparent,
            getter = { Color(it.interfacePreferences.subtitlesPreferences.backgroundColor) },
            setter = { prefs, value ->
                prefs.updateSubtitlePreferences { backgroundColor = value.toArgb().and(0x00FFFFFF) }
            },
            displayValues = R.array.font_colors,
            indexToValue = { colorList.getOrNull(it) ?: Color.White },
            valueToIndex = { value ->
                val color = value.toArgb().and(0x00FFFFFF)
                colorList.indexOfFirstOrNull { color == it.toArgb().and(0x00FFFFFF) } ?: 0
            },
        )

    val BackgroundOpacity =
        AppSliderPreference(
            title = R.string.background_opacity,
            defaultValue = 50,
            min = 10,
            max = 100,
            interval = 10,
            getter = {
                it.interfacePreferences.subtitlesPreferences.backgroundOpacity
                    .toLong()
            },
            setter = { prefs, value ->
                prefs.updateSubtitlePreferences { backgroundOpacity = value.toInt() }
            },
            summarizer = { value -> value?.let { "$it%" } },
        )

    val BackgroundStylePref =
        AppChoicePreference<BackgroundStyle>(
            title =
                R.string.background_style,
            defaultValue = BackgroundStyle.BG_NONE,
            getter = { it.interfacePreferences.subtitlesPreferences.backgroundStyle },
            setter = { prefs, value ->
                prefs.updateSubtitlePreferences { backgroundStyle = value }
            },
            displayValues = R.array.background_style,
            indexToValue = { BackgroundStyle.forNumber(it) },
            valueToIndex = { it.number },
        )

    val Margin =
        AppSliderPreference(
            title = R.string.subtitle_margin,
            defaultValue = 8,
            min = 0,
            max = 100,
            interval = 1,
            getter = {
                it.interfacePreferences.subtitlesPreferences.margin
                    .toLong()
            },
            setter = { prefs, value ->
                prefs.updateSubtitlePreferences { margin = value.toInt() }
            },
            summarizer = { value -> value?.let { "$it%" } },
        )

    val Reset =
        AppClickablePreference(
            title = R.string.reset,
            getter = { },
            setter = { prefs, _ -> prefs },
        )

    val preferences =
        listOf(
            PreferenceGroup(
                title = R.string.font,
                preferences =
                    listOf(
                        FontSize,
                        FontColor,
                        FontBold,
                        FontItalic,
                        FontOpacity,
                    ),
            ),
            PreferenceGroup(
                title = R.string.edge_style,
                preferences =
                    listOf(
                        EdgeStylePref,
                        EdgeColor,
                    ),
            ),
            PreferenceGroup(
                title = R.string.background,
                preferences =
                    listOf(
                        BackgroundStylePref,
                        BackgroundColor,
                        BackgroundOpacity,
                    ),
            ),
            PreferenceGroup(
                title = R.string.more,
                preferences =
                    listOf(
                        Margin,
                        Reset,
                    ),
            ),
        )

    private fun combine(
        color: Int,
        opacity: Int,
    ) = ((opacity / 100.0 * 255).toInt().shl(24)).or(color.and(0x00FFFFFF))

    @OptIn(UnstableApi::class)
    fun SubtitlePreferences.toSubtitleStyle(): CaptionStyleCompat {
        val bg = combine(backgroundColor, backgroundOpacity)
        return CaptionStyleCompat(
            combine(fontColor, fontOpacity),
            if (backgroundStyle == BackgroundStyle.BG_WRAP)bg else 0,
            if (backgroundStyle == BackgroundStyle.BG_BOXED) bg else 0,
            when (edgeStyle) {
                EdgeStyle.EDGE_NONE, EdgeStyle.UNRECOGNIZED -> CaptionStyleCompat.EDGE_TYPE_NONE
                EdgeStyle.EDGE_SOLID -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                EdgeStyle.EDGE_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            },
            combine(edgeColor, fontOpacity),
            when {
                fontBold && fontItalic -> Typeface.defaultFromStyle(Typeface.BOLD_ITALIC)
                fontBold -> Typeface.defaultFromStyle(Typeface.BOLD)
                fontItalic -> Typeface.defaultFromStyle(Typeface.ITALIC)
                else -> Typeface.DEFAULT
            },
        )
    }

    fun SubtitlePreferences.applyToMpv(
        configuration: Configuration,
        density: Density,
    ) {
        val fo = (fontOpacity / 100.0 * 255).toInt().shl(24)
        val fc = Color(combine(fontColor, fontOpacity))
        val bg = Color(combine(backgroundColor, backgroundOpacity))
        val edge = Color(combine(edgeColor, fontOpacity))

        // TODO weird, but seems to get the size to be very close to matching sizes between renderers
        val fontSizePx = with(density) { fontSize.sp.toPx() * .8 }.toInt()
        MPVLib.setPropertyInt("sub-font-size", fontSizePx)
        MPVLib.setPropertyColor("sub-color", fc)
        MPVLib.setPropertyColor("sub-outline-color", edge)

        val heightInPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val margin = (heightInPx * (margin.toFloat() / 100f) * .8).toInt()
        MPVLib.setPropertyInt("sub-margin-y", margin)
        Timber.d("MPV subtitles: fontSizePx=%s, margin=$margin", fontSizePx, margin)

        when (edgeStyle) {
            EdgeStyle.EDGE_NONE,
            EdgeStyle.UNRECOGNIZED,
            -> {
                MPVLib.setPropertyInt("sub-shadow-offset", 0)
                MPVLib.setPropertyDouble("sub-outline-size", 0.0)
            }

            EdgeStyle.EDGE_SOLID -> {
                MPVLib.setPropertyInt("sub-shadow-offset", 0)
                MPVLib.setPropertyDouble("sub-outline-size", 1.15)
            }

            EdgeStyle.EDGE_SHADOW -> {
                MPVLib.setPropertyInt("sub-shadow-offset", 4)
                MPVLib.setPropertyDouble("sub-outline-size", 0.0)
            }
        }

//        if (fontBold) {
//            MPVLib.setPropertyString("sub-font", "Roboto Bold")
//        } else {
//            MPVLib.setPropertyString("sub-font", "Roboto Regular")
//        }
        MPVLib.setPropertyBoolean("sub-bold", fontBold)
        MPVLib.setPropertyBoolean("sub-italic", fontItalic)

        MPVLib.setPropertyColor("sub-back-color", bg)
        val borderStyle =
            when (backgroundStyle) {
                BackgroundStyle.UNRECOGNIZED,
                BackgroundStyle.BG_NONE,
                -> "outline-and-shadow"

                BackgroundStyle.BG_WRAP -> "opaque-box"
                BackgroundStyle.BG_BOXED -> "background-box"
            }
        MPVLib.setPropertyString("sub-border-style", borderStyle)
    }
}
