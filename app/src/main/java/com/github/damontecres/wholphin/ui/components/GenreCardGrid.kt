package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetGenresRequestHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class GenreViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private lateinit var itemId: UUID
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val genres = MutableLiveData<List<BaseItem?>>(listOf())

        fun init(itemId: UUID) {
            loading.value = LoadingState.Loading
            this.itemId = itemId
            viewModelScope.launch(Dispatchers.IO + LoadingExceptionHandler(loading, "Failed to fetch genres")) {
                val request =
                    GetGenresRequest(
                        parentId = itemId,
                        fields = SlimItemFields,
                    )
                val pager = ApiRequestPager(api, request, GetGenresRequestHandler, viewModelScope).init()
                withContext(Dispatchers.Main) {
                    genres.value = pager
                    loading.value = LoadingState.Success
                }
            }
        }

        suspend fun positionOfLetter(letter: Char): Int =
            withContext(Dispatchers.IO) {
                val request =
                    GetGenresRequest(
                        parentId = itemId,
                        nameLessThan = letter.toString(),
                        limit = 0,
                        enableTotalRecordCount = true,
                    )
                val result by GetGenresRequestHandler.execute(api, request)
                return@withContext result.totalRecordCount
            }
    }

@Composable
fun GenreCardGrid(
    itemId: UUID,
    modifier: Modifier = Modifier,
    viewModel: GenreViewModel = hiltViewModel(),
) {
    OneTimeLaunchedEffect {
        viewModel.init(itemId)
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)
    val genres by viewModel.genres.observeAsState(listOf())

    val gridFocusRequester = remember { FocusRequester() }
    when (val st = loading) {
        LoadingState.Pending,
        LoadingState.Loading,
        -> LoadingPage(modifier.focusable())

        is LoadingState.Error -> ErrorMessage(st, modifier.focusable())

        LoadingState.Success ->
            Box(modifier = modifier) {
                LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
                CardGrid(
                    pager = genres,
                    onClickItem = { genre ->
                        viewModel.navigationManager.navigateTo(
                            Destination.FilteredCollection(
                                itemId = itemId,
                                filter = GetItemsFilter(genres = listOf(genre.id)),
                                recursive = true,
                            ),
                        )
                    },
                    onLongClickItem = {},
                    letterPosition = { viewModel.positionOfLetter(it) },
                    gridFocusRequester = gridFocusRequester,
                    showJumpButtons = false,
                    showLetterButtons = true,
                    modifier = Modifier.fillMaxSize(),
                    initialPosition = 0,
                    positionCallback = { columns, position ->
                    },
                    cardContent = { item: BaseItem?, onClick: () -> Unit, onLongClick: () -> Unit, mod: Modifier ->
                        GridCard(
                            item = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = mod,
                            imageAspectRatio = 1f,
                        )
                    },
                )
            }
    }
}
