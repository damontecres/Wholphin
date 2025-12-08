package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.ui.Cards

@Composable
@NonRestartableComposable
fun DiscoverItemCard(
    item: DiscoverItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = SeasonCard(
    title = item?.title,
    subtitle = item?.releaseDate,
    name = item?.title,
    imageUrl = item?.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }, // TODO
    isFavorite = false,
    isPlayed = false,
    unplayedItemCount = -1,
    playedPercentage = -1.0,
    onClick = onClick,
    onLongClick = onLongClick,
    imageHeight = Cards.height2x3,
    modifier = modifier,
    interactionSource = interactionSource,
)
