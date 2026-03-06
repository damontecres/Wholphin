package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest

@HiltViewModel(assistedFactory = ItemGridViewModel.Factory::class)
class ItemGridViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val navigationManager: NavigationManager,
        @Assisted private val destination: Destination.ItemGrid,
    ) : ViewModel() {
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val items = MutableLiveData<List<BaseItem?>>(listOf())

        @AssistedFactory
        interface Factory {
            fun create(destination: Destination.ItemGrid): ItemGridViewModel
        }

        init {
            viewModelScope.launchIO(LoadingExceptionHandler(loading, "Error fetching items")) {
                val request =
                    GetItemsRequest(
                        ids = destination.itemIds,
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = listOf(SortOrder.ASCENDING),
                    )
                val pager = ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
                if (pager.isNotEmpty()) {
                    pager.getBlocking(0)
                }
                withContext(Dispatchers.Main) {
                    this@ItemGridViewModel.items.value = pager
                    this@ItemGridViewModel.loading.value = LoadingState.Success
                }
            }
        }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }
    }

@Composable
fun ItemGrid(
    destination: Destination.ItemGrid,
    modifier: Modifier = Modifier,
    viewModel: ItemGridViewModel =
        hiltViewModel<ItemGridViewModel, ItemGridViewModel.Factory>(
            creationCallback = { it.create(destination) },
        ),
) {
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val items by viewModel.items.observeAsState(listOf())
    when (val state = loading) {
        is LoadingState.Error -> {
            ErrorMessage(state, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.tryRequestFocus()
            }
            Column(modifier = modifier) {
                Text(
                    text = destination.title ?: destination.titleRes?.let { stringResource(it) } ?: "",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                CardGrid(
                    pager = items,
                    onClickItem = { index: Int, item: BaseItem ->
                        // TODO handle more types
                        viewModel.navigateTo(Destination.Playback(item.id, 0))
                    },
                    onLongClickItem = { index: Int, item: BaseItem -> },
                    onClickPlay = { _, item -> viewModel.navigateTo(Destination.Playback(item)) },
                    letterPosition = { c: Char -> 0 },
                    gridFocusRequester = focusRequester,
                    showJumpButtons = false,
                    showLetterButtons = false,
                    spacing = 24.dp,
                    cardContent = @Composable { item, onClick, onLongClick, mod ->
                        GridCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = mod,
                            imageAspectRatio = AspectRatios.WIDE, // TODO
                        )
                    },
                    columns = 3,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
