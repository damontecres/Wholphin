package com.github.damontecres.wholphin.ui.main.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun HomeSettingsFavoriteList(
    onClick: (BaseItemKind) -> Unit,
    modifier: Modifier = Modifier,
    firstFocus: FocusRequester = remember { FocusRequester() },
) {
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    Column(modifier = modifier) {
        Text(
            text = "Add favorites row...",
        )
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                modifier
                    .fillMaxHeight()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(favoriteOptionsList) { index, type ->
                HomeSettingsListItem(
                    selected = false,
                    headlineContent = {
                        Text(
                            text = stringResource(favoriteOptions[type]!!),
                        )
                    },
                    onClick = { onClick.invoke(type) },
                    modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
        }
    }
}

val favoriteOptions by lazy {
    mapOf(
        BaseItemKind.MOVIE to R.string.movies,
        BaseItemKind.SERIES to R.string.tv_shows,
        BaseItemKind.EPISODE to R.string.episodes,
        BaseItemKind.VIDEO to R.string.videos,
        BaseItemKind.PLAYLIST to R.string.playlists,
        BaseItemKind.PERSON to R.string.people,
    )
}
val favoriteOptionsList by lazy { favoriteOptions.keys.toList() }
