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
import com.github.damontecres.wholphin.data.model.BaseItem
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
import com.github.damontecres.wholphin.util.GetResumeItemsRequestHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = RecommendedMovieViewModel.Factory::class)
class RecommendedMovieViewModel
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
            fun create(parentId: UUID): RecommendedMovieViewModel
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
                    val resumeItemsRequest =
                        GetResumeItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            enableUserData = true,
                            startIndex = 0,
                            limit = itemsPerRow,
                            enableTotalRecordCount = false,
                        )
                    val resumeItems =
                        GetResumeItemsRequestHandler
                            .execute(api, resumeItemsRequest)
                            .toBaseItems(api, false)
                    update(
                        R.string.continue_watching,
                        HomeRowLoadingState.Success(
                            context.getString(R.string.continue_watching),
                            resumeItems,
                        ),
                    )

                    if (resumeItems.isNotEmpty()) {
                        loading.setValueOnMain(LoadingState.Success)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Exception fetching movie recommendations")
                    withContext(Dispatchers.Main) {
                        loading.value = LoadingState.Error(ex)
                    }
                }

                update(R.string.recently_released) {
                    val recentlyReleasedRequest =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
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

                update(R.string.recently_added) {
                    val recentlyAddedRequest =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
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

                update(R.string.suggestions) {
                    val userId = serverRepository.currentUser.value?.id
                    val contextualLimit = (itemsPerRow * 0.4).toInt().coerceAtLeast(1)
                    val randomLimit = (itemsPerRow * 0.3).toInt().coerceAtLeast(1)
                    val freshLimit = (itemsPerRow * 0.3).toInt().coerceAtLeast(1)

                    // Source 1: Contextual - fetch recent history and deduplicate
                    val historyRequest =
                        GetItemsRequest(
                            parentId = parentId,
                            userId = userId,
                            fields = SlimItemFields + listOf(ItemFields.GENRES),
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            recursive = true,
                            isPlayed = true,
                            sortBy = listOf(ItemSortBy.DATE_PLAYED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = 20,
                            enableTotalRecordCount = false,
                        )
                    val historyItems =
                        GetItemsRequestHandler
                            .execute(api, historyRequest)
                            .content
                            .items
                            .orEmpty()

                    // Deduplicate by seriesId (for episodes) or id (for movies)
                    val seedItems = historyItems
                        .distinctBy { it.seriesId ?: it.id }
                        .take(3)

                    // Collect all genre IDs from seed items
                    val allGenreIds = seedItems
                        .flatMap { it.genreItems?.mapNotNull { g -> g.id } ?: emptyList() }
                        .distinct()

                    // Exclude all seed items from recommendations
                    val excludeIds = seedItems.map { it.id }

                    // Run all queries in parallel using async
                    val contextualDeferred =
                        viewModelScope.async(Dispatchers.IO) {
                            if (allGenreIds.isEmpty()) return@async emptyList()

                            val contextualRequest =
                                GetItemsRequest(
                                    parentId = parentId,
                                    fields = SlimItemFields,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                                    genreIds = allGenreIds,
                                    recursive = true,
                                    excludeItemIds = excludeIds,
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    limit = contextualLimit,
                                    enableTotalRecordCount = false,
                                )
                            GetItemsRequestHandler
                                .execute(api, contextualRequest)
                                .content
                                .items
                                .orEmpty()
                        }

                    val randomDeferred =
                        viewModelScope.async(Dispatchers.IO) {
                            val randomRequest =
                                GetItemsRequest(
                                    parentId = parentId,
                                    fields = SlimItemFields,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                                    recursive = true,
                                    isPlayed = false,
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    limit = randomLimit,
                                    enableTotalRecordCount = false,
                                )
                            GetItemsRequestHandler
                                .execute(api, randomRequest)
                                .content
                                .items
                                .orEmpty()
                        }

                    val freshDeferred =
                        viewModelScope.async(Dispatchers.IO) {
                            val freshRequest =
                                GetItemsRequest(
                                    parentId = parentId,
                                    fields = SlimItemFields,
                                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                                    recursive = true,
                                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                                    sortOrder = listOf(SortOrder.DESCENDING),
                                    limit = freshLimit,
                                    enableTotalRecordCount = false,
                                )
                            GetItemsRequestHandler
                                .execute(api, freshRequest)
                                .content
                                .items
                                .orEmpty()
                        }

                    // Await all and combine
                    val contextual = contextualDeferred.await()
                    val random = randomDeferred.await()
                    val fresh = freshDeferred.await()

                    (contextual + random + fresh)
                        .distinctBy { it.id }
                        .shuffled()
                        .take(itemsPerRow)
                        .map { BaseItem.from(it, api, false) }
                }

                update(R.string.top_unwatched) {
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
                    R.string.continue_watching,
                    R.string.recently_released,
                    R.string.recently_added,
                    R.string.suggestions,
                    R.string.top_unwatched,
                ).mapIndexed { index, i -> i to index }.toMap()
        }
    }

/**
 * The "recommended" tab of a movie library
 */
@Composable
fun RecommendedMovie(
    preferences: UserPreferences,
    parentId: UUID,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendedMovieViewModel =
        hiltViewModel<RecommendedMovieViewModel, RecommendedMovieViewModel.Factory>(
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
