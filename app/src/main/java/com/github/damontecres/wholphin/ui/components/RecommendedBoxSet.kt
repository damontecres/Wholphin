package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.datastore.core.DataStore
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSuggestionsRequest
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = RecommendedBoxSetViewModel.Factory::class)
class RecommendedBoxSetViewModel
    @AssistedInject
    constructor(
        @ApplicationContext context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val preferencesDataStore: DataStore<AppPreferences>,
        @Assisted val parentId: UUID,
        navigationManager: NavigationManager,
        favoriteWatchManager: FavoriteWatchManager,
        backdropService: BackdropService,
    ) : RecommendedViewModel(context, navigationManager, favoriteWatchManager, backdropService) {
        @AssistedFactory
        interface Factory {
            fun create(parentId: UUID): RecommendedBoxSetViewModel
        }

        override val rows =
            MutableStateFlow<List<HomeRowLoadingState>>(
                rowTitles.keys.map {
                    HomeRowLoadingState.Pending(
                        context.getString(it),
                    )
                },
            )

        override fun init() {
            viewModelScope.launch(Dispatchers.IO + ExceptionHandler()) {
                val itemsPerRow =
                    preferencesDataStore.data
                        .firstOrNull()
                        ?.homePagePreferences
                        ?.maxItemsPerRow
                        ?: AppPreference.HomePageItems.defaultValue.toInt()
                try {
                    // Recently Released - Mixed Movies and Series
                    update(R.string.recently_released) {
                        val recentlyReleasedRequest =
                            GetItemsRequest(
                                parentId = parentId,
                                fields = SlimItemFields,
                                includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                                recursive = true,
                                enableUserData = true,
                                sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                startIndex = 0,
                                limit = itemsPerRow,
                                enableTotalRecordCount = false,
                            )
                        GetItemsRequestHandler
                            .execute(api, recentlyReleasedRequest)
                            .toBaseItems(api, false)
                    }

                    if (loading.value == LoadingState.Loading || loading.value == LoadingState.Pending) {
                        loading.setValueOnMain(LoadingState.Success)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception fetching boxset recommendations")
                    withContext(Dispatchers.Main) {
                        loading.value = LoadingState.Error(ex)
                    }
                }

                // Recently Added - Mixed Movies and Series
                update(R.string.recently_added) {
                    val recentlyAddedRequest =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            enableUserData = true,
                            sortBy = listOf(ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            startIndex = 0,
                            limit = itemsPerRow,
                            enableTotalRecordCount = false,
                        )
                    GetItemsRequestHandler
                        .execute(api, recentlyAddedRequest)
                        .toBaseItems(api, false)
                }

                // Suggestions - Mixed Movies and Series (filtered to BoxSet items)
                update(R.string.suggestions) {
                    val userId = serverRepository.currentUser.value?.id
                    
                    // First, fetch all items in this BoxSet to create a filter set
                    val boxSetItemsRequest =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = listOf(),
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            enableUserData = false,
                            startIndex = 0,
                            limit = null, // Get all items
                            enableTotalRecordCount = false,
                        )
                    val boxSetItemIds =
                        GetItemsRequestHandler
                            .execute(api, boxSetItemsRequest)
                            .content
                            .items
                            .mapNotNull { it.id }
                            .toSet()
                    
                    Timber.d("BoxSet has ${boxSetItemIds.size} items for filtering")
                    
                    // Fetch more suggestions to compensate for filtering
                    val movieRequest =
                        GetSuggestionsRequest(
                            userId = userId,
                            type = listOf(BaseItemKind.MOVIE),
                            startIndex = 0,
                            limit = itemsPerRow * 2, // Fetch more since we'll filter
                            enableTotalRecordCount = false,
                        )
                    val seriesRequest =
                        GetSuggestionsRequest(
                            userId = userId,
                            type = listOf(BaseItemKind.SERIES),
                            startIndex = 0,
                            limit = itemsPerRow * 2, // Fetch more since we'll filter
                            enableTotalRecordCount = false,
                        )

                    val movies =
                        GetSuggestionsRequestHandler
                            .execute(api, movieRequest)
                            .toBaseItems(api, false)
                            .filter { it.id in boxSetItemIds }
                    val series =
                        GetSuggestionsRequestHandler
                            .execute(api, seriesRequest)
                            .toBaseItems(api, true)
                            .filter { it.id in boxSetItemIds }

                    Timber.d("BoxSet Suggestions (filtered) - Movies: ${movies.size}, Series: ${series.size}")
                    val result = (movies + series).shuffled().take(itemsPerRow)
                    Timber.d("BoxSet Suggestions (filtered) - Combined: ${result.size} items")
                    result
                }

                // Top Rated Unwatched - Mixed Movies and Series
                update(R.string.top_unwatched) {
                    val unwatchedTopRatedRequest =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            recursive = true,
                            enableUserData = true,
                            isPlayed = false,
                            sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            startIndex = 0,
                            limit = itemsPerRow,
                            enableTotalRecordCount = false,
                        )
                    GetItemsRequestHandler
                        .execute(api, unwatchedTopRatedRequest)
                        .toBaseItems(api, false)
                }

                if (loading.value == LoadingState.Loading || loading.value == LoadingState.Pending) {
                    loading.setValueOnMain(LoadingState.Success)
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
                    R.string.recently_released,
                    R.string.recently_added,
                    R.string.suggestions,
                    R.string.top_unwatched,
                ).mapIndexed { index, i -> i to index }.toMap()
        }
    }

/**
 * The "recommended" tab of a boxset
 */
@Composable
fun RecommendedBoxSet(
    preferences: UserPreferences,
    parentId: UUID,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendedBoxSetViewModel =
        hiltViewModel<RecommendedBoxSetViewModel, RecommendedBoxSetViewModel.Factory>(
            creationCallback = { it.create(parentId) },
        ),
) {
    RecommendedContent(
        preferences = preferences,
        viewModel = viewModel,
        onFocusPosition = onFocusPosition,
        modifier = modifier,
    )
}
