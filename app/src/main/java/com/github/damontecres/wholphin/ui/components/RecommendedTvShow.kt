package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.main.HomePageContent
import com.github.damontecres.wholphin.ui.main.HomeRow
import com.github.damontecres.wholphin.ui.main.HomeSection
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetNextUpRequestHandler
import com.github.damontecres.wholphin.util.GetResumeItemsRequestHandler
import com.github.damontecres.wholphin.util.GetSuggestionsRequestHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSuggestionsRequest
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RecommendedTvShowViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) : ViewModel() {
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val rows = MutableLiveData<List<HomeRow>>()

        fun init(
            preferences: UserPreferences,
            parentId: UUID,
        ) {
            viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
                val resumeItemsRequest =
                    GetResumeItemsRequest(
                        parentId = parentId,
                        fields = SlimItemFields,
                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                        enableUserData = true,
                    )
                val resumeItems =
                    ApiRequestPager(api, resumeItemsRequest, GetResumeItemsRequestHandler, viewModelScope, useSeriesForPrimary = true)

                val nextUpRequest =
                    GetNextUpRequest(
                        parentId = parentId,
                        fields = SlimItemFields,
                        enableUserData = true,
                    )
                val nextUpItems = ApiRequestPager(api, nextUpRequest, GetNextUpRequestHandler, viewModelScope, useSeriesForPrimary = true)

                val recentlyReleasedRequest =
                    GetItemsRequest(
                        parentId = parentId,
                        fields = SlimItemFields,
                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                        recursive = true,
                        enableUserData = true,
                        sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                        sortOrder = listOf(SortOrder.DESCENDING),
                    )
                val recentlyReleasedItems =
                    ApiRequestPager(api, recentlyReleasedRequest, GetItemsRequestHandler, viewModelScope, useSeriesForPrimary = true)

                val recentlyAddedRequest =
                    GetItemsRequest(
                        parentId = parentId,
                        fields = SlimItemFields,
                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                        recursive = true,
                        enableUserData = true,
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                    )
                val recentlyAddedItems =
                    ApiRequestPager(api, recentlyAddedRequest, GetItemsRequestHandler, viewModelScope, useSeriesForPrimary = true)

                val suggestionsRequest =
                    GetSuggestionsRequest(
                        userId = serverRepository.currentUser?.id,
                        type = listOf(BaseItemKind.SERIES),
                    )
                val suggestedItems = ApiRequestPager(api, suggestionsRequest, GetSuggestionsRequestHandler, viewModelScope)

                val unwatchedTopRatedRequest =
                    GetItemsRequest(
                        parentId = parentId,
                        fields = SlimItemFields,
                        includeItemTypes = listOf(BaseItemKind.SERIES),
                        recursive = true,
                        enableUserData = true,
                        isPlayed = false,
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                        sortOrder = listOf(SortOrder.DESCENDING),
                    )
                val unwatchedTopRatedItems =
                    ApiRequestPager(api, unwatchedTopRatedRequest, GetItemsRequestHandler, viewModelScope, useSeriesForPrimary = true)

                val rows =
                    listOf(resumeItems, nextUpItems, recentlyReleasedItems, recentlyAddedItems, suggestedItems, unwatchedTopRatedItems)
                rows.forEach { it.init() }
                val homeRows =
                    listOf(
                        HomeRow(HomeSection.RESUME, resumeItems, "Continue Watching"),
                        HomeRow(HomeSection.NEXT_UP, nextUpItems, "Next Up"),
                        HomeRow(HomeSection.LATEST_MEDIA, recentlyReleasedItems, "Recently Released"),
                        HomeRow(HomeSection.LATEST_MEDIA, recentlyAddedItems, "Recently Added"),
                        HomeRow(HomeSection.NONE, suggestedItems, "Suggestions"),
                        HomeRow(HomeSection.NONE, unwatchedTopRatedItems, "Top Rated Unwatched"),
                    ).filter { it.items.isNotEmpty() }
                withContext(Dispatchers.Main) {
                    this@RecommendedTvShowViewModel.rows.value = homeRows
                    loading.value = LoadingState.Success
                }
            }
        }
    }

/**
 * The "recommended" tab of a TV show library
 */
@Composable
fun RecommendedTvShow(
    preferences: UserPreferences,
    parentId: UUID,
    onClickItem: (BaseItem) -> Unit,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendedTvShowViewModel = hiltViewModel(),
) {
    OneTimeLaunchedEffect {
        viewModel.init(preferences, parentId)
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val rows by viewModel.rows.observeAsState(listOf())

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
                modifier = modifier,
            )
    }
}
