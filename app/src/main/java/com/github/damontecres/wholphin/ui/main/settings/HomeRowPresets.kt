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
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.PrefContentScale
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.components.ViewOptionImageType
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.CollectionType

data class HomeRowPresets(
    val continueWatching: HomeRowViewOptions,
    val movieLibrary: HomeRowViewOptions,
    val tvLibrary: HomeRowViewOptions,
    val videoLibrary: HomeRowViewOptions,
    val photoLibrary: HomeRowViewOptions,
    val playlist: HomeRowViewOptions,
    val genreSize: Int,
) {
    fun getByCollectionType(collectionType: CollectionType): HomeRowViewOptions =
        when (collectionType) {
            CollectionType.MOVIES -> movieLibrary

            CollectionType.TVSHOWS -> tvLibrary

            CollectionType.MUSICVIDEOS -> videoLibrary

            CollectionType.TRAILERS -> videoLibrary

            CollectionType.HOMEVIDEOS -> videoLibrary

            CollectionType.BOXSETS -> movieLibrary

            CollectionType.PHOTOS -> photoLibrary

            CollectionType.UNKNOWN,
            CollectionType.MUSIC,
            CollectionType.BOOKS,
            CollectionType.LIVETV,
            CollectionType.PLAYLISTS,
            CollectionType.FOLDERS,
            -> HomeRowViewOptions()
        }

    companion object {
        val WholphinDefault by lazy {
            HomeRowPresets(
                continueWatching = HomeRowViewOptions(),
                movieLibrary = HomeRowViewOptions(),
                tvLibrary = HomeRowViewOptions(),
                videoLibrary =
                    HomeRowViewOptions(
                        aspectRatio = AspectRatio.WIDE,
                    ),
                photoLibrary =
                    HomeRowViewOptions(
                        aspectRatio = AspectRatio.WIDE,
                        contentScale = PrefContentScale.CROP,
                    ),
                playlist =
                    HomeRowViewOptions(
                        aspectRatio = AspectRatio.SQUARE,
                        contentScale = PrefContentScale.FIT,
                    ),
                genreSize = Cards.HEIGHT_2X3_DP,
            )
        }

        val WholphinCompact by lazy {
            val height = 148
            val epHeight = 100
            HomeRowPresets(
                continueWatching =
                    HomeRowViewOptions(
                        heightDp = height,
                    ),
                movieLibrary =
                    HomeRowViewOptions(
                        heightDp = height,
                    ),
                tvLibrary =
                    HomeRowViewOptions(
                        heightDp = height,
                    ),
                videoLibrary =
                    HomeRowViewOptions(
                        heightDp = epHeight,
                        aspectRatio = AspectRatio.WIDE,
                    ),
                photoLibrary =
                    HomeRowViewOptions(
                        heightDp = epHeight,
                        aspectRatio = AspectRatio.WIDE,
                        contentScale = PrefContentScale.CROP,
                    ),
                playlist =
                    HomeRowViewOptions(
                        heightDp = epHeight,
                        aspectRatio = AspectRatio.SQUARE,
                        contentScale = PrefContentScale.FIT,
                    ),
                genreSize = epHeight,
            )
        }

        val Thumbnails by lazy {
            val height = 148
            val epHeight = 100
            HomeRowPresets(
                continueWatching =
                    HomeRowViewOptions(
                        heightDp = epHeight,
                        imageType = ViewOptionImageType.THUMB,
                        aspectRatio = AspectRatio.WIDE,
                        episodeImageType = ViewOptionImageType.THUMB,
                        episodeAspectRatio = AspectRatio.WIDE,
                    ),
                movieLibrary =
                    HomeRowViewOptions(
                        heightDp = height,
                    ),
                tvLibrary =
                    HomeRowViewOptions(
                        heightDp = height,
                    ),
                videoLibrary =
                    HomeRowViewOptions(
                        heightDp = epHeight,
                        aspectRatio = AspectRatio.WIDE,
                    ),
                photoLibrary =
                    HomeRowViewOptions(
                        heightDp = epHeight,
                        aspectRatio = AspectRatio.WIDE,
                        contentScale = PrefContentScale.CROP,
                    ),
                playlist =
                    HomeRowViewOptions(
                        heightDp = epHeight,
                        aspectRatio = AspectRatio.SQUARE,
                        contentScale = PrefContentScale.FIT,
                    ),
                genreSize = epHeight,
            )
        }
    }
}

@Composable
fun HomeRowPresetsContent(
    onApply: (HomeRowPresets) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets =
        remember {
            listOf(
                "Wholphin Default",
                "Wholphin Compact",
                "Thumbnails",
            )
        }
    val focusRequesters = remember { List(presets.size) { FocusRequester() } }
    LaunchedEffect(Unit) { focusRequesters[0].tryRequestFocus() }
    Column(modifier = modifier) {
        TitleText(stringResource(R.string.display_presets))
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                modifier
                    .fillMaxHeight()
                    .focusRestorer(focusRequesters[0]),
        ) {
            itemsIndexed(presets) { index, title ->
                HomeSettingsListItem(
                    selected = false,
                    headlineText = title,
                    onClick = {
                        when (index) {
                            0 -> onApply.invoke(HomeRowPresets.WholphinDefault)
                            1 -> onApply.invoke(HomeRowPresets.WholphinCompact)
                            2 -> onApply.invoke(HomeRowPresets.Thumbnails)
                        }
                    },
                    modifier = Modifier.focusRequester(focusRequesters[index]),
                )
            }
        }
    }
}
