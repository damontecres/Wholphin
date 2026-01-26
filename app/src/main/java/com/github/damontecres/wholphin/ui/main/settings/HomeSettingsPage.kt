package com.github.damontecres.wholphin.ui.main.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.ui.main.HomePageContent
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import timber.log.Timber

val settingsWidth = 300.dp

@Composable
fun HomeSettingsPage(
    modifier: Modifier,
    viewModel: HomeSettingsViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var destination by remember { mutableStateOf<HomeSettingsDestination>(HomeSettingsDestination.RowList) }

    val state by viewModel.state.collectAsState()

    BackHandler(destination is HomeSettingsDestination.ChooseRowType) {
        destination = HomeSettingsDestination.ChooseLibrary
    }
    BackHandler(destination is HomeSettingsDestination.ChooseLibrary) {
        destination = HomeSettingsDestination.RowList
    }
    BackHandler(destination is HomeSettingsDestination.RowSettings) {
        destination = HomeSettingsDestination.RowList
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .width(settingsWidth)
                    .fillMaxHeight()
                    .background(color = MaterialTheme.colorScheme.surface),
        ) {
            val destModifier =
                Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            when (val dest = destination) {
                HomeSettingsDestination.RowList -> {
                    HomeSettingsRowList(
                        state = state,
                        onClickAdd = { destination = HomeSettingsDestination.ChooseLibrary },
                        onClickSaveLocal = { viewModel.saveToLocal() },
                        onClickMove = viewModel::moveRow,
                        onClickDelete = viewModel::deleteRow,
                        onClick = { index, row ->
                            destination = HomeSettingsDestination.RowSettings(row.id)
                            scope.launch(ExceptionHandler()) {
                                Timber.v("Scroll to $index")
                                listState.scrollToItem(index)
                            }
                        },
                        onClickResize = {
                            viewModel.resizeCards(it)
                        },
                        modifier = destModifier,
                    )
                }

                is HomeSettingsDestination.ChooseLibrary -> {
                    HomeSettingsLibraryList(
                        libraries = state.libraries,
                        onClick = { destination = HomeSettingsDestination.ChooseRowType(it) },
                        onClickMeta = {
                            viewModel.addRow(it)
                            destination = HomeSettingsDestination.RowList
                        },
                        modifier = destModifier,
                    )
                }

                is HomeSettingsDestination.ChooseRowType -> {
                    HomeLibraryRowTypeList(
                        library = dest.library,
                        onClick = {
                            viewModel.addRow(dest.library, it)
                            destination = HomeSettingsDestination.RowList
                        },
                        modifier = destModifier,
                    )
                }

                is HomeSettingsDestination.RowSettings -> {
                    val row =
                        state.rows
                            .first { it.id == dest.rowId }
                    HomeRowSettings(
                        title = row.title,
                        viewOptions = row.config.viewOptions,
                        onViewOptionsChange = {
                            viewModel.updateViewOptions(dest.rowId, it)
                        },
                        onApplyApplyAll = {
                            viewModel.updateViewOptionsForAll(row.config.viewOptions)
                        },
                        modifier = destModifier,
                    )
                }
            }
        }
        HomePageContent(
            loadingState = state.loading,
            homeRows = state.rowData,
            onClickItem = { _, _ -> },
            onLongClickItem = { _, _ -> },
            onClickPlay = { _, _ -> },
            showClock = false,
            onUpdateBackdrop = viewModel::updateBackdrop,
            listState = listState,
            takeFocus = false,
            modifier =
                Modifier
                    .fillMaxHeight()
                    .weight(1f),
        )
    }
}
