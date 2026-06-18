package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.GridItemDetails
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.playback.scale
import com.github.damontecres.wholphin.ui.util.ScrollToTopBringIntoViewSpec
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ItemSortBy
import kotlin.time.Duration

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CollectionFolderGrid(
    preferences: UserPreferences,
    collectionType: CollectionType?,
    focusedItem: BaseItem?,
    items: List<BaseItem?>,
    sortAndDirection: SortAndDirection,
    onClickItem: (Int, BaseItem) -> Unit,
    onLongClickItem: (Int, BaseItem) -> Unit,
    letterPosition: suspend (Char) -> Int,
    viewOptions: ViewOptions,
    onClickPlay: (Int, BaseItem) -> Unit,
    initialPosition: Int,
    gridFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    positionCallback: ((columns: Int, position: Int) -> Unit)? = null,
) {
    Box(modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val defaultBringIntoViewSpec = LocalBringIntoViewSpec.current
            val density = LocalDensity.current
            AnimatedVisibility(viewOptions.showDetails) {
                HomePageHeader(
                    item = focusedItem,
                    showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .padding(HeaderUtils.padding),
                )
            }
            CardGrid(
                pager = items,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                onClickPlay = onClickPlay,
                letterPosition = letterPosition,
                gridFocusRequester = gridFocusRequester,
                showJumpButtons = false, // TODO add preference
                showLetterButtons = sortAndDirection.sort == ItemSortBy.SORT_NAME,
                modifier = Modifier.fillMaxSize(),
                initialPosition = initialPosition,
                positionCallback = positionCallback,
                cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = viewOptions.contentScale.scale,
                        imageAspectRatio = viewOptions.aspectRatio.ratio,
                        imageType = viewOptions.imageType,
                        showTitle = viewOptions.showTitles,
                        fillWidth = widthPx,
                        modifier = mod,
                    )
                },
                columns = viewOptions.columns,
                spacing = viewOptions.spacing.dp,
                bringIntoViewSpec =
                    remember(viewOptions) {
                        val spacingPx = with(density) { viewOptions.spacing.dp.toPx() }
                        if (viewOptions.showDetails) {
                            ScrollToTopBringIntoViewSpec(spacingPx)
                        } else {
                            defaultBringIntoViewSpec
                        }
                    },
            )
        }
    }
}

data class PositionItem(
    val position: Int,
    val item: BaseItem,
)

data class CollectionFolderGridParameters(
    val columns: Int = 6,
    val spacing: Dp = 16.dp,
    val cardContent: @Composable (GridItemDetails<BaseItem>) -> Unit = { (item, index, onClick, onLongClick, widthPx, mod) ->
        GridCard(
            item = item,
            onClick = onClick,
            onLongClick = onLongClick,
            imageContentScale = ContentScale.FillBounds,
            fillWidth = widthPx,
            modifier = mod,
        )
    },
) {
    companion object {
        val POSTER =
            CollectionFolderGridParameters(
                columns = 6,
                spacing = 16.dp,
                cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = ContentScale.FillBounds,
                        imageAspectRatio = AspectRatios.TALL,
                        fillWidth = widthPx,
                        modifier = mod,
                    )
                },
            )
        val WIDE =
            CollectionFolderGridParameters(
                columns = 4,
                spacing = 24.dp,
                cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = ContentScale.Crop,
                        imageAspectRatio = AspectRatios.WIDE,
                        fillWidth = widthPx,
                        modifier = mod,
                    )
                },
            )
        val SQUARE =
            CollectionFolderGridParameters(
                columns = 6,
                spacing = 16.dp,
                cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
                    GridCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        imageContentScale = ContentScale.FillBounds,
                        imageAspectRatio = AspectRatios.SQUARE,
                        fillWidth = widthPx,
                        modifier = mod,
                    )
                },
            )
    }
}

