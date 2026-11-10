@file:OptIn(ExperimentalCoilApi::class)

package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.CrossFadeFactory
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.components.QuickDetailsText
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.components.rememberQuickDetailsContentMap
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.model.api.ImageType
import kotlin.time.Duration.Companion.milliseconds

/**
 * Extra height added on top of the card height for the artwork and details area of a showcase row
 */
private const val SHOWCASE_EXTRA_HEIGHT_DP = 220

/**
 * Displays a home row in "showcase" mode: the focused item's backdrop art is shown large behind
 * the row, with optional logo, rating, and overview. The row of cards ([rowContent]) is anchored
 * to the bottom.
 *
 * If not [enabled], only [rowContent] is shown.
 *
 * @param focusedIndex the index of the focused item in this row, or null if focus is in another row
 */
@Composable
fun ShowcaseRow(
    enabled: Boolean,
    items: List<BaseItem?>,
    focusedIndex: Int?,
    viewOptions: HomeRowViewOptions,
    modifier: Modifier = Modifier,
    rowContent: @Composable (Modifier) -> Unit,
) {
    if (!enabled) {
        rowContent.invoke(modifier)
        return
    }
    // Keep showing the last focused item when focus moves to another row
    var lastIndex by remember { mutableIntStateOf(0) }
    val index =
        if (focusedIndex != null && focusedIndex in items.indices) {
            focusedIndex
        } else {
            lastIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
        }
    SideEffect { lastIndex = index }
    val item = items.getOrNull(index)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height((viewOptions.heightDp + SHOWCASE_EXTRA_HEIGHT_DP).dp),
    ) {
        ShowcaseBackdrop(
            item = item,
            modifier = Modifier.matchParentSize(),
        )
        ShowcaseDetails(
            item = item,
            viewOptions = viewOptions,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 24.dp)
                    .fillMaxWidth(.5f),
        )
        rowContent.invoke(Modifier.align(Alignment.BottomStart))
    }
}

@Composable
private fun ShowcaseBackdrop(
    item: BaseItem?,
    modifier: Modifier = Modifier,
) {
    val imageUrlService = LocalImageUrlService.current
    val imageUrl = imageUrlService.rememberImageUrl(item, ImageType.BACKDROP)
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val background = MaterialTheme.colorScheme.background
    AsyncImage(
        model =
            ImageRequest
                .Builder(LocalContext.current)
                .data(imageUrl)
                .transitionFactory(CrossFadeFactory(400.milliseconds))
                .build(),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        alignment = Alignment.TopCenter,
        modifier =
            modifier.drawWithContent {
                drawContent()
                // Fade toward the start edge so the details text stays readable
                val start = if (isRtl) size.width else 0f
                val end = if (isRtl) 0f else size.width
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(background.copy(alpha = .85f), Color.Transparent),
                            startX = start,
                            endX = start + (end - start) * .6f,
                        ),
                )
                // Fade toward the bottom so the cards blend into the page
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, background),
                            startY = size.height * .4f,
                            endY = size.height,
                        ),
                )
            },
    )
}

@Composable
private fun ShowcaseDetails(
    item: BaseItem?,
    viewOptions: HomeRowViewOptions,
    modifier: Modifier = Modifier,
) {
    val textStyle = MaterialTheme.typography.titleSmall
    val inlineContentMap = rememberQuickDetailsContentMap(textStyle)
    val details = item?.ui?.quickDetails
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        TitleOrLogo(
            title = item?.data?.seriesName ?: item?.title,
            logoImageUrl = rememberLogoUrl(item),
            showLogo = viewOptions.showcaseShowLogo,
        )
        if (details != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QuickDetailsText(details.basic, Modifier, textStyle, inlineContentMap)
                if (viewOptions.showcaseShowRating) {
                    QuickDetailsText(details.communityRating, Modifier, textStyle, inlineContentMap)
                    QuickDetailsText(details.criticRating, Modifier, textStyle, inlineContentMap)
                }
            }
        }
        val overview = item?.data?.overview
        if (viewOptions.showcaseShowOverview && overview.isNotNullOrBlank()) {
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
