package com.github.damontecres.wholphin.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.cards.PlaylistTile
import com.github.damontecres.wholphin.ui.components.CollectionFolderView
import com.github.damontecres.wholphin.ui.components.GridClickActions
import com.github.damontecres.wholphin.ui.components.ViewOptionsPlaylists
import com.github.damontecres.wholphin.ui.components.playlistProgress
import com.github.damontecres.wholphin.ui.data.PlaylistSortOptions
import com.github.damontecres.wholphin.ui.playback.scale
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.toServerString
import java.util.UUID

@Composable
fun CollectionFolderPlaylist(
    preferences: UserPreferences,
    itemId: UUID,
    recursive: Boolean,
    modifier: Modifier = Modifier,
    filter: CollectionFolderFilter = CollectionFolderFilter(),
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    var showHeader by rememberSaveable { mutableStateOf(true) }
    CollectionFolderView(
        preferences = preferences,
        itemId = itemId.toServerString(),
        actions =
            GridClickActions(
                onClickItem = { _, item -> preferencesViewModel.navigationManager.navigateTo(item.destination()) },
            ),
        initialFilter = filter,
        showTitle = showHeader,
        recursive = recursive,
        sortOptions = PlaylistSortOptions,
        modifier = modifier,
        positionCallback = { columns, position ->
            showHeader = position < columns
        },
        defaultViewOptions = ViewOptionsPlaylists,
        playEnabled = false,
        gridCardContent = { (item, _, onClick, onLongClick, widthPx, mod) ->
            PlaylistTile(
                item = item,
                onClick = onClick,
                onLongClick = onLongClick,
                progress = viewOptions.playlistProgress,
                imageAspectRatio = viewOptions.aspectRatio.ratio,
                imageContentScale = viewOptions.contentScale.scale,
                imageType = viewOptions.imageType,
                showTitle = viewOptions.showTitles,
                fillWidth = widthPx,
                modifier = mod,
            )
        },
    )
}
