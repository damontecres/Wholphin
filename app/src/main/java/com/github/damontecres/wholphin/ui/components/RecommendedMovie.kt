package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.HomePageContent
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetResumeItemsRequestHandler
import com.github.damontecres.wholphin.util.GetSuggestionsRequestHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSuggestionsRequest
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = RecommendedMovieViewModel.Factory::class)
class RecommendedMovieViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        @Assisted val parentId: UUID,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(parentId: UUID): RecommendedMovieViewModel
        }

        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)

        val rows =
            MutableStateFlow<MutableList<HomeRowLoadingState>>(
                rowTitles
                    .map {
                        HomeRowLoadingState.Pending(
                            context.getString(it),
                        )
                    }.toMutableList(),
            )

        fun init() {
            viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
                try {
                    val resumeItemsRequest =
                        GetResumeItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            enableUserData = true,
                        )
                    val resumeItems =
                        ApiRequestPager(
                            api,
                            resumeItemsRequest,
                            GetResumeItemsRequestHandler,
                            viewModelScope,
                        ).init()
                    if (resumeItems.isNotEmpty()) {
                        resumeItems.getBlocking(0)
                    }

                    update(
                        0,
                        HomeRowLoadingState.Success(
                            context.getString(R.string.continue_watching),
                            resumeItems,
                        ),
                    )

                    withContext(Dispatchers.Main) {
                        loading.value = LoadingState.Success
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception fetching movie recommendations")
                    withContext(Dispatchers.Main) {
                        loading.value = LoadingState.Error(ex)
                    }
                }

                val recentlyReleasedRequest =
                    GetItemsRequest(
                        parentId = parentId,
                        fields = SlimItemFields,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        enableUserData = true,
                        sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                        sortOrder = listOf(SortOrder.DESCENDING),
                    )
                val recentlyReleasedItems =
                    ApiRequestPager(api, recentlyReleasedRequest, GetItemsRequestHandler, viewModelScope)

                val recentlyAddedRequest =
                    GetItemsRequest(
                        parentId = parentId,
                        fields = SlimItemFields,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        enableUserData = true,
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                    )
                val recentlyAddedItems =
                    ApiRequestPager(api, recentlyAddedRequest, GetItemsRequestHandler, viewModelScope)

                val suggestionsRequest =
                    GetSuggestionsRequest(
                        userId = serverRepository.currentUser?.id,
                        type = listOf(BaseItemKind.MOVIE),
                    )
                val suggestedItems = ApiRequestPager(api, suggestionsRequest, GetSuggestionsRequestHandler, viewModelScope)

                val unwatchedTopRatedRequest =
                    GetItemsRequest(
                        parentId = parentId,
                        fields = SlimItemFields,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        recursive = true,
                        enableUserData = true,
                        isPlayed = false,
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                        sortOrder = listOf(SortOrder.DESCENDING),
                    )
                val unwatchedTopRatedItems =
                    ApiRequestPager(api, unwatchedTopRatedRequest, GetItemsRequestHandler, viewModelScope, useSeriesForPrimary = true)

                val rows =
                    listOf(
                        R.string.recently_released to recentlyReleasedItems,
                        R.string.recently_added to recentlyAddedItems,
                        R.string.suggestions to suggestedItems,
                        R.string.top_unwatched to unwatchedTopRatedItems,
                    )

                rows.forEachIndexed { index, (title, pager) ->
                    viewModelScope.launchIO {
                        val title = context.getString(title)
                        val result =
                            try {
                                pager.init()
                                HomeRowLoadingState.Success(title, pager)
                            } catch (ex: Exception) {
                                Timber.e(ex, "Error fetching %s", title)
                                HomeRowLoadingState.Error(title, null, ex)
                            }
                        update(index + 1, result)
                    }
                }
            }
        }

        private fun update(
            position: Int,
            row: HomeRowLoadingState,
        ) {
            rows.update { current ->
                current.apply { set(position, row) }
            }
        }

        companion object {
            private val rowTitles =
                listOf(
                    R.string.continue_watching,
                    R.string.recently_released,
                    R.string.recently_added,
                    R.string.suggestions,
                    R.string.top_unwatched,
                )
        }
    }

/**
 * The "recommended" tab of a movie library
 */
@Composable
fun RecommendedMovie(
    preferences: UserPreferences,
    parentId: UUID,
    onClickItem: (BaseItem) -> Unit,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendedMovieViewModel =
        hiltViewModel<RecommendedMovieViewModel, RecommendedMovieViewModel.Factory>(
            creationCallback = { it.create(parentId) },
        ),
) {
    OneTimeLaunchedEffect {
        viewModel.init()
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val rows by viewModel.rows.collectAsState()

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)

        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()

        LoadingState.Success ->
            HomePageContent(
                homeRows = rows,
                onClickItem = onClickItem,
                onLongClickItem = {},
                onFocusPosition = onFocusPosition,
                showClock = preferences.appPreferences.interfacePreferences.showClock,
                modifier = modifier,
            )
    }
}