val CollectionType.baseItemKinds: List<BaseItemKind>
    get() =
        when (this) {
            CollectionType.MOVIES -> {
                listOf(BaseItemKind.MOVIE)
            }

            CollectionType.TVSHOWS -> {
                listOf(BaseItemKind.SERIES)
            }

            CollectionType.HOMEVIDEOS -> {
                listOf(BaseItemKind.VIDEO)
            }

            CollectionType.MUSIC -> {
                listOf(
                    BaseItemKind.AUDIO,
                    BaseItemKind.MUSIC_ARTIST,
                    BaseItemKind.MUSIC_ALBUM,
                )
            }

            CollectionType.BOXSETS -> {
                listOf(BaseItemKind.BOX_SET)
            }

            CollectionType.PLAYLISTS -> {
                listOf(BaseItemKind.PLAYLIST)
            }

            else -> {
                listOf()
            }
        }

@Composable
@NonRestartableComposable
fun GridTitle(
    title: String,
    modifier: Modifier = Modifier,
) = Text(
    text = title,
    style = MaterialTheme.typography.displaySmall,
    color = MaterialTheme.colorScheme.onBackground,
    textAlign = TextAlign.Center,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
    modifier = modifier.fillMaxWidth(),
)

data class GridClickActions(
    val onClickItem: (Int, BaseItem) -> Unit,
    val onLongClickItem: ((Int, BaseItem) -> Unit)? = null,
    val onClickPlayAll: ((shuffle: Boolean) -> Unit)? = null,
    val onClickPlayRemoteButton: ((Int, BaseItem) -> Unit)? = null,
)

@Composable
fun CollectionFolderHeader(
    showHeader: Boolean,
    showTitle: Boolean,
    playEnabled: Boolean,
    title: String,
    sortAndDirection: SortAndDirection,
    onSortChange: (SortAndDirection) -> Unit,
    sortOptions: List<ItemSortBy>,
    getPossibleFilterValues: suspend (ItemFilterBy<*>) -> List<FilterValueOption>,
    onClickShowViewOptions: () -> Unit,
    onClickPlayAll: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    currentFilter: GetItemsFilter = GetItemsFilter(),
    filterOptions: List<ItemFilterBy<*>> = listOf(),
    onFilterChange: (GetItemsFilter) -> Unit = {},
    onShowFilterDropdown: ((Boolean) -> Unit)? = null,
    filterButtonFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    AnimatedVisibility(
        showHeader,
        enter = expandVertically(),
        exit = shrinkVertically(),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (showTitle) {
                GridTitle(title)
            }
            val endPadding =
                16.dp + if (sortAndDirection.sort == ItemSortBy.SORT_NAME) 24.dp else 0.dp
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .padding(start = 16.dp, end = endPadding)
                        .focusRestorer()
                        .fillMaxWidth(),
            ) {
                if (sortOptions.isNotEmpty() || filterOptions.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.focusRestorer(),
                    ) {
                        if (sortOptions.isNotEmpty()) {
                            SortByButton(
                                sortOptions = sortOptions,
                                current = sortAndDirection,
                                onSortChange = onSortChange,
                                modifier = Modifier,
                            )
                        }
                        if (filterOptions.isNotEmpty()) {
                            FilterByButton(
                                filterOptions = filterOptions,
                                current = currentFilter,
                                onFilterChange = onFilterChange,
                                getPossibleValues = getPossibleFilterValues,
                                modifier = Modifier.focusRequester(filterButtonFocusRequester),
                                onShow = onShowFilterDropdown,
                            )
                        }
                        ExpandableFaButton(
                            title = R.string.view_options,
                            iconStringRes = R.string.fa_sliders,
                            onClick = onClickShowViewOptions,
                            modifier = Modifier,
                        )
                    }
                }
                if (playEnabled) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.focusRestorer(),
                    ) {
                        ExpandablePlayButton(
                            title = R.string.play,
                            resume = Duration.ZERO,
                            icon = Icons.Default.PlayArrow,
                            onClick = { onClickPlayAll.invoke(false) },
                        )
                        ExpandableFaButton(
                            title = R.string.shuffle,
                            iconStringRes = R.string.fa_shuffle,
                            onClick = { onClickPlayAll.invoke(true) },
                        )
                    }
                }
            }
        }
    }
}
