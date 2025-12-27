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
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.data.BoxSetSortOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import java.util.UUID

@Composable
fun CollectionFolderBoxSet(
    preferences: UserPreferences,
    itemId: UUID,
    item: BaseItem?,
    recursive: Boolean,
    modifier: Modifier = Modifier,
    filter: CollectionFolderFilter = CollectionFolderFilter(),
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
    playEnabled: Boolean = false,
) {
    var showHeader by remember { mutableStateOf(true) }
    CollectionFolderGrid(
        preferences = preferences,
        onClickItem = { _, item -> preferencesViewModel.navigationManager.navigateTo(item.destination()) },
        itemId = itemId,
        initialFilter = filter,
        showTitle = showHeader,
        recursive = recursive,
        sortOptions = BoxSetSortOptions,
        initialSortAndDirection = SortAndDirection(ItemSortBy.DEFAULT, SortOrder.ASCENDING),
        modifier =
            modifier
                .padding(start = 16.dp),
        positionCallback = { columns, position ->
            showHeader = position < columns
        },
        defaultViewOptions = ViewOptionsPoster,
        playEnabled = playEnabled,
    )
}
