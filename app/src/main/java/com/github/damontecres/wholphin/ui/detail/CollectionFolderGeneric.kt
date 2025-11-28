package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.data.filter.DefaultFilterOptions
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.CollectionFolderGridParameters
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import org.jellyfin.sdk.model.api.ItemSortBy
import java.util.UUID

@Composable
fun CollectionFolderGeneric(
    preferences: UserPreferences,
    itemId: UUID,
    usePosters: Boolean,
    recursive: Boolean,
    playEnabled: Boolean,
    modifier: Modifier = Modifier,
    filter: GetItemsFilter = GetItemsFilter(),
    filterOptions: List<ItemFilterBy<*>> = DefaultFilterOptions,
    sortOptions: List<ItemSortBy> = VideoSortOptions,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    var showHeader by remember { mutableStateOf(true) }
    val params =
        remember(usePosters) {
            if (usePosters) {
                CollectionFolderGridParameters.POSTER
            } else {
                CollectionFolderGridParameters.WIDE
            }
        }
    CollectionFolderGrid(
        preferences = preferences,
        onClickItem = { _, item -> preferencesViewModel.navigationManager.navigateTo(item.destination()) },
        itemId = itemId,
        initialFilter = filter,
        showTitle = showHeader,
        recursive = recursive,
        sortOptions = sortOptions,
        modifier =
            modifier
                .padding(start = 16.dp),
        positionCallback = { columns, position ->
            showHeader = position < columns
        },
        params = params,
        playEnabled = playEnabled,
        filterOptions = filterOptions,
    )
}
