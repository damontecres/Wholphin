package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.equalsNotNull
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.RequestHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = ItemGridViewModel.Factory::class)
class ItemGridViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val navigationManager: NavigationManager,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val mediaManagementService: MediaManagementService,
        val serverRepository: ServerRepository,
        val mediaReportService: MediaReportService,
        @Assisted private val destination: Destination.ItemGrid<*>,
    ) : ViewModel(),
        ContextMenuProvider {
        @AssistedFactory
        interface Factory {
            fun create(destination: Destination.ItemGrid<*>): ItemGridViewModel
        }

        private val _state = MutableStateFlow(ItemGridState())
        val state: StateFlow<ItemGridState> = _state

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

        override fun navigateTo(destination: Destination) {
            navigationManager.navigateTo(destination)
        }

        override fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        override fun deleteItem(
            index: Int,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    refreshAfterDelete(index, item)
                }
            }
        }

        private suspend fun refreshAfterDelete(
            position: Int,
            deletedItem: BaseItem,
        ) {
            try {
                val pager =
                    ((state.value.items as? DataLoadingState.Success)?.data as? ApiRequestPager<*>)
                position.let {
                    Timber.v("Item deleted: position=%s", it)
                    val item = pager?.get(it)
                    // Exact item deleted (eg a movie) or deleted item was within the series
                    if (item?.id == deletedItem.id ||
                        equalsNotNull(item?.data?.id, deletedItem.data.seriesId)
                    ) {
                        pager?.refreshPagesAfter(position)
                    }
                }
            } catch (ex: Exception) {
                Timber.e(ex, "Error refreshing after deleted item %s", deletedItem.id)
                showToast(context, "Error refreshing after item deleted")
            }
        }

        override fun setWatched(
            position: Int,
            itemId: UUID,
            played: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                favoriteWatchManager.setWatched(itemId, played)
                (state.value.items as? DataLoadingState.Success)?.let {
                    (it.data as? ApiRequestPager<*>)?.refreshItem(position, itemId)
                }
            }
        }

        override fun setFavorite(
            position: Int,
            itemId: UUID,
            favorite: Boolean,
        ) {
            viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
                favoriteWatchManager.setFavorite(itemId, favorite)
                (state.value.items as? DataLoadingState.Success)?.let {
                    (it.data as? ApiRequestPager<*>)?.refreshItem(position, itemId)
                }
            }
        }

        override fun isAdministrator(): Boolean = serverRepository.currentUserDto?.policy?.isAdministrator == true

        override fun sendReportFor(itemId: UUID) = mediaReportService.sendReportFor(itemId)
    }

data class ItemGridState(
    val items: DataLoadingState<List<BaseItem?>> = DataLoadingState.Pending,
)

/**
 * Display a grid of a list of arbitrary items [com.github.damontecres.wholphin.data.ExtrasItem]
 */
@Composable
fun ItemGrid(
    preferences: UserPreferences,
    destination: Destination.ItemGrid<*>,
    modifier: Modifier = Modifier,
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
    viewModel: ItemGridViewModel =
        hiltViewModel<ItemGridViewModel, ItemGridViewModel.Factory>(
            creationCallback = { it.create(destination) },
        ),
) {
    val contextMenu = rememberContextMenu(preferences, viewModel)

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
            var position by rememberInt(destination.initialPosition)
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.tryRequestFocus()
            }

            Column(modifier = modifier) {
                GridTitle(destination.title.getString())

                CardGrid(
                    pager = st.data,
                    onClickItem = { index: Int, item: BaseItem ->
                        position = index
                        viewModel.navigateTo(item.destination(index))
                    },
                    onLongClickItem = { index: Int, item: BaseItem ->
                        position = index
                        contextMenu.showContextMenu(index, item)
                    },
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
    contextMenu.Compose()
}
