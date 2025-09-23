package com.github.damontecres.dolphin.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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

const val DEFAULT_PAGE_SIZE = 50

val DefaultItemFields =
    listOf(
        ItemFields.PRIMARY_IMAGE_ASPECT_RATIO,
        ItemFields.SEASON_USER_DATA,
        ItemFields.CHILD_COUNT,
    )
