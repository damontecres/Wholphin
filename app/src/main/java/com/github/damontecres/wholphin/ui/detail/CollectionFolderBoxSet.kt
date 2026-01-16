package com.github.damontecres.wholphin.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.preferences.BoxSetViewMode
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.CollectionFolderGrid
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.RecommendedBoxSet
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.data.BoxSetSortOptions
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.logTab
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = BoxSetViewModel.Factory::class)
class BoxSetViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        @Assisted val itemId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): BoxSetViewModel
        }

        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val boxSetItem = MutableLiveData<BaseItem?>(null)

        init {
            viewModelScope.launchIO(LoadingExceptionHandler(loading, "Error fetching BoxSet item")) {
                val item = api.userLibraryApi.getItem(itemId).content.let {
                    BaseItem.from(it, api)
                }
                boxSetItem.setValueOnMain(item)
                loading.setValueOnMain(LoadingState.Success)
            }
        }
    }

@Composable
fun CollectionFolderBoxSet(
    preferences: UserPreferences,
    itemId: UUID,
    recursive: Boolean,
    modifier: Modifier = Modifier,
    filter: CollectionFolderFilter = CollectionFolderFilter(),
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
    playEnabled: Boolean = false,
) {
    val boxSetViewMode = preferences.appPreferences.interfacePreferences.boxsetViewMode
    
    when (boxSetViewMode) {
        BoxSetViewMode.DEFAULT_GRID -> {
            // Original default grid implementation
            CollectionFolderBoxSetDefaultGrid(
                preferences = preferences,
                itemId = itemId,
                recursive = recursive,
                filter = filter,
                preferencesViewModel = preferencesViewModel,
                playEnabled = playEnabled,
                modifier = modifier,
            )
        }
        
        BoxSetViewMode.ADVANCED_VIEW -> {
            // New advanced view with tabs
            CollectionFolderBoxSetAdvanced(
                preferences = preferences,
                itemId = itemId,
                recursive = recursive,
                filter = filter,
                preferencesViewModel = preferencesViewModel,
                playEnabled = playEnabled,
                modifier = modifier,
            )
        }
        
        else -> {
            // Fallback to default grid
            CollectionFolderBoxSetDefaultGrid(
                preferences = preferences,
                itemId = itemId,
                recursive = recursive,
                filter = filter,
                preferencesViewModel = preferencesViewModel,
                playEnabled = playEnabled,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun CollectionFolderBoxSetDefaultGrid(
    preferences: UserPreferences,
    itemId: UUID,
    recursive: Boolean,
    filter: CollectionFolderFilter,
    preferencesViewModel: PreferencesViewModel,
    playEnabled: Boolean,
    modifier: Modifier,
) {
    var showHeader by remember { mutableStateOf(true) }
    CollectionFolderGrid(
        preferences = preferences,
        onClickItem = { _, item -> preferencesViewModel.navigationManager.navigateTo(item.destination()) },
        itemId = itemId,
        initialFilter = filter,
        showTitle = showHeader,
        recursive = recursive,
        sortOptions = BoxSetSortOptions,
        initialSortAndDirection = SortAndDirection(ItemSortBy.DEFAULT, SortOrder.ASCENDING),
        modifier =
            modifier
                .padding(start = 16.dp),
        positionCallback = { columns, position ->
            showHeader = position < columns
        },
        defaultViewOptions = ViewOptionsPoster,
        playEnabled = playEnabled,
    )
}

@Composable
private fun CollectionFolderBoxSetAdvanced(
    preferences: UserPreferences,
    itemId: UUID,
    recursive: Boolean,
    filter: CollectionFolderFilter,
    preferencesViewModel: PreferencesViewModel,
    playEnabled: Boolean,
    modifier: Modifier,
) {
    // Fetch BoxSet item to get name
    val boxSetViewModel: BoxSetViewModel =
        hiltViewModel<BoxSetViewModel, BoxSetViewModel.Factory>(
            creationCallback = { it.create(itemId) },
        )
    val boxSetItem by boxSetViewModel.boxSetItem.observeAsState()
    val loading by boxSetViewModel.loading.observeAsState(LoadingState.Loading)
    
    when (loading) {
        is LoadingState.Error -> {
            ErrorMessage(loading as LoadingState.Error)
        }
        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage()
        }
        LoadingState.Success -> {
            val boxSetName = boxSetItem?.name ?: stringResource(R.string.collection)
            
            val rememberedTabIndex =
                remember { preferencesViewModel.getRememberedTab(preferences, itemId, 0) }

            val tabs =
                listOf(
                    stringResource(R.string.recommended_boxset_name, boxSetName),
                    stringResource(R.string.library),
                )
            var selectedTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
            val focusRequester = remember { FocusRequester() }
            val tabFocusRequesters = remember { List(tabs.size) { FocusRequester() } }

            val firstTabFocusRequester = remember { FocusRequester() }

            LaunchedEffect(selectedTabIndex) {
                logTab("boxset", selectedTabIndex)
                preferencesViewModel.saveRememberedTab(preferences, itemId, selectedTabIndex)
                preferencesViewModel.backdropService.clearBackdrop()
            }

            var showHeader by rememberSaveable { mutableStateOf(true) }

            LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
            
            Column(
                modifier = modifier,
            ) {
                AnimatedVisibility(
                    showHeader,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut(),
                ) {
                    TabRow(
                        selectedTabIndex = selectedTabIndex,
                        modifier =
                            Modifier
                                .padding(start = 32.dp, top = 16.dp, bottom = 16.dp)
                                .focusRequester(firstTabFocusRequester),
                        tabs = tabs,
                        onClick = { selectedTabIndex = it },
                        focusRequesters = tabFocusRequesters,
                    )
                }
                when (selectedTabIndex) {
                    // Recommended tab
                    0 -> {
                        RecommendedBoxSet(
                            preferences = preferences,
                            parentId = itemId,
                            onFocusPosition = { pos ->
                                showHeader = pos.row < 1
                            },
                            modifier =
                                Modifier
                                    .padding(start = 16.dp)
                                    .fillMaxSize()
                                    .focusRequester(focusRequester),
                        )
                    }

                    // Library tab
                    1 -> {
                        CollectionFolderGrid(
                            preferences = preferences,
                            onClickItem = { _, item ->
                                preferencesViewModel.navigationManager.navigateTo(item.destination())
                            },
                            itemId = itemId,
                            viewModelKey = "${itemId}_library",
                            initialFilter = filter,
                            showTitle = false,
                            recursive = recursive,
                            sortOptions = BoxSetSortOptions,
                            defaultViewOptions = ViewOptionsPoster,
                            modifier =
                                Modifier
                                    .padding(start = 16.dp)
                                    .fillMaxSize()
                                    .focusRequester(focusRequester),
                            positionCallback = { columns, position ->
                                showHeader = position < columns
                            },
                            playEnabled = playEnabled,
                            focusRequesterOnEmpty = tabFocusRequesters.getOrNull(selectedTabIndex),
                        )
                    }

                    else -> {
                        ErrorMessage("Invalid tab index $selectedTabIndex", null)
                    }
                }
            }
        }
    }
}
