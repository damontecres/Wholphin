package com.github.damontecres.wholphin.ui

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.ImageUrlService
import org.jellyfin.sdk.model.api.ItemFields

// This file is for constants used for the UI

val FontAwesome = FontFamily(Font(resId = R.font.fa_solid_900))

val LocalImageUrlService =
    staticCompositionLocalOf<ImageUrlService> { throw IllegalStateException("LocalImageUrlService not set") }

/**
 * Colors not associated with the theme
 */
object AppColors {
    val TransparentBlack25 = Color(0x40000000)
    val TransparentBlack50 = Color(0x80000000)
    val TransparentBlack75 = Color(0xBF000000)

    val DarkGreen = Color(0xFF114000)
    val DarkRed = Color(0xFF400000)
    val DarkCyan = Color(0xFF21556E)
    val DarkPurple = Color(0xFF261370)

    val GoldenYellow = Color(0xFFDAB440)

    object Discover {
        val Blue = Color(37, 99, 235)
        val Purple = Color(147, 51, 234)
        val Green = Color(74, 222, 128)
        val Yellow = Color(234, 179, 8)
    }
}

const val DEFAULT_PAGE_SIZE = 100

/**
 * The default [ItemFields] to fetch for most queries
 */
val DefaultItemFields =
    listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.SEASON_USER_DATA,
        ItemFields.CHILD_COUNT,
        ItemFields.OVERVIEW,
        ItemFields.TRICKPLAY,
        ItemFields.SORT_NAME,
        ItemFields.CHAPTERS,
        ItemFields.MEDIA_SOURCES,
        ItemFields.MEDIA_SOURCE_COUNT,
        ItemFields.PARENT_ID,
    )

/**
 * [ItemFields] for higher level displays such as grids or rows
 */
val SlimItemFields =
    listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.SEASON_USER_DATA,
        ItemFields.CHILD_COUNT,
        ItemFields.OVERVIEW,
        ItemFields.SORT_NAME,
        ItemFields.MEDIA_SOURCE_COUNT,
        ItemFields.PARENT_ID,
    )

val PhotoItemFields =
    DefaultItemFields +
        listOf(
            ItemFields.WIDTH,
            ItemFields.HEIGHT,
        )

object Cards {
    val height2x3 = 172.dp
    val heightEpisode = height2x3 * .75f
    val playedPercentHeight = 6.dp
    val serverUserCircle = height2x3 * .75f
}

object AspectRatios {
    const val WIDE = 16f / 9f
    const val FOUR_THREE = 4f / 3f
    const val TALL = 2f / 3f
    const val SQUARE = 1f

    const val MIN = TALL
}

enum class AspectRatio(
    val ratio: Float,
) {
    TALL(AspectRatios.TALL),
    WIDE(AspectRatios.WIDE),
    FOUR_THREE(AspectRatios.FOUR_THREE),
    SQUARE(AspectRatios.SQUARE),
}

@Preview(
    device = "spec:parent=tv_1080p",
    backgroundColor = 0xFF383535,
    uiMode = UI_MODE_TYPE_TELEVISION,
)
annotation class PreviewTvSpec
