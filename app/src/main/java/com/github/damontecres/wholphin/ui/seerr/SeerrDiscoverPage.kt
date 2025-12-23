package com.github.damontecres.wholphin.ui.seerr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.MediaApi
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SeerrDiscoverViewModel
    @Inject
    constructor(
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val api: ApiClient,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        val recentlyAdded = MutableLiveData<List<DiscoverItem>>(listOf())
        val discoverMovies = MutableLiveData<List<DiscoverItem>>(listOf())
        val discoverTv = MutableLiveData<List<DiscoverItem>>(listOf())

        init {
            viewModelScope.launchIO {
                backdropService.clearBackdrop()
                val tv =
                    seerrService.api.mediaApi
                        .mediaGet(
                            take = 20,
                            filter = MediaApi.FilterMediaGet.ALLAVAILABLE,
                            sort = MediaApi.SortMediaGet.MEDIA_ADDED,
                        ).results
                        .orEmpty()
//                Timber.v(tv.firstOrNull()?.jellyfinMediaId)
            }
            viewModelScope.launchIO {
                val movies = seerrService.discoverMovies()
                discoverMovies.setValueOnMain(movies)
            }
            viewModelScope.launchIO {
                val tv = seerrService.discoverTv()
                discoverTv.setValueOnMain(tv)
            }
        }

        fun updateBackdrop(item: DiscoverItem?) {
            viewModelScope.launchIO {
                if (item != null) {
                    backdropService.submit("discover_${item.id}", item.backDropUrl)
                }
            }
        }
    }

@Composable
fun SeerrDiscoverPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SeerrDiscoverViewModel = hiltViewModel(),
) {
    val movies by viewModel.discoverMovies.observeAsState(listOf())
    val tv by viewModel.discoverTv.observeAsState(listOf())

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(movies) { if (movies.isNotEmpty()) focusRequester.tryRequestFocus() }
    val scrollState = rememberScrollState()

    var position by rememberPosition(0, 0)
    LaunchedEffect(position) {
        position.let {
            val item = if (it.row == 0) movies.getOrNull(it.column) else tv.getOrNull(it.column)
            Timber.v("Backdrop for $item")
            viewModel.updateBackdrop(item)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            modifier
                .verticalScroll(scrollState)
                .padding(16.dp),
    ) {
        ItemRow(
            title = stringResource(R.string.movies),
            items = movies,
            onClickItem = { index, item ->
                if (item.jellyfinItemId != null) {
                    viewModel.navigationManager.navigateTo(
                        Destination.MediaItem(
                            itemId = item.jellyfinItemId,
                            type = BaseItemKind.MOVIE,
                        ),
                    )
                } else {
                    viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(item))
                }
            },
            onLongClickItem = { index, item -> },
            cardContent = { index: Int, item: DiscoverItem?, mod: Modifier, onClick: () -> Unit, onLongClick: () -> Unit ->
                DiscoverItemCard(
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    showOverlay = false,
                    modifier = mod,
                )
            },
            cardOnFocus = { isFocused, index ->
                if (isFocused) {
                    position = RowColumn(0, index)
                }
            },
            modifier = Modifier.focusRequester(focusRequester),
        )
        ItemRow(
            title = stringResource(R.string.tv_shows),
            items = tv,
            onClickItem = { index, item ->
                if (item.jellyfinItemId != null) {
                    viewModel.navigationManager.navigateTo(
                        Destination.MediaItem(
                            itemId = item.jellyfinItemId,
                            type = BaseItemKind.SERIES,
                        ),
                    )
                } else {
                    viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(item))
                }
            },
            onLongClickItem = { index, item -> },
            cardContent = { index: Int, item: DiscoverItem?, mod: Modifier, onClick: () -> Unit, onLongClick: () -> Unit ->
                DiscoverItemCard(
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    showOverlay = false,
                    modifier = mod,
                )
            },
            cardOnFocus = { isFocused, index ->
                if (isFocused) {
                    position = RowColumn(1, index)
                }
            },
        )
    }
}
