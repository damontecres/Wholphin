package com.github.damontecres.dolphin.ui

import android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.github.damontecres.dolphin.R
import org.jellyfin.sdk.model.api.ItemFields

val FontAwesome = FontFamily(Font(resId = R.font.fa_solid_900))

sealed class AppColors private constructor() {
    companion object {
        val TransparentBlack25 = Color(0x40000000)
        val TransparentBlack50 = Color(0x80000000)
        val TransparentBlack75 = Color(0xBF000000)
    }
}

const val DEFAULT_PAGE_SIZE = 100

val DefaultItemFields =
    listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.SEASON_USER_DATA,
        ItemFields.CHILD_COUNT,
        ItemFields.OVERVIEW,
        ItemFields.TRICKPLAY,
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
    val defaultHeight2x3 = 180.dp
    val playedPercentHeight = 6.dp
}

@Preview(
    device = "spec:parent=tv_1080p",
    backgroundColor = 0xFF383535,
    uiMode = UI_MODE_TYPE_TELEVISION,
)
annotation class PreviewTvSpec
