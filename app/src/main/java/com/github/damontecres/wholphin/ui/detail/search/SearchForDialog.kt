package com.github.damontecres.wholphin.ui.detail.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.components.VoiceSearchButton
import com.github.damontecres.wholphin.ui.main.SearchResult
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun SearchForContent(
    searchType: BaseItemKind,
    onClick: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchForViewModel = hiltViewModel(key = searchType.serialName),
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val state by viewModel.state.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    val searchFocusRequester = remember { FocusRequester() }
    val focusRequester = remember { FocusRequester() }

    var immediateSearchQuery by rememberSaveable { mutableStateOf<String?>(null) }

    LifecycleResumeEffect(Unit) {
        onPauseOrDispose {
            viewModel.voiceInputManager.stopListening()
        }
    }

    fun triggerImmediateSearch(searchQuery: String) {
        immediateSearchQuery = searchQuery
        viewModel.search(searchType, searchQuery)
    }

    LaunchedEffect(query) {
        when {
            immediateSearchQuery == query -> {
                immediateSearchQuery = null
            }

            else -> {
                delay(750L)
                viewModel.search(searchType, query)
            }
        }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            var isSearchActive by remember { mutableStateOf(false) }
            var isTextFieldFocused by remember { mutableStateOf(false) }
            val textFieldFocusRequester = remember { FocusRequester() }

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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .focusGroup()
                        .focusRestorer(textFieldFocusRequester)
                        .focusRequester(searchFocusRequester),
            ) {
                VoiceSearchButton(
                    onSpeechResult = { spokenText ->
                        query = spokenText
                        triggerImmediateSearch(spokenText)
                    },
                    voiceInputManager = viewModel.voiceInputManager,
                )

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
                            .focusRequester(textFieldFocusRequester)
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

        when (val st = state.results) {
            is SearchResult.Error -> {
                ErrorMessage("Error", st.ex)
            }

            SearchResult.NoQuery -> {
                // no-op
            }

            SearchResult.Searching -> {
                Text(
                    text = stringResource(R.string.searching),
                )
            }

            is SearchResult.SuccessSeerr -> {
                Text(
                    text = "Not supported",
                    color = MaterialTheme.colorScheme.error,
                )
            }

            is SearchResult.Success -> {
                if (st.items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_results),
                    )
                } else {
                    val titleRes =
                        remember {
                            when (searchType) {
                                BaseItemKind.BOX_SET -> R.string.collections
                                BaseItemKind.PLAYLIST -> R.string.playlists
                                else -> null
                            }
                        }
                    ItemRow(
                        title = titleRes?.let { stringResource(it) } ?: "",
                        items = st.items,
                        onClickItem = { _, item -> onClick.invoke(item) },
                        onLongClickItem = { _, _ -> },
                        modifier = Modifier.focusRequester(focusRequester),
                        cardContent = { index, item, mod, onClick, onLongClick ->
                            SeasonCard(
                                item = item,
                                onClick = {
                                    onClick.invoke()
                                },
                                onLongClick = onLongClick,
                                imageHeight = Cards.height2x3,
                                modifier = mod,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SearchForDialog(
    onDismissRequest: () -> Unit,
    searchType: BaseItemKind,
    onClick: (BaseItem) -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        SearchForContent(
            searchType = searchType,
            onClick = onClick,
            modifier =
                Modifier
                    .padding(8.dp)
                    .fillMaxWidth(.8f)
                    .fillMaxHeight(.66f),
        )
    }
}
