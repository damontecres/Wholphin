package com.github.damontecres.wholphin.ui.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ExtrasItem
import com.github.damontecres.wholphin.data.pluralRes
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards

@Composable
fun ExtrasRow(
    extras: List<ExtrasItem>,
    onClickItem: (Int, ExtrasItem) -> Unit,
    onLongClickItem: (Int, ExtrasItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    ItemRow(
        title = stringResource(R.string.extras),
        items = extras,
        onClickItem = onClickItem,
        onLongClickItem = onLongClickItem,
        cardContent = { index, item, mod, onClick, onLongClick ->
            SeasonCard(
                title =
                    when (item) {
                        is ExtrasItem.Group -> item.type.pluralRes.let { resources.getQuantityString(it, item.items.size) }
                        is ExtrasItem.Single -> item.title ?: item.type.pluralRes.let { resources.getQuantityString(it, 1) }
                        null -> null
                    },
                name = null,
                subtitle =
                    if (item is ExtrasItem.Single && item.title != null) {
                        item.type.pluralRes.let { resources.getQuantityString(it, 1) }
                    } else if (item is ExtrasItem.Group) {
                        resources.getQuantityString(R.plurals.items, item.items.size, item.items.size)
                    } else {
                        null
                    },
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = mod,
                showImageOverlay = true,
                imageHeight = Cards.height2x3 * .75f,
                imageWidth = Dp.Unspecified,
                imageUrl = item?.imageUrl,
                isFavorite = false,
                isPlayed = false,
                unplayedItemCount = -1,
                playedPercentage = -1.0,
                aspectRatio = AspectRatios.FOUR_THREE, // TODO
            )
        },
        modifier = modifier,
    )
}
