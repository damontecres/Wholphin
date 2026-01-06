package com.github.damontecres.wholphin.ui.discover

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.HomePageHeader
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import javax.inject.Inject

@HiltViewModel
class SeerrDiscoverViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val api: ApiClient,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        val state = MutableStateFlow<DiscoverState>(DiscoverState())

        init {
            viewModelScope.launchIO {
                backdropService.clearBackdrop()
            }
            fetchAndUpdateState(seerrService::discoverMovies) {
                this.copy(movies = DiscoverRowData(context.getString(R.string.movies), it))
            }
            fetchAndUpdateState(seerrService::discoverTv) {
                this.copy(tv = DiscoverRowData(context.getString(R.string.tv_shows), it))
            }
        }

        private fun fetchAndUpdateState(
            getData: suspend () -> List<DiscoverItem>,
            copyFunc: DiscoverState.(DataLoadingState<List<DiscoverItem>>) -> DiscoverState,
        ) {
            viewModelScope.launchIO {
                state.update {
                    copyFunc.invoke(it, DataLoadingState.Loading)
                }
                try {
                    val results = getData.invoke()
                    state.update {
                        copyFunc.invoke(it, DataLoadingState.Success(results))
                    }
                } catch (ex: Exception) {
                    state.update {
                        copyFunc.invoke(it, DataLoadingState.Error(ex))
                    }
                }
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

data class DiscoverRowData(
    val title: String,
    val items: DataLoadingState<List<DiscoverItem>>,
) {
    companion object {
        val EMPTY = DiscoverRowData("", DataLoadingState.Pending)
    }
}

data class DiscoverState(
    val movies: DiscoverRowData = DiscoverRowData.EMPTY,
    val tv: DiscoverRowData = DiscoverRowData.EMPTY,
)

@Composable
fun SeerrDiscoverPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SeerrDiscoverViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val rows = listOf(state.movies, state.tv)

    val focusRequesters = remember(2) { List(rows.size) { FocusRequester() } }
    var position by rememberPosition(0, -1)
    val focusedItem =
        remember(position) {
            position.let {
                (rows.getOrNull(it.row)?.items as? DataLoadingState.Success)?.data?.getOrNull(it.column)
            }
        }
    LaunchedEffect(focusedItem) {
        viewModel.updateBackdrop(focusedItem)
    }
    var firstFocused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.movies) {
        if (!firstFocused && state.movies.items is DataLoadingState.Success<*>) {
            firstFocused = focusRequesters.getOrNull(0)?.tryRequestFocus("discover") == true
        }
    }

    Column(
        modifier = modifier,
    ) {
        HomePageHeader(
            title = focusedItem?.title,
            subtitle = focusedItem?.subtitle,
            overview = focusedItem?.overview,
            overviewTwoLines = true,
            quickDetails = {
                // TODO
            },
            modifier =
                Modifier
                    .fillMaxHeight(.33f)
                    .padding(top = 48.dp, bottom = 32.dp, start = 32.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
            modifier =
                Modifier
                    .focusRestorer()
                    .fillMaxSize(),
        ) {
            itemsIndexed(rows) { rowIndex, row ->
                DiscoverRow(
                    row = row,
                    onClickItem = { index, item ->
                        position = RowColumn(rowIndex, index)
                        viewModel.navigationManager.navigateTo(item.destination)
                    },
                    onLongClickItem = { index, item -> },
                    onCardFocus = { index -> position = RowColumn(rowIndex, index) },
                    focusRequester = focusRequesters[rowIndex],
                    modifier =
                        Modifier
                            .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun DiscoverRow(
    row: DiscoverRowData,
    onClickItem: (Int, DiscoverItem) -> Unit,
    onLongClickItem: (Int, DiscoverItem) -> Unit,
    onCardFocus: (Int) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    when (val state = row.items) {
        is DataLoadingState.Error -> {
            ErrorMessage(state.message, state.exception, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier,
            ) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        is DataLoadingState.Success<List<DiscoverItem>> -> {
            ItemRow(
                title = row.title,
                items = state.data,
                onClickItem = onClickItem,
                onLongClickItem = onLongClickItem,
                cardContent = { index: Int, item: DiscoverItem?, mod: Modifier, onClick: () -> Unit, onLongClick: () -> Unit ->
                    DiscoverItemCard(
                        item = item,
                        onClick = onClick,
                        onLongClick = onLongClick,
                        showOverlay = false,
                        modifier =
                            mod.onFocusChanged {
                                if (it.isFocused) {
                                    onCardFocus.invoke(index)
                                }
                            },
                    )
                },
                modifier = modifier.focusRequester(focusRequester),
            )
        }
    }
}
