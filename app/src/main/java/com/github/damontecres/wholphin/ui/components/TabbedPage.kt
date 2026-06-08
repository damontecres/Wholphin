package com.github.damontecres.wholphin.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.RememberedTabService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.ui.util.StringProvider
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

@HiltViewModel(assistedFactory = TabViewModel.Factory::class)
class TabViewModel
    @AssistedInject
    constructor(
        private val userPreferencesService: UserPreferencesService,
        private val rememberedTabService: RememberedTabService,
        private val backdropService: BackdropService,
        @param:Assisted private val itemId: String,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(itemId: String): TabViewModel
        }

        private val _state = MutableStateFlow<Int>(-1)
        val state: StateFlow<Int> = _state

        init {
            viewModelScope.launchIO {
                val startingTab =
                    if (rememberTabs()) {
                        Timber.v("Getting remembered tab for %s", itemId)
                        rememberedTabService.getRememberedTab(itemId) ?: 0
                    } else {
                        0
                    }
                _state.value = startingTab
            }
        }

        private suspend fun rememberTabs(): Boolean =
            userPreferencesService
                .getCurrent()
                .appPreferences.interfacePreferences.rememberSelectedTab

        fun saveTabIndex(tabIndex: Int) {
            _state.value = tabIndex
            viewModelScope.launchIO {
                backdropService.clearBackdrop()
                if (rememberTabs()) {
                    Timber.v("Saving remembered tab for %s: %s", itemId, tabIndex)
                    rememberedTabService.saveRememberedTab(itemId, tabIndex)
                }
            }
        }
    }

data class TabDetails(
    val title: StringProvider,
    val tabFocusRequester: FocusRequester = FocusRequester(),
    val contentFocusRequester: FocusRequester = FocusRequester(),
) {
    constructor(
        @StringRes stringResId: Int,
    ) : this(ResStringProvider(stringResId), FocusRequester(), FocusRequester())
}

@Composable
fun TabbedPage(
    itemId: String,
    tabs: List<TabDetails>,
    modifier: Modifier = Modifier,
    showTabs: Boolean = true,
    viewModel: TabViewModel =
        hiltViewModel<TabViewModel, TabViewModel.Factory>(
            key = itemId,
            creationCallback = { it.create(itemId) },
        ),
    tabContent: @Composable (Int, TabDetails) -> Unit,
) {
    val selectedTabIndex by viewModel.state.collectAsState()
    val tabTitles = tabs.map { it.title.getString() }
    val tabFocusRequesters = tabs.map { it.contentFocusRequester }

    Column(
        modifier = modifier,
    ) {
        AnimatedVisibility(
            showTabs,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier =
                    Modifier
                        .padding(vertical = 16.dp),
                tabs = tabTitles,
                onClick = viewModel::saveTabIndex,
                focusRequesters = tabFocusRequesters,
            )
        }
        selectedTabIndex.let { tabIndex ->
            if (tabIndex >= 0) {
                tabContent.invoke(tabIndex, tabs[tabIndex])
            } else {
                // TODO
            }
        }
    }
}
