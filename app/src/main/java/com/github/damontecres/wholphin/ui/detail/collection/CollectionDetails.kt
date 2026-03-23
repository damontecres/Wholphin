package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.util.LoadingState
import java.util.UUID

@Composable
fun CollectionDetails(
    preferences: UserPreferences,
    itemId: UUID,
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel =
        hiltViewModel<CollectionViewModel, CollectionViewModel.Factory>(
            creationCallback = { it.create(itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    val onClickItem =
        remember {
            { position: RowColumn, item: BaseItem ->
            }
        }
    val onLongClickItem =
        remember {
            { position: RowColumn, item: BaseItem ->
            }
        }
    val onSortChange =
        remember {
            { sort: SortAndDirection -> viewModel.changeSort(sort) }
        }
    val onFilterChange = remember { { filter: GetItemsFilter -> } }
    val onClickPlay = { position: RowColumn, item: BaseItem ->
    }
    val onClickPlayAll = remember { { shuffle: Boolean -> } }
    val onChangeBackdrop = remember { { item: BaseItem -> } }

    var showViewOptionsDialog by remember { mutableStateOf(false) }
    val onClickViewOptions = remember { { showViewOptionsDialog = true } }

    when (val s = state.loadingState) {
        is LoadingState.Error -> {
            ErrorMessage(s, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier,
            ) {
                Text(
                    text = state.collection?.title ?: "",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(.85f),
                )
                if (state.viewOptions.mixed) {
                    CollectionMixedGrid(
                        preferences = preferences,
                        state = state,
                        onClickItem = onClickItem,
                        onLongClickItem = onLongClickItem,
                        onSortChange = onSortChange,
                        onClickPlay = onClickPlay,
                        onClickPlayAll = onClickPlayAll,
                        onChangeBackdrop = onChangeBackdrop,
                        onFilterChange = onFilterChange,
                        getPossibleFilterValues = viewModel::getPossibleFilterValues,
                        letterPosition = viewModel::letterPosition,
                        onClickViewOptions = onClickViewOptions,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CollectionRows(state, Modifier.fillMaxSize())
                }
            }
        }
    }
    if (showViewOptionsDialog) {
        CollectionViewOptionsDialog(
            viewOptions = state.viewOptions,
            onDismissRequest = { showViewOptionsDialog = false },
            onViewOptionsChange = viewModel::changeViewOptions,
        )
    }
}

@Composable
fun CollectionRows(
    state: CollectionState,
    modifier: Modifier = Modifier,
) {
}
