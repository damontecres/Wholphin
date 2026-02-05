package com.github.damontecres.wholphin.ui.main.settings

import androidx.annotation.StringRes
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.main.HomePageContent
import com.github.damontecres.wholphin.ui.main.settings.HomeSettingsDestination.ChooseRowType
import com.github.damontecres.wholphin.ui.main.settings.HomeSettingsDestination.RowSettings
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

val settingsWidth = 360.dp

@Composable
fun HomeSettingsPage(
    modifier: Modifier,
    viewModel: HomeSettingsViewModel = hiltViewModel(),
) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val backStack = rememberNavBackStack(HomeSettingsDestination.RowList)
    var showConfirmDialog by remember { mutableStateOf<ShowConfirm?>(null) }

    val state by viewModel.state.collectAsState()
    // TODO discover rows
    val discoverEnabled = false // by viewModel.discoverEnabled.collectAsState(false)

    // Adds a row, waits until its done loading, then scrolls to the new row
    fun addRow(func: () -> Job) {
        scope.launch(ExceptionHandler(autoToast = true)) {
            backStack.add(HomeSettingsDestination.RowList)
            func.invoke().join()
            listState.animateScrollToItem(state.rows.lastIndex)
        }
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
            NavDisplay(
                backStack = backStack,
//                onBack = { navigationManager.goBack() },
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                modifier = Modifier.background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)),
                entryProvider = { key ->
                    val dest = key as HomeSettingsDestination
                    NavEntry(dest, contentKey = key.toString()) {
                        val destModifier =
                            Modifier
                                .fillMaxSize()
                                .padding(8.dp)
                        when (dest) {
                            HomeSettingsDestination.RowList -> {
                                HomeSettingsRowList(
                                    state = state,
                                    onClickAdd = { backStack.add(HomeSettingsDestination.AddRow) },
                                    onClickSettings = { backStack.add(HomeSettingsDestination.GlobalSettings) },
                                    onClickMove = viewModel::moveRow,
                                    onClickDelete = viewModel::deleteRow,
                                    onClick = { index, row ->
                                        backStack.add(RowSettings(row.id))
                                        scope.launch(ExceptionHandler()) {
                                            Timber.v("Scroll to $index")
                                            listState.scrollToItem(index)
                                        }
                                    },
                                    modifier = destModifier,
                                )
                            }

                            is HomeSettingsDestination.AddRow -> {
                                HomeSettingsAddRow(
                                    libraries = state.libraries,
                                    showDiscover = discoverEnabled,
                                    onClick = { backStack.add(ChooseRowType(it)) },
                                    onClickMeta = {
                                        when (it) {
                                            MetaRowType.CONTINUE_WATCHING,
                                            MetaRowType.NEXT_UP,
                                            MetaRowType.COMBINED_CONTINUE_WATCHING,
                                            -> {
                                                addRow { viewModel.addRow(it) }
                                            }

                                            MetaRowType.FAVORITES -> {
                                                backStack.add(HomeSettingsDestination.ChooseFavorite)
                                            }

                                            MetaRowType.DISCOVER -> {
                                                backStack.add(HomeSettingsDestination.ChooseDiscover)
                                            }
                                        }
                                    },
                                    modifier = destModifier,
                                )
                            }

                            is ChooseRowType -> {
                                HomeLibraryRowTypeList(
                                    library = dest.library,
                                    onClick = { type ->
                                        addRow { viewModel.addRow(dest.library, type) }
                                    },
                                    modifier = destModifier,
                                )
                            }

                            is RowSettings -> {
                                val row =
                                    state.rows
                                        .first { it.id == dest.rowId }
                                val preferenceOptions =
                                    remember(row.config) {
                                        when (row.config) {
                                            is HomeRowConfig.ContinueWatching,
                                            is HomeRowConfig.ContinueWatchingCombined,
                                            -> Options.OPTIONS_EPISODES

                                            is HomeRowConfig.Genres -> Options.GENRE_OPTIONS

                                            else -> Options.OPTIONS
                                        }
                                    }
                                HomeRowSettings(
                                    title = row.title,
                                    preferenceOptions = preferenceOptions,
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

                            HomeSettingsDestination.ChooseDiscover -> {
                                TODO()
                            }

                            HomeSettingsDestination.ChooseFavorite -> {
                                HomeSettingsFavoriteList(
                                    onClick = { type ->
                                        addRow { viewModel.addFavoriteRow(type) }
                                    },
                                )
                            }

                            HomeSettingsDestination.GlobalSettings -> {
                                HomeSettingsGlobal(
                                    onClickResize = { viewModel.resizeCards(it) },
                                    onClickSave = {
                                        showConfirmDialog =
                                            ShowConfirm(R.string.overwrite_server_settings) {
                                                viewModel.saveToRemote()
                                            }
                                    },
                                    onClickLoad = {
                                        showConfirmDialog =
                                            ShowConfirm(R.string.overwrite_local_settings) {
                                                viewModel.loadFromRemote()
                                            }
                                    },
                                    onClickLoadWeb = {
                                        showConfirmDialog =
                                            ShowConfirm(R.string.overwrite_local_settings) {
                                                viewModel.loadFromRemoteWeb()
                                            }
                                    },
                                    onClickReset = {
                                        showConfirmDialog =
                                            ShowConfirm(R.string.overwrite_local_settings) {
                                                viewModel.resetToDefault()
                                            }
                                    },
                                    modifier = destModifier,
                                )
                            }
                        }
                    }
                },
            )
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
    showConfirmDialog?.let { (body, onConfirm) ->
        ConfirmDialog(
            title = stringResource(R.string.confirm),
            body = stringResource(body),
            onCancel = { showConfirmDialog = null },
            onConfirm = {
                onConfirm.invoke()
                showConfirmDialog = null
            },
        )
    }
}

data class ShowConfirm(
    @param:StringRes val body: Int,
    val onConfirm: () -> Unit,
)
