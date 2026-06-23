package com.github.damontecres.wholphin.ui.discover

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.components.VoiceInputManager
import com.github.damontecres.wholphin.ui.components.VoiceSearchButton
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.SearchCombinedResults
import com.github.damontecres.wholphin.ui.main.SearchResult
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DiscoverSearchViewModel
    @Inject
    constructor(
        val navigationManager: NavigationManager,
        private val seerrService: SeerrService,
        val voiceInputManager: VoiceInputManager,
        val userPreferencesService: UserPreferencesService,
    ) : ViewModel() {
        val seerrResults = MutableStateFlow<SearchResult>(SearchResult.NoQuery)

        private var searchJob: Job? = null
        var currentQuery: String = ""

        fun search(query: String) {
            if (query == currentQuery) {
                return
            }
            currentQuery = query
            viewModelScope.launchIO {
                if (query.isBlank()) {
                    seerrResults.value = SearchResult.NoQuery
                    return@launchIO
                }
                Timber.v("Starting seerr search")
                seerrResults.value = SearchResult.Searching
                val results =
                    seerrService
                        .search(query)
                        .map { seerrService.createDiscoverItem(it) }
                        .filter { it.type == SeerrItemType.MOVIE || it.type == SeerrItemType.TV }
                Timber.v("Seerr search complete: %s results", results.size)
                seerrResults.value = SearchResult.SuccessSeerr(results)
            }
        }
    }

@Composable
fun DiscoverSearchPage(
    preferences: UserPreferences,
    positionCallback: (columns: Int, position: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverSearchViewModel = hiltViewModel(),
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val seerrResults by viewModel.seerrResults.collectAsState()
    var immediateSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf(viewModel.currentQuery) }
    var position by rememberInt(-1)

    // Start with current preferences, but collect updates when view options change
    val prefs =
        viewModel.userPreferencesService.flow
            .collectAsState(preferences)
            .value.appPreferences.interfacePreferences.searchPreferences

    val onClickDiscover = { _: Int, item: DiscoverItem ->
        val dest =
            if (item.jellyfinItemId != null && item.type.baseItemKind != null) {
                Destination.MediaItem(
                    itemId = item.jellyfinItemId,
                    type = item.type.baseItemKind,
                )
            } else {
                Destination.DiscoveredItem(item)
            }
        viewModel.navigationManager.navigateTo(dest)
    }

    fun triggerImmediateSearch(searchQuery: String) {
        immediateSearchQuery = searchQuery
        viewModel.search(searchQuery)
    }

    LaunchedEffect(query) {
        when {
            immediateSearchQuery == query -> {
                immediateSearchQuery = null
            }

            else -> {
                delay(750L)
                viewModel.search(query)
            }
        }
    }
    val gridFocusRequester = remember { FocusRequester() }
    val textFieldFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        if (position >= 0) {
            gridFocusRequester.tryRequestFocus("grid")
        } else {
            textFieldFocusRequester.tryRequestFocus("textField")
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            var isSearchActive by remember { mutableStateOf(false) }
            var isTextFieldFocused by remember { mutableStateOf(false) }

            BackHandler(isTextFieldFocused) {
                when {
                    isSearchActive -> {
                        isSearchActive = false
                        keyboardController?.hide()
                    }

                    else -> {
                        focusManager.moveFocus(FocusDirection.Next)
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .padding(start = 16.dp, end = 16.dp)
                        .focusGroup()
                        .focusRestorer()
                        .focusRequester(textFieldFocusRequester),
            ) {
                AnimatedVisibility(
                    visible = prefs.showVoiceSearchButton,
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.End),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.End),
                ) {
                    VoiceSearchButton(
                        onSpeechResult = { spokenText ->
                            query = spokenText
                            triggerImmediateSearch(spokenText)
                        },
                        voiceInputManager = viewModel.voiceInputManager,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }

                SearchEditTextBox(
                    value = query,
                    onValueChange = {
                        isSearchActive = true
                        query = it
                    },
                    onSearchClick = { triggerImmediateSearch(query) },
                    readOnly = !isSearchActive,
                    modifier =
                        Modifier
                            .onFocusChanged { state ->
                                isTextFieldFocused = state.isFocused
                                if (!state.isFocused) isSearchActive = false
                            }.onPreviewKeyEvent { event ->
                                val isActivationKey =
                                    event.key in listOf(Key.DirectionCenter, Key.Enter)
                                if (event.type == KeyEventType.KeyUp && isActivationKey && !isSearchActive) {
                                    isSearchActive = true
                                    keyboardController?.show()
                                    true
                                } else {
                                    false
                                }
                            },
                )
            }
        }
        SearchCombinedResults(
            result = seerrResults,
            focusRequester = gridFocusRequester,
            onClickItem = { _, _ -> },
            onPlayItem = { _, _ -> },
            onClickPosition = { position = it.column },
            onClickDiscover = onClickDiscover,
            positionCallback = { columns, index ->
                position = index
                positionCallback.invoke(columns, index)
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
