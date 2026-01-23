package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetNextUpRequestHandler
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
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFilter
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.api.request.GetSuggestionsRequest
import timber.log.Timber

@HiltViewModel(assistedFactory = RecommendedCollectionViewModel.Factory::class)
class RecommendedCollectionViewModel
    @AssistedInject
    constructor(
        @ApplicationContext context: Context,
        navigationManager: NavigationManager,
        favoriteWatchManager: FavoriteWatchManager,
        backdropService: BackdropService,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        @Assisted private val parentId: UUID,
    ) : RecommendedViewModel(
            context,
            navigationManager,
            favoriteWatchManager,
            backdropService,
        ) {
        @AssistedFactory
        interface Factory {
            fun create(parentId: UUID): RecommendedCollectionViewModel
        }

        override val rows: MutableStateFlow<List<HomeRowLoadingState>> =
            MutableStateFlow(
                listOf(
                    HomeRowLoadingState.Loading(context.getString(R.string.suggestions)),
                    HomeRowLoadingState.Loading(context.getString(R.string.top_unwatched)),
                    HomeRowLoadingState.Loading(context.getString(R.string.latest_series)),
                    HomeRowLoadingState.Loading(context.getString(R.string.latest_movies)),
                    HomeRowLoadingState.Loading(context.getString(R.string.continue_watching)),
                    HomeRowLoadingState.Loading(context.getString(R.string.next_up)),
                ),
            )

        private val itemsPerRow = 20

        override fun init() {
            viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
                val userId = serverRepository.currentUser.value?.id
                
                // Load Suggestions first (priority row)
                update(R.string.suggestions) {
                    val movieRequest =
                        GetSuggestionsRequest(
                            userId = userId,
                            type = listOf(BaseItemKind.MOVIE),
                            startIndex = 0,
                            limit = itemsPerRow / 2,
                            enableTotalRecordCount = false,
                        )
                    val seriesRequest =
                        GetSuggestionsRequest(
                            userId = userId,
                            type = listOf(BaseItemKind.SERIES),
                            startIndex = 0,
                            limit = itemsPerRow / 2,
                            enableTotalRecordCount = false,
                        )

                    val movies =
                        GetSuggestionsRequestHandler
                            .execute(api, movieRequest)
                            .toBaseItems(api, false)
                    val series =
                        GetSuggestionsRequestHandler
                            .execute(api, seriesRequest)
                            .toBaseItems(api, false)

                    Timber.d("Suggestions - Movies: ${movies.size}, Series: ${series.size}")
                    val result = (movies + series).shuffled().take(itemsPerRow)
                    Timber.d("Suggestions - Combined: ${result.size} items")
                    result
                }
                
                // Set loading to Success after first row is loaded
                if (loading.value == LoadingState.Loading || loading.value == LoadingState.Pending) {
                    loading.setValueOnMain(LoadingState.Success)
                }

                // Load remaining rows in parallel
                update(R.string.top_unwatched) {
                val moviesRequest =
                    GetItemsRequest(
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        fields = SlimItemFields,
                        filters = listOf(ItemFilter.IS_NOT_FOLDER),
                        recursive = true,
                        isPlayed = false,
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        startIndex = 0,
                        limit = itemsPerRow / 2,
                        enableTotalRecordCount = false,
                    )
                val seriesRequest =
                    GetItemsRequest(
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.SERIES),
                        fields = SlimItemFields,
                        filters = listOf(ItemFilter.IS_NOT_FOLDER),
                        recursive = true,
                        isPlayed = false,
                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        startIndex = 0,
                        limit = itemsPerRow / 2,
                        enableTotalRecordCount = false,
                    )

                val movies =
                    GetItemsRequestHandler
                        .execute(api, moviesRequest)
                        .toBaseItems(api, false)
                val series =
                    GetItemsRequestHandler
                        .execute(api, seriesRequest)
                        .toBaseItems(api, false)

                Timber.d("Top Unwatched - Movies: ${movies.size}, Series: ${series.size}")
                val result = (movies + series).sortedByDescending { it.data.communityRating }.take(itemsPerRow)
                Timber.d("Top Unwatched - Combined: ${result.size} items")
                result
                }

                // Latest Series
                update(R.string.latest_series) {
                val request =
                    GetItemsRequest(
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.EPISODE),
                        fields = SlimItemFields,
                        filters = listOf(ItemFilter.IS_NOT_FOLDER),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        startIndex = 0,
                        limit = itemsPerRow,
                        enableTotalRecordCount = false,
                    )
                val result = GetItemsRequestHandler
                    .execute(api, request)
                    .toBaseItems(api, true)
                Timber.d("Latest Series (Episodes): ${result.size} items")
                result
                }

                // Latest Movies
                update(R.string.latest_movies) {
                val request =
                    GetItemsRequest(
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        fields = SlimItemFields,
                        filters = listOf(ItemFilter.IS_NOT_FOLDER),
                        recursive = true,
                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                        sortOrder = listOf(SortOrder.DESCENDING),
                        startIndex = 0,
                        limit = itemsPerRow,
                        enableTotalRecordCount = false,
                    )
                val result = GetItemsRequestHandler
                    .execute(api, request)
                    .toBaseItems(api, false)
                Timber.d("Latest Movies: ${result.size} items")
                result
                }
            
                // Movie Continue Watching
                update(R.string.continue_watching) {
                val request =
                    GetResumeItemsRequest(
                        parentId = parentId,
                        includeItemTypes = listOf(BaseItemKind.MOVIE),
                        fields = SlimItemFields,
                        startIndex = 0,
                        limit = itemsPerRow,
                        enableTotalRecordCount = false,
                    )
                val result = GetResumeItemsRequestHandler
                    .execute(api, request)
                    .toBaseItems(api, false)
                Timber.d("Continue Watching (Movies): ${result.size} items")
                result
                }

                // Series Next Up
                update(R.string.next_up) {
                val request =
                    GetNextUpRequest(
                        userId = userId,
                        fields = SlimItemFields,
                        imageTypeLimit = 1,
                        parentId = parentId,
                        limit = itemsPerRow,
                        enableResumable = false,
                        enableUserData = true,
                    )
                val result = GetNextUpRequestHandler
                    .execute(api, request)
                    .toBaseItems(api, true)
                Timber.d("Next Up (Series): ${result.size} items")
                    result
                }
            }
        }

        override fun update(
            @StringRes title: Int,
            row: HomeRowLoadingState,
        ) {
            rows.update { current ->
                current.toMutableList().apply { set(rowTitles[title]!!, row) }
            }
        }

        companion object {
            private val rowTitles =
                listOf(
                    R.string.suggestions,
                    R.string.top_unwatched,
                    R.string.latest_series,
                    R.string.latest_movies,
                    R.string.continue_watching,
                    R.string.next_up,
                ).mapIndexed { index, i -> i to index }.toMap()
        }
    }


@Composable
fun RecommendedCollection(
    parentId: UUID,
    preferences: UserPreferences,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendedCollectionViewModel =
        hiltViewModel<RecommendedCollectionViewModel, RecommendedCollectionViewModel.Factory> { factory ->
            factory.create(parentId)
        },
) {
    RecommendedContent(
        preferences = preferences,
        viewModel = viewModel,
        onFocusPosition = onFocusPosition,
        modifier = modifier,
    )
}