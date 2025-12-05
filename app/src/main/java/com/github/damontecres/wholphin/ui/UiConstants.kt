package com.github.damontecres.wholphin.ui

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.layout.PaddingValues
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
sealed class AppColors private constructor() {
    companion object {
        val TransparentBlack25 = Color(0x40000000)
        val TransparentBlack50 = Color(0x80000000)
        val TransparentBlack75 = Color(0xBF000000)

        val DarkGreen = Color(0xFF114000)
        val DarkRed = Color(0xFF400000)
        val DarkCyan = Color(0xFF21556E)
        val DarkPurple = Color(0xFF261370)

        val GoldenYellow = Color(0xFFDAB440)
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
    )

val DefaultButtonPadding =
    PaddingValues(
        start = 12.dp / 2,
        top = 10.dp / 2,
        end = 16.dp / 2,
        bottom = 10.dp / 2,
    )

object Cards {
    val height2x3 = 172.dp
    val playedPercentHeight = 6.dp
}

object AspectRatios {
    const val WIDE = 16f / 9f
    const val FOUR_THREE = 4f / 3f
    const val TALL = 2f / 3f
    const val SQUARE = 1f
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
