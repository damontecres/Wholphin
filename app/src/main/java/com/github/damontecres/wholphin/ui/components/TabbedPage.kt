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
        @param:Assisted private val tabCount: Int,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                itemId: String,
                tabCount: Int,
            ): TabViewModel
        }

        private val _state = MutableStateFlow<Int>(UNSET)
        val state: StateFlow<Int> = _state

        init {
            viewModelScope.launchIO {
                val startingTab =
                    if (shouldRememberTabs()) {
                        Timber.v("Getting remembered tab for %s", itemId)
                        try {
                            rememberedTabService
                                .getRememberedTab(itemId)
                                ?.coerceIn(0..<tabCount)
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error getting tab for %s", itemId)
                            null
                        } ?: 0
                    } else {
                        0
                    }
                _state.value = startingTab
            }
        }

        private suspend fun shouldRememberTabs(): Boolean =
            userPreferencesService
                .getCurrent()
                .appPreferences.interfacePreferences.rememberSelectedTab

        fun updateSelectedTabIndex(tabIndex: Int) {
            _state.value = tabIndex
            viewModelScope.launchIO {
                backdropService.clearBackdrop()
                if (shouldRememberTabs()) {
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
    ) : this(ResStringProvider(stringResId))
}

const val UNSET = -1234

/**
 * A page showing multiple tabs
 *
 * This handles remembering the selected tabs via [TabViewModel] if needed
 */
@Composable
fun TabbedPage(
    itemId: String,
    tabs: List<TabDetails>,
    modifier: Modifier = Modifier,
    showTabs: Boolean = true,
    viewModel: TabViewModel =
        hiltViewModel<TabViewModel, TabViewModel.Factory>(
            key = "$itemId-${tabs.size}",
            creationCallback = { it.create(itemId, tabs.size) },
        ),
    tabContent: @Composable (Int, TabDetails) -> Unit,
) {
    val selectedTabIndex by viewModel.state.collectAsState()

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
                tabs = tabs,
                onClick = viewModel::updateSelectedTabIndex,
            )
        }
        selectedTabIndex.let { tabIndex ->
            if (tabIndex >= 0) {
                tabContent.invoke(tabIndex, tabs[tabIndex])
            } else {
                DelayedLoadingPage(focusEnabled = false)
            }
        }
    }
}
