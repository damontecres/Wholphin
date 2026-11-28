package com.github.damontecres.wholphin.ui.main.settings

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.filter.FilterValueOption
import com.github.damontecres.wholphin.data.filter.ItemFilterBy
import com.github.damontecres.wholphin.data.filter.filterOptionsFor
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.services.FilterOptionCache
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.FilterOptionTitle
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.SelectedLeadingContent
import com.github.damontecres.wholphin.ui.components.isFilterOptionSelected
import com.github.damontecres.wholphin.ui.components.updateFilter
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber

@HiltViewModel(assistedFactory = ByParentFilterViewModel.Factory::class)
class ByParentFilterViewModel
    @AssistedInject
    constructor(
        private val filterOptionCache: FilterOptionCache,
        @Assisted private val library: Library?,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(library: Library?): ByParentFilterViewModel
        }

        private val _state = MutableStateFlow(ByParentFilterState())
        val state = _state.asStateFlow()

        fun fetchPossibleFilterValues(filterBy: ItemFilterBy<out Any>) {
            _state.update { it.copy(possibleFilterValues = DataLoadingState.Loading) }
            viewModelScope.launchDefault {
                try {
                    val result = filterOptionCache.getFilterOptionValues(library?.itemId, filterBy)
                    _state.update { it.copy(possibleFilterValues = DataLoadingState.Success(result)) }
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching filter options for %s", filterBy)
                    _state.update { it.copy(possibleFilterValues = DataLoadingState.Error(ex)) }
                }
            }
        }
    }

data class ByParentFilterState(
    val possibleFilterValues: DataLoadingState<List<FilterValueOption>> = DataLoadingState.Pending,
)

@Composable
fun ByParentFilterDialog(
    show: Boolean,
    library: Library?,
    current: GetItemsFilter,
    onDismissRequest: () -> Unit,
    onFilterChange: (GetItemsFilter) -> Unit,
) {
    if (show) {
        BasicDialog(
            onDismissRequest = onDismissRequest,
            elevation = 3.dp,
        ) {
            ByParentFilterDialogContent(
                library = library,
                current = current,
                onFilterChange = onFilterChange,
                onDismissRequest = onDismissRequest,
            )
        }
    }
}

@Composable
fun ByParentFilterDialogContent(
    library: Library?,
    current: GetItemsFilter,
    onFilterChange: (GetItemsFilter) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ByParentFilterViewModel =
        hiltViewModel<ByParentFilterViewModel, ByParentFilterViewModel.Factory>(
            creationCallback = { it.create(library) },
        ),
) {
    val state by viewModel.state.collectAsState()
    val filterOptions = filterOptionsFor(library?.collectionType)
    val filterCount = current.countFilters(filterOptions)

    var showSubMenu by remember { mutableStateOf<ItemFilterBy<out Any>?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        modifier = modifier,
    ) {
        if (filterCount > 0) {
            item {
                val interactionSource = remember { MutableInteractionSource() }
                val focused by interactionSource.collectIsFocusedAsState()
                ListItem(
                    selected = false,
                    onClick = {
                        onFilterChange.invoke(current.delete(filterOptions))
                        onDismissRequest.invoke()
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color.Red.copy(alpha = .8f),
                        )
                    },
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.remove),
                            color = if (focused) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                        )
                    },
                    interactionSource = interactionSource,
                )
                HorizontalDivider()
            }
        }
        items(filterOptions) { filterOption ->
            val currentValue = filterOption.get(current)
            ListItem(
                selected = false,
                onClick = {
                    viewModel.fetchPossibleFilterValues(filterOption)
                    showSubMenu = filterOption
                },
                leadingContent = {
                    SelectedLeadingContent(currentValue != null)
                },
                headlineContent = {
                    Text(
                        text = stringResource(filterOption.stringRes),
                    )
                },
            )
        }
    }
    showSubMenu?.let { filterOption ->
        BasicDialog(
            onDismissRequest = { showSubMenu = null },
            elevation = 5.dp,
        ) {
            filterOption as ItemFilterBy<Any>
            val currentValue = remember(current) { filterOption.get(current) }

            when (val s = state.possibleFilterValues) {
                is DataLoadingState.Error -> {
                    ErrorMessage(s)
                }

                DataLoadingState.Loading,
                DataLoadingState.Pending,
                -> {
                    LoadingPage()
                }

                is DataLoadingState.Success<List<FilterValueOption>> -> {
                    LazyColumn(
                        contentPadding = PaddingValues(8.dp),
                        modifier = modifier,
                    ) {
                        if (currentValue != null) {
                            item {
                                val interactionSource = remember { MutableInteractionSource() }
                                val focused by interactionSource.collectIsFocusedAsState()
                                ListItem(
                                    selected = false,
                                    onClick = {
                                        onFilterChange.invoke(filterOption.set(null, current))
                                        showSubMenu = null
                                    },
                                    leadingContent = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = Color.Red.copy(alpha = .8f),
                                        )
                                    },
                                    headlineContent = {
                                        Text(
                                            text = stringResource(R.string.remove),
                                            color = if (focused) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    interactionSource = interactionSource,
                                )
                                HorizontalDivider()
                            }
                        }
                        itemsIndexed(s.data) { index, value ->
                            val isSelected =
                                remember(filterOption, value, currentValue) {
                                    isFilterOptionSelected(filterOption, value, currentValue)
                                }
                            val interactionSource = remember { MutableInteractionSource() }
                            val focused by interactionSource.collectIsFocusedAsState()
                            ListItem(
                                selected = false,
                                onClick = {
                                    val newFilter =
                                        updateFilter(
                                            current = current,
                                            filterOption = filterOption,
                                            isSelected = isSelected,
                                            value = value,
                                            currentValue = currentValue,
                                        )
                                    onFilterChange.invoke(newFilter)
                                    if (!filterOption.supportMultiple) {
                                        showSubMenu = null
                                    }
                                },
                                leadingContent = {
                                    SelectedLeadingContent(isSelected)
                                },
                                headlineContent = {
                                    FilterOptionTitle(
                                        filterOption = filterOption,
                                        value = value,
                                        focused = focused,
                                    )
                                },
                                interactionSource = interactionSource,
                            )
                        }
                    }
                }
            }
        }
    }
}
