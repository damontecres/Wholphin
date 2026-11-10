@file:OptIn(ExperimentalCoilApi::class)

package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.transitionFactory
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.ui.CrossFadeFactory
import com.github.damontecres.wholphin.ui.LocalImageUrlService
import com.github.damontecres.wholphin.ui.cards.ItemRowTitle
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetailsText
import com.github.damontecres.wholphin.ui.components.TimeRemaining
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.components.rememberLogoUrl
import com.github.damontecres.wholphin.ui.components.rememberQuickDetailsContentMap
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The details shown over the full screen artwork while a showcase row is focused. The artwork itself
 * is drawn behind the nav drawer by [ShowcaseBackdrop], and the rest of the page is hidden until
 * focus moves to another row.
 *
 * @param rowTitle the title of the showcase row, shown faintly so it's clear which row this is
 * @param index the position of [item] in the row
 * @param count the number of items in the row
 */
@Composable
fun ShowcaseHero(
    item: BaseItem?,
    rowTitle: String,
    viewOptions: HomeRowViewOptions,
    index: Int,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Text(
            text = rowTitle,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 32.dp, top = 24.dp)
                    .alpha(.7f),
        )
        ShowcaseDetails(
            item = item,
            viewOptions = viewOptions,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 32.dp, bottom = 32.dp)
                    .fillMaxWidth(.5f),
        )
        Text(
            text = "${index + 1} / $count",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = 32.dp)
                    .alpha(.7f),
        )
    }
}

/**
 * A home row in showcase mode. While focused, [ShowcaseHero] covers the page, and left/right moves
 * through the row's items (with [infiniteScroll], right on the last item goes back to the first).
 * When not focused, the row shows a single wide banner of its current item's artwork.
 *
 * Select opens the item, long press shows its menu, and the play button plays it.
 */
@Composable
fun ShowcaseRowBanner(
    title: String,
    items: List<BaseItem?>,
    initialIndex: Int,
    height: Dp,
    infiniteScroll: Boolean,
    onFocusIndex: (Int) -> Unit,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    onClickPlay: (Int, BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lastIndex = (items.size - 1).coerceAtLeast(0)
    var index by rememberSaveable { mutableIntStateOf(initialIndex.coerceIn(0, lastIndex)) }
    val currentIndex = index.coerceIn(0, lastIndex)
    val item = items.getOrNull(currentIndex)

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        ItemRowTitle(title)
        Surface(
            onClick = { item?.let { onClickItem.invoke(currentIndex, it) } },
            onLongClick = { item?.let { onLongClickItem.invoke(currentIndex, it) } },
            modifier =
                Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth()
                    .height(height)
                    .onFocusChanged {
                        if (it.isFocused) onFocusIndex.invoke(currentIndex)
                    }.onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft) {
                            if (currentIndex > 0) {
                                index = currentIndex - 1
                                onFocusIndex.invoke(index)
                                true
                            } else {
                                // Let focus move out of the row, such as to the nav drawer
                                false
                            }
                        } else if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight) {
                            if (currentIndex < lastIndex) {
                                index = currentIndex + 1
                                onFocusIndex.invoke(index)
                            } else if (infiniteScroll && lastIndex > 0) {
                                // Go back to the first item
                                index = 0
                                onFocusIndex.invoke(index)
                            }
                            true
                        } else if (isPlayKeyUp(event)) {
                            item?.let { if (it.playable) onClickPlay.invoke(currentIndex, it) }
                            true
                        } else {
                            false
                        }
                    },
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ShowcaseBackdrop(
                    item = item,
                    rotate = false,
                    modifier = Modifier.matchParentSize(),
                )
                Text(
                    text = item?.data?.seriesName ?: item?.title ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                )
            }
        }
    }
}

/**
 * How long each backdrop is shown when rotating artwork
 */
private val ROTATE_ARTWORK_INTERVAL = 8.seconds

/**
 * Provides the [BackdropService] so the home page can set the full screen showcase artwork
 */
@HiltViewModel
class ShowcaseViewModel
    @Inject
    constructor(
        val backdropService: BackdropService,
    ) : ViewModel()

/**
 * Shows the item's backdrop. If [rotate] is on and the item has more than one backdrop set in
 * Jellyfin, cycles through them in the order they are set. Dimmed while the nav drawer is open.
 */
@Composable
fun ShowcaseBackdrop(
    item: BaseItem?,
    rotate: Boolean,
    modifier: Modifier = Modifier,
    drawerIsOpen: Boolean = false,
) {
    val imageUrlService = LocalImageUrlService.current
    val imageUrls = remember(item) { imageUrlService.backdropUrls(item) }
    var imageIndex by remember(item) { mutableIntStateOf(0) }
    if (rotate && imageUrls.size > 1) {
        LaunchedEffect(item, imageUrls.size) {
            while (true) {
                delay(ROTATE_ARTWORK_INTERVAL)
                imageIndex = (imageIndex + 1) % imageUrls.size
            }
        }
    }
    val imageUrl = imageUrls.getOrNull(imageIndex)
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
                // Fade toward the start edge and the bottom so text on top stays readable
                val start = if (isRtl) size.width else 0f
                val end = if (isRtl) 0f else size.width
                drawRect(
                    brush =
                        Brush.horizontalGradient(
                            colors = listOf(background.copy(alpha = .75f), Color.Transparent),
                            startX = start,
                            endX = start + (end - start) * .55f,
                        ),
                )
                drawRect(
                    brush =
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, background.copy(alpha = .85f)),
                            startY = size.height * .55f,
                            endY = size.height,
                        ),
                )
                if (drawerIsOpen) {
                    drawRect(Color.Black.copy(alpha = .75f))
                }
            },
    )
}

/**
 * All of the backdrop images set for the item in Jellyfin, in order. Episodes and seasons use their
 * series' backdrops.
 */
private fun ImageUrlService.backdropUrls(item: BaseItem?): List<String> {
    if (item == null) return emptyList()
    val data = item.data
    val useParent = item.type == BaseItemKind.EPISODE || item.type == BaseItemKind.SEASON
    val itemId = if (useParent) data.parentBackdropItemId else item.id
    val tags = if (useParent) data.parentBackdropImageTags.orEmpty() else data.backdropImageTags.orEmpty()
    return if (itemId == null || tags.isEmpty()) {
        listOfNotNull(getItemImageUrl(item, ImageType.BACKDROP))
    } else {
        tags.mapIndexedNotNull { index, tag ->
            getItemImageUrl(itemId = itemId, imageType = ImageType.BACKDROP, tag = tag, imageIndex = index)
        }
    }
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
        val showDetails = viewOptions.showcaseShowDetails
        val showRating = viewOptions.showcaseShowRating
        if (details != null && (showDetails || showRating)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Episode number & date or year, runtime, etc
                if (showDetails) {
                    QuickDetailsText(details.basic, Modifier, textStyle, inlineContentMap)
                }
                if (showRating) {
                    QuickDetailsText(details.communityRating, Modifier, textStyle, inlineContentMap)
                    QuickDetailsText(details.criticRating, Modifier, textStyle, inlineContentMap)
                }
                // When it would end if started now, using the time remaining if partly watched
                if (showDetails) {
                    item?.timeRemainingOrRuntime?.let { TimeRemaining(it, textStyle = textStyle) }
                }
            }
        }
        val overview = item?.data?.overview
        if (viewOptions.showcaseShowOverview && overview.isNotNullOrBlank()) {
            // The shared overview component, so app-wide overview settings apply here too
            OverviewText(
                overview = overview,
                maxLines = 3,
                onClick = {},
                enabled = false,
            )
        }
    }
}
