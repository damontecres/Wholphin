package com.github.damontecres.wholphin.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.data.filter.DefaultFilterOptions
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.CollectionFolderViewModel
import com.github.damontecres.wholphin.ui.components.ViewOptionsWide
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.toServerString
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import java.util.UUID

@Composable
fun CollectionFolderPhotoAlbum(
    preferences: UserPreferences,
    itemId: UUID,
    recursive: Boolean,
    modifier: Modifier = Modifier,
    filter: CollectionFolderFilter = CollectionFolderFilter(),
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    sortOptions: List<ItemSortBy> = VideoSortOptions,
    // Note: making the VM here and passing down is bad practice, but we need the grid state when clicking on items
    // TODO refactor this
    viewModel: CollectionFolderViewModel =
        hiltViewModel<CollectionFolderViewModel, CollectionFolderViewModel.Factory>(
            key = itemId.toServerString(),
        ) {
            it.create(
                itemId = itemId.toServerString(),
                initialSortAndDirection = null,
                recursive = recursive,
                collectionFilter = filter,
                useSeriesForPrimary = false,
                defaultViewOptions = ViewOptionsWide,
            )
        },
) {
    var showHeader by remember { mutableStateOf(true) }
    CollectionFolderGrid(
        preferences = preferences,
        onClickItem = { index, item ->
            val destination =
                if (item.type == BaseItemKind.PHOTO) {
                    Destination.Slideshow(
                        parentId = itemId,
                        index = index,
                        filter = CollectionFolderFilter(filter = viewModel.filter.value ?: GetItemsFilter()),
                        sortAndDirection = viewModel.sortAndDirection.value ?: SortAndDirection.DEFAULT,
                        recursive = true,
                        startSlideshow = false,
                    )
                } else {
                    item.destination(index)
                }
            viewModel.navigationManager.navigateTo(destination)
        },
        itemId = itemId.toServerString(),
        initialFilter = filter,
        showTitle = showHeader,
        recursive = recursive,
        sortOptions = sortOptions,
        modifier = modifier,
        positionCallback = { columns, position ->
            showHeader = position < columns
        },
        defaultViewOptions = ViewOptionsWide,
        playEnabled = true,
        filterOptions = filterOptions,
        viewModel = viewModel,
    )
}
