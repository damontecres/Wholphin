package com.github.damontecres.wholphin.ui.detail.collection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGridContent
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import java.util.UUID

@Composable
fun CollectionDetails(
    itemId: UUID,
    modifier: Modifier = Modifier,
    viewModel: CollectionViewModel =
        hiltViewModel<CollectionViewModel, CollectionViewModel.Factory>(
            creationCallback = { it.create(itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
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
            Column(modifier = modifier) {
                Text(
                    text = state.collection?.title ?: "",
                )
                if (state.viewOptions.mixed) {
//                    CollectionMixedGrid(state, Modifier.fillMaxSize())
                } else {
                    CollectionRows(state, Modifier.fillMaxSize())
                }
            }
        }
    }
}

@Composable
fun CollectionMixedGrid(
    preferences: UserPreferences,
    state: CollectionState,
    onClickItem: (RowColumn, BaseItem) -> Unit,
    onLongClickItem: (RowColumn, BaseItem) -> Unit,
    onSortChange: (SortAndDirection) -> Unit,
    onViewOptionsChange: (CollectionViewOptions) -> Unit,
    onClickPlay: (RowColumn, BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        CollectionFolderGridContent(
            preferences = preferences,
            item = state.collection,
            title = "",
            loadingState = DataLoadingState.Success(state.items),
            sortAndDirection = state.sortAndDirection,
            onClickItem = { index, item -> onClickItem.invoke(RowColumn(0, index), item) },
            onLongClickItem = { index, item -> onLongClickItem.invoke(RowColumn(0, index), item) },
            onSortChange = onSortChange,
            letterPosition = { 0 },
            sortOptions = emptyList(),
            playEnabled = true,
            getPossibleFilterValues = { emptyList() },
            defaultViewOptions = ViewOptions(showDetails = state.viewOptions.showDetails),
            onSaveViewOptions = {},
            viewOptions = ViewOptions(showDetails = state.viewOptions.showDetails),
            onClickPlayAll = {},
            onClickPlay = { index, item -> onClickPlay.invoke(RowColumn(0, index), item) },
            onChangeBackdrop = {},
            initialPosition = 0,
            showTitle = false,
        )
    }
}

@Composable
fun CollectionRows(
    state: CollectionState,
    modifier: Modifier = Modifier,
) {
}
