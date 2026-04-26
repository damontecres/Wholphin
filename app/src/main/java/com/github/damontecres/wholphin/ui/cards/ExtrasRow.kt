package com.github.damontecres.wholphin.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards

@Composable
fun ExtrasRow(
    extras: List<ExtrasItem>,
    onClickItem: (Int, ExtrasItem) -> Unit,
    onLongClickItem: (Int, ExtrasItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    ItemRow(
        title = stringResource(R.string.extras),
        items = extras,
        onClickItem = onClickItem,
        onLongClickItem = onLongClickItem,
        cardContent = { index, item, mod, onClick, onLongClick ->
            SeasonCard(
                title = item?.title,
                name = null,
                subtitle = item?.subtitle,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = mod,
                showImageOverlay = true,
                imageHeight = Cards.heightEpisode,
                imageWidth = Dp.Unspecified,
                imageUrl = item?.imageUrl,
                isFavorite = false,
                isPlayed = item?.isPlayed == true,
                unplayedItemCount = -1,
                playedPercentage = item?.playedPercentage ?: 0.0,
                numberOfVersions = -1,
                aspectRatio = AspectRatios.FOUR_THREE, // TODO
            )
        },
        modifier = modifier,
    )
}
