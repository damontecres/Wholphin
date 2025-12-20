package com.github.damontecres.wholphin.ui.seerr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
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
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.setValueOnMain
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SeerrDiscoverViewModel
    @Inject
    constructor(
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val api: ApiClient,
    ) : ViewModel() {
        val recentlyAdded = MutableLiveData<List<DiscoverItem>>(listOf())
        val discoverMovies = MutableLiveData<List<DiscoverItem>>(listOf())
        val discoverTv = MutableLiveData<List<DiscoverItem>>(listOf())

        init {
            viewModelScope.launchIO {

                val tv =
                    seerrService.api.mediaApi
                        .mediaGet(
                            take = 20,
                            filter = MediaApi.FilterMediaGet.ALLAVAILABLE,
                            sort = MediaApi.SortMediaGet.MEDIA_ADDED,
                        ).results
                        .orEmpty()
                Timber.v(tv.firstOrNull()?.jellyfinMediaId)
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
    }

@Composable
fun SeerrDiscoverPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SeerrDiscoverViewModel = hiltViewModel(),
) {
    val movies by viewModel.discoverMovies.observeAsState(listOf())
    val tv by viewModel.discoverTv.observeAsState(listOf())

    val scrollState = rememberScrollState()
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
                viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(item))
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
        )
        ItemRow(
            title = stringResource(R.string.tv_shows),
            items = tv,
            onClickItem = { index, item ->
                viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(item))
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
        )
    }
}
