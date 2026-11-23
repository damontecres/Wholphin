package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.theme.LocalTheme

/**
 * The watch progress along the bottom of a card.
 *
 * The fill sits inside a black track that is inset from the card edges, so the bar stays
 * readable over artwork of any colour: a bare fill drawn straight onto the poster
 * disappears wherever the image happens to be the same colour.
 */
@Composable
fun PlayedPercentBar(
    percent: Double,
    modifier: Modifier = Modifier,
) {
    val fraction = (percent / 100.0).toFloat().coerceIn(0f, 1f)
    val shape = RoundedCornerShape(Cards.playedPercentHeight / 2)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .height(Cards.playedPercentHeight)
                .clip(shape)
                .border(1.dp, SolidColor(Color.Black), shape)
                .background(AppColors.TransparentBlack75),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(playedPercentColor()),
        )
    }
}

/**
 * The fill color, typically [MaterialTheme.colorScheme.tertiary]
 */
@Composable
private fun playedPercentColor(): Color =
    when (LocalTheme.current) {
        AppThemeColors.UNRECOGNIZED,
        AppThemeColors.PURPLE,
        AppThemeColors.BLUE,
        AppThemeColors.GREEN,
        AppThemeColors.ORANGE,
        AppThemeColors.RED,
        AppThemeColors.BOLD_BLUE,
        AppThemeColors.OLED_BLACK,
        -> MaterialTheme.colorScheme.tertiary

        AppThemeColors.BROWN -> MaterialTheme.colorScheme.primary
    }
