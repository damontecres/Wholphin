package com.github.damontecres.wholphin.ui.main.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.CollectionType

@Composable
fun HomeLibraryRowTypeList(
    library: Library,
    onClick: (LibraryRowType) -> Unit,
    modifier: Modifier,
    firstFocus: FocusRequester = remember { FocusRequester() },
) {
    val items = remember(library) { getSupportedRowTypes(library) }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    Column(modifier = modifier) {
        TitleText(stringResource(R.string.add_row_for, library.name))
        HomeSettingsLazyColumn(
            modifier =
                modifier
                    .fillMaxHeight()
                    .focusRestorer(firstFocus),
        ) {
            itemsIndexed(items) { index, rowType ->
                ListItem(
                    selected = false,
                    headlineContent = {
                        Text(
                            text = stringResource(rowType.stringId),
                        )
                    },
                    onClick = { onClick.invoke(rowType) },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                )
            }
        }
    }
}

fun getSupportedRowTypes(library: Library): List<LibraryRowType> =
    when {
        library.isRecordingFolder -> {
            listOf(
                LibraryRowType.RECENTLY_RECORDED,
                LibraryRowType.GENRES,
            )
        }

        library.collectionType == CollectionType.LIVETV -> {
            listOf(
                LibraryRowType.TV_CHANNELS,
                LibraryRowType.TV_PROGRAMS,
            )
        }

        library.collectionType == CollectionType.TVSHOWS -> {
            listOf(
                LibraryRowType.RECENTLY_ADDED,
                LibraryRowType.RECENTLY_RELEASED,
                LibraryRowType.SUGGESTIONS,
                LibraryRowType.GENRES,
                LibraryRowType.STUDIOS,
                LibraryRowType.BY_PARENT,
            )
        }

        library.collectionType == CollectionType.MOVIES -> {
            listOf(
                LibraryRowType.RECENTLY_ADDED,
                LibraryRowType.RECENTLY_RELEASED,
                LibraryRowType.SUGGESTIONS,
                LibraryRowType.GENRES,
                LibraryRowType.BY_PARENT,
            )
        }

        library.collectionType == CollectionType.BOXSETS -> {
            listOf(
                LibraryRowType.RECENTLY_ADDED,
                LibraryRowType.RECENTLY_RELEASED,
                LibraryRowType.GENRES,
                LibraryRowType.COLLECTION,
                LibraryRowType.BY_PARENT,
            )
        }

        library.collectionType == CollectionType.PLAYLISTS -> {
            listOf(
                LibraryRowType.RECENTLY_ADDED,
                LibraryRowType.RECENTLY_RELEASED,
                LibraryRowType.GENRES,
                LibraryRowType.PLAYLIST,
                LibraryRowType.BY_PARENT,
            )
        }

        else -> {
            listOf(
                LibraryRowType.RECENTLY_ADDED,
                LibraryRowType.RECENTLY_RELEASED,
                LibraryRowType.GENRES,
                LibraryRowType.BY_PARENT,
            )
        }
    }

enum class LibraryRowType(
    @param:StringRes val stringId: Int,
) {
    RECENTLY_ADDED(R.string.recently_added),
    RECENTLY_RELEASED(R.string.recently_released),
    SUGGESTIONS(R.string.suggestions),
    GENRES(R.string.genres),
    STUDIOS(R.string.studios),
    TV_CHANNELS(R.string.channels),
    TV_PROGRAMS(R.string.watch_live),
    RECENTLY_RECORDED(R.string.recently_recorded),
    COLLECTION(R.string.collection),
    PLAYLIST(R.string.playlist),
    BY_PARENT(R.string.library),
}
