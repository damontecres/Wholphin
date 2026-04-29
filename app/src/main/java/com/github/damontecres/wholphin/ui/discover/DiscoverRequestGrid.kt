package com.github.damontecres.wholphin.ui.discover

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrApi
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GridTitle
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.DiscoverMovieRequestHandler
import com.github.damontecres.wholphin.util.DiscoverRequestPager
import com.github.damontecres.wholphin.util.DiscoverRequestType
import com.github.damontecres.wholphin.util.DiscoverTvRequestHandler
import com.github.damontecres.wholphin.util.TrendingRequestHandler
import com.github.damontecres.wholphin.util.UpcomingMovieRequestHandler
import com.github.damontecres.wholphin.util.UpcomingTvRequestHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

@HiltViewModel(assistedFactory = DiscoverRequestViewModel.Factory::class)
class DiscoverRequestViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val api: SeerrApi,
        @Assisted val type: DiscoverRequestType,
        @Assisted startIndex: Int,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                type: DiscoverRequestType,
                startIndex: Int,
            ): DiscoverRequestViewModel
        }

        private val _state = MutableStateFlow(DiscoverRequestState())
        val state: StateFlow<DiscoverRequestState> = _state

        init {
            viewModelScope.launchDefault {
                try {
                    val pager =
                        when (type) {
                            DiscoverRequestType.DISCOVER_TV -> {
                                DiscoverRequestPager(
                                    api,
                                    DiscoverTvRequestHandler,
                                    seerrService::createDiscoverItem,
                                    viewModelScope,
                                )
                            }

                            DiscoverRequestType.DISCOVER_MOVIES -> {
                                DiscoverRequestPager(
                                    api,
                                    DiscoverMovieRequestHandler,
                                    seerrService::createDiscoverItem,
                                    viewModelScope,
                                )
                            }

                            DiscoverRequestType.TRENDING -> {
                                DiscoverRequestPager(
                                    api,
                                    TrendingRequestHandler,
                                    seerrService::createDiscoverItem,
                                    viewModelScope,
                                )
                            }

                            DiscoverRequestType.UPCOMING_TV -> {
                                DiscoverRequestPager(
                                    api,
                                    UpcomingTvRequestHandler,
                                    seerrService::createDiscoverItem,
                                    viewModelScope,
                                )
                            }

                            DiscoverRequestType.UPCOMING_MOVIES -> {
                                DiscoverRequestPager(
                                    api,
                                    UpcomingMovieRequestHandler,
                                    seerrService::createDiscoverItem,
                                    viewModelScope,
                                )
                            }

                            DiscoverRequestType.UNKNOWN -> {
                                throw IllegalArgumentException("Cannot display grid for DiscoverRequestType.UNKNOWN")
                            }
                        }.init(startIndex)
                    _state.update {
                        it.copy(
                            loading = DataLoadingState.Success(pager),
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error initializing %s", type)
                    _state.update {
                        it.copy(
                            loading = DataLoadingState.Error(ex),
                        )
                    }
                }
            }
        }
    }

data class DiscoverRequestState(
    val loading: DataLoadingState<List<DiscoverItem?>> = DataLoadingState.Pending,
)

@Composable
fun DiscoverRequestGrid(
    destination: Destination.DiscoverMoreResult,
    modifier: Modifier = Modifier,
    viewModel: DiscoverRequestViewModel =
        hiltViewModel<DiscoverRequestViewModel, DiscoverRequestViewModel.Factory>(
            creationCallback = { it.create(destination.type, destination.startIndex) },
        ),
) {
    val state by viewModel.state.collectAsState()
    when (val s = state.loading) {
        DataLoadingState.Pending,
        DataLoadingState.Loading,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Error -> {
            ErrorMessage(s, modifier)
        }

        is DataLoadingState.Success<List<DiscoverItem?>> -> {
            val gridFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
            Column(
                modifier = modifier,
            ) {
                GridTitle(stringResource(destination.type.stringRes))

                CardGrid(
                    initialPosition = destination.startIndex,
                    pager = s.data,
                    onClickItem = { index, item ->
                        viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(item))
                    },
                    onLongClickItem = { index, item -> },
                    onClickPlay = { _, _ -> },
                    letterPosition = { 0 },
                    gridFocusRequester = gridFocusRequester,
                    showJumpButtons = false,
                    showLetterButtons = false,
                    cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
                        DiscoverItemCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = mod,
                            width = Dp.Unspecified,
                        )
                    },
                )
            }
        }
    }
}
