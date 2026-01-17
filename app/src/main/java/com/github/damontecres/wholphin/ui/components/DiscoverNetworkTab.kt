package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

data class NetworkDiscoverGridItem(
    val item: DiscoverItem,
) : CardGridItem {
    override val gridId: String
        get() = item.id.toString()
    override val playable: Boolean
        get() = false
    override val sortName: String
        get() = item.title ?: ""
}

data class NetworkDiscoverState(
    val data: List<NetworkDiscoverGridItem> = emptyList(),
    val loading: DataLoadingState<Unit> = DataLoadingState.Pending,
    val hasMore: Boolean = true,
    val currentPage: Int = 1,
)

@HiltViewModel(assistedFactory = DiscoverNetworkViewModel.Factory::class)
class DiscoverNetworkViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
        @Assisted private val networkId: String,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(networkId: String): DiscoverNetworkViewModel
        }

        val state = MutableStateFlow(NetworkDiscoverState())

        init {
            loadPage(1)
        }

        fun loadPage(page: Int) {
            if (state.value.loading is DataLoadingState.Loading) {
                Timber.d("DiscoverNetwork: Skipping page $page - already loading")
                return // Prevent duplicate loads
            }

            Timber.d("DiscoverNetwork: Loading page $page")
            viewModelScope.launchIO {
                state.update { it.copy(loading = DataLoadingState.Loading) }
                try {
                    val response = seerrService.api.searchApi.discoverTvNetworkNetworkIdGet(
                        networkId = networkId,
                        page = page,
                    )
                    
                    val items = response.results
                        ?.map { DiscoverItem(it) }
                        ?.map { NetworkDiscoverGridItem(it) }
                        .orEmpty()
                    
                    val hasMore = (response.page ?: 0) < (response.totalPages ?: 0)
                    val oldDataSize = state.value.data.size
                    
                    state.update {
                        it.copy(
                            data = if (page == 1) items else it.data + items,
                            loading = DataLoadingState.Success(Unit),
                            hasMore = hasMore,
                            currentPage = page,
                        )
                    }
                    Timber.d("DiscoverNetwork: Page $page loaded - data size: $oldDataSize -> ${state.value.data.size}, hasMore: $hasMore")
                } catch (ex: Exception) {
                    Timber.e(ex, "Error loading network discovery page $page")
                    state.update { it.copy(loading = DataLoadingState.Error(ex)) }
                }
            }
        }

        fun loadNextPage() {
            if (state.value.hasMore) {
                Timber.d("DiscoverNetwork: loadNextPage triggered - current page: ${state.value.currentPage}")
                loadPage(state.value.currentPage + 1)
            } else {
                Timber.d("DiscoverNetwork: loadNextPage called but hasMore=false")
            }
        }

        fun updateBackdrop(item: DiscoverItem?) {
            viewModelScope.launchIO {
                if (item != null) {
                    backdropService.submit("discover_${item.id}", item.backDropUrl)
                } else {
                    backdropService.clearBackdrop()
                }
            }
        }
    }

@Composable
fun DiscoverNetworkTab(
    networkId: String,
    modifier: Modifier = Modifier,
    focusRequesterOnEmpty: FocusRequester? = null,
    viewModel: DiscoverNetworkViewModel =
        hiltViewModel<DiscoverNetworkViewModel, DiscoverNetworkViewModel.Factory>(
            creationCallback = { it.create(networkId) },
        ),
) {
    val state by viewModel.state.collectAsState()
    val hasRequestedFocus = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.updateBackdrop(null)
    }

    // Show error or grid with data
    if (state.loading is DataLoadingState.Error && state.data.isEmpty()) {
        // Only show error if we have no data
        ErrorMessage(
            message = stringResource(R.string.error_loading_network_discover),
            exception = (state.loading as DataLoadingState.Error).exception,
            modifier = modifier,
        )
    } else if (state.data.isNotEmpty()) {
        // Show grid as soon as we have data, regardless of loading state
        ShowNetworkGrid(
            state = state,
            viewModel = viewModel,
            focusRequesterOnEmpty = focusRequesterOnEmpty,
            hasRequestedFocus = hasRequestedFocus,
            modifier = modifier,
        )
    } else {
        // Show loading page only when we have no data yet
        LoadingPage(modifier = modifier)
    }
}

@Composable
private fun ShowNetworkGrid(
    state: NetworkDiscoverState,
    viewModel: DiscoverNetworkViewModel,
    focusRequesterOnEmpty: FocusRequester?,
    hasRequestedFocus: MutableState<Boolean>,
    modifier: Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    Timber.d("DiscoverNetwork: ShowNetworkGrid recomposed - data size: ${state.data.size}, hasRequestedFocus: ${hasRequestedFocus.value}")

    LaunchedEffect(state.data.isNotEmpty()) {
        Timber.d("DiscoverNetwork: LaunchedEffect triggered - isEmpty: ${state.data.isEmpty()}, hasRequestedFocus: ${hasRequestedFocus.value}")
        if (!hasRequestedFocus.value) {
            if (state.data.isNotEmpty()) {
                Timber.d("DiscoverNetwork: Requesting focus on grid")
                focusRequester.tryRequestFocus()
                hasRequestedFocus.value = true
            } else {
                Timber.d("DiscoverNetwork: Requesting focus on empty fallback")
                focusRequesterOnEmpty?.tryRequestFocus()
            }
        }
    }

    if (state.data.isEmpty()) {
        Column(modifier = modifier.fillMaxSize()) {
            Text(
                text = stringResource(R.string.no_results),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        CardGrid(
            pager = state.data,
            onClickItem = { _: Int, item: NetworkDiscoverGridItem ->
                viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(item.item))
            },
            onLongClickItem = { _: Int, _: NetworkDiscoverGridItem -> },
            onClickPlay = { _, _ -> },
            letterPosition = { _: Char -> 0 },
            gridFocusRequester = focusRequester,
            showJumpButtons = false,
            showLetterButtons = false,
            spacing = 16.dp,
            positionCallback = { _, position ->
                // Load next page when scrolling near the end
                val threshold = state.data.size - 18 // 3 rows of 6 items
                Timber.d("DiscoverNetwork: positionCallback - position: $position, threshold: $threshold, dataSize: ${state.data.size}")
                if (position >= threshold && 
                    state.hasMore && 
                    state.loading !is DataLoadingState.Loading) {
                    Timber.d("DiscoverNetwork: Position threshold reached, triggering loadNextPage")
                    viewModel.loadNextPage()
                }
            },
            cardContent = { item: NetworkDiscoverGridItem?, onClick, onLongClick, mod ->
                item?.let {
                    DiscoverItemCard(
                        item = it.item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        showOverlay = true,
                        modifier = mod,
                    )
                }
            },
            columns = 6,
            modifier = modifier,
        )
    }
}
