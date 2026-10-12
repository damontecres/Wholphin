package com.github.damontecres.wholphin.ui.search

import android.text.format.DateUtils
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.cards.BannerCardWithTitle
import com.github.damontecres.wholphin.ui.cards.PersonCard
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.cards.personRowCardWidth
import com.github.damontecres.wholphin.ui.cards.rememberImageUrl
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.time.OffsetDateTime

@Composable
fun SearchPageCard(
    item: BaseItem?,
    type: BaseItemKind,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    when (type) {
        BaseItemKind.EPISODE -> {
            BannerCardWithTitle(
                title = item?.title,
                subtitle = item?.subtitle,
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.padding(horizontal = 8.dp),
                cardHeight = Cards.heightEpisode,
            )
        }

        BaseItemKind.MUSIC_ALBUM,
        BaseItemKind.MUSIC_ARTIST,
        BaseItemKind.AUDIO,
        -> {
            SeasonCard(
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                imageHeight = Cards.heightEpisode,
                aspectRatio = AspectRatios.SQUARE,
                showImageOverlay = true,
                modifier = modifier,
            )
        }

        BaseItemKind.PERSON -> {
            val imageUrlService = LocalImageUrlService.current
            val imageUrl =
                remember(item) {
                    imageUrlService.getItemImageUrl(
                        item,
                        ImageType.PRIMARY,
                    )
                }
            PersonCard(
                name = item?.name ?: "",
                role = null,
                imageUrl = imageUrl,
                favorite = item?.favorite ?: false,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier.width(personRowCardWidth),
            )
        }

        BaseItemKind.TV_CHANNEL -> {
            SeasonCard(
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                imageHeight = Cards.HEIGHT_LIVE_TV.dp,
                showImageOverlay = true,
                aspectRatio = AspectRatios.WIDE,
                modifier = modifier,
            )
        }

        BaseItemKind.PROGRAM,
        BaseItemKind.TV_PROGRAM,
        BaseItemKind.LIVE_TV_PROGRAM,
        -> {
            val subtitle =
                remember(item) {
                    val startDate = item?.data?.startDate
                    val endDate = item?.data?.endDate
                    if (startDate != null && endDate != null) {
                        DateUtils.formatDateRange(
                            context,
                            startDate
                                .toInstant(OffsetDateTime.now().offset)
                                .epochSecond * 1000,
                            endDate
                                .toInstant(OffsetDateTime.now().offset)
                                .epochSecond * 1000,
                            DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY,
                        )
                    } else {
                        null
                    }
                }

            SeasonCard(
                title = item?.title,
                subtitle = subtitle,
                name = item?.name,
                imageUrl = rememberImageUrl(item, Cards.HEIGHT_LIVE_TV.dp, Dp.Unspecified),
                isFavorite = item?.data?.userData?.isFavorite ?: false,
                isPlayed = item?.data?.userData?.played ?: false,
                unplayedItemCount = item?.data?.userData?.unplayedItemCount ?: 0,
                playedPercentage = item?.data?.userData?.playedPercentage ?: 0.0,
                numberOfVersions = item?.data?.mediaSourceCount ?: 0,
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier,
                imageHeight = Cards.HEIGHT_LIVE_TV.dp,
                imageWidth = Dp.Unspecified,
                showImageOverlay = true,
                aspectRatio = AspectRatios.WIDE,
            )
        }

        else -> {
            SeasonCard(
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                imageHeight = Cards.height2x3,
                showImageOverlay = true,
                modifier = modifier,
            )
        }
    }
}
