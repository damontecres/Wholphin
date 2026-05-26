package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.RequestHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber

@HiltViewModel(assistedFactory = ItemGridViewModel.Factory::class)
class ItemGridViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val navigationManager: NavigationManager,
        @Assisted private val destination: Destination.ItemGrid<*>,
    ) : ViewModel() {
        private val _state = MutableStateFlow(ItemGridState())
        val state: StateFlow<ItemGridState> = _state

        @AssistedFactory
        interface Factory {
            fun create(destination: Destination.ItemGrid<*>): ItemGridViewModel
        }

        init {
            viewModelScope.launchIO {
                try {
                    val request = destination.request as Any
                    val pager =
                        ApiRequestPager(
                            api,
                            request,
                            destination.requestHandler as RequestHandler<Any>,
                            viewModelScope,
                            useSeriesForPrimary = true,
                        ).init()
                    if (pager.isNotEmpty()) {
                        pager.getBlocking(0)
                    }
                    _state.update {
                        it.copy(items = DataLoadingState.Success(pager))
                    }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching items")
                    _state.update { it.copy(items = DataLoadingState.Error(ex)) }
                }
            }
        }

        fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }
    }

data class ItemGridState(
    val items: DataLoadingState<List<BaseItem?>> = DataLoadingState.Pending,
)

/**
 * Display a grid of a list of arbitrary items [com.github.damontecres.wholphin.data.ExtrasItem]
 */
@Composable
fun ItemGrid(
    destination: Destination.ItemGrid<*>,
    modifier: Modifier = Modifier,
    viewModel: ItemGridViewModel =
        hiltViewModel<ItemGridViewModel, ItemGridViewModel.Factory>(
            creationCallback = { it.create(destination) },
        ),
) {
    val state by viewModel.state.collectAsState()
    when (val st = state.items) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<List<BaseItem?>> -> {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.tryRequestFocus()
            }
            Column(modifier = modifier) {
                GridTitle(destination.title.getString())

                CardGrid(
                    pager = st.data,
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
                    initialPosition = destination.initialPosition,
                    spacing = destination.viewOptions.spacing.dp,
                    cardContent = @Composable { (item, index, onClick, onLongClick, widthPx, mod) ->
                        GridCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            fillWidth = widthPx,
                            modifier = mod,
                            imageAspectRatio = destination.viewOptions.aspectRatio.ratio,
                        )
                    },
                    columns = destination.viewOptions.columns,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
