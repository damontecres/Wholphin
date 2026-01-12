package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel(assistedFactory = DiscoverPersonViewModel.Factory::class)
class DiscoverPersonViewModel
    @AssistedInject
    constructor(
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val seerrService: SeerrService,
        private val backdropService: BackdropService,
        @Assisted val item: DiscoverItem,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(item: DiscoverItem): DiscoverPersonViewModel
        }

        val credits = MutableStateFlow<DataLoadingState<List<DiscoverItem>>>(DataLoadingState.Pending)

        init {
            viewModelScope.launchIO {
                backdropService.clearBackdrop()

                val credits =
                    seerrService.api.personApi
                        .personPersonIdCombinedCreditsGet(personId = item.id)
                        .let { credits ->
                            val cast =
                                credits.cast
                                    ?.map(::DiscoverItem)
                                    .orEmpty()
                            val crew =
                                credits.crew
                                    ?.map(::DiscoverItem)
                                    .orEmpty()
                            cast + crew
                        }
                this@DiscoverPersonViewModel.credits.update {
                    DataLoadingState.Success(credits)
                }
            }
        }
    }

@Composable
fun DiscoverPersonPage(
    person: DiscoverItem,
    modifier: Modifier = Modifier,
    viewModel: DiscoverPersonViewModel =
        hiltViewModel<DiscoverPersonViewModel, DiscoverPersonViewModel.Factory>(
            creationCallback = { it.create(person) },
        ),
) {
    val credits by viewModel.credits.collectAsState()

    when (val state = credits) {
        is DataLoadingState.Error -> {
            ErrorMessage(state.message, state.exception, modifier.focusable())
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier.focusable())
        }

        is DataLoadingState.Success<List<DiscoverItem>> -> {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (state.data.isNotEmpty()) {
                    focusRequester.tryRequestFocus()
                }
            }
            Column(modifier = modifier) {
                Text(
                    text = stringResource(R.string.discover) + (person.title?.let { ": $it" } ?: ""),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.data.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_results),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CardGrid(
                        pager = state.data,
                        onClickItem = { index: Int, item: DiscoverItem ->
                            viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(item))
                        },
                        onLongClickItem = { index: Int, item: DiscoverItem ->
                        },
                        onClickPlay = { _, item ->
                        },
                        letterPosition = { c: Char -> 0 },
                        gridFocusRequester = focusRequester,
                        showJumpButtons = false,
                        showLetterButtons = false,
                        spacing = 16.dp,
                        cardContent = @Composable { item, onClick, onLongClick, mod ->
                            DiscoverItemCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                showOverlay = true,
                                modifier = mod,
                            )
                        },
                        columns = 6,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
