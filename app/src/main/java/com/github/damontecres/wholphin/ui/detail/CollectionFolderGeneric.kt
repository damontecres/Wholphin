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
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel

@Composable
fun CollectionFolderGeneric(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    recursive: Boolean,
    modifier: Modifier = Modifier,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    var showHeader by remember { mutableStateOf(true) }
    CollectionFolderGrid(
        preferences = preferences,
        onClickItem = { preferencesViewModel.navigationManager.navigateTo(it.destination()) },
        destination = destination,
        showTitle = showHeader,
        recursive = recursive,
        modifier =
            modifier
                .padding(start = 16.dp),
        positionCallback = { columns, position ->
            showHeader = position < columns
        },
    )
}
