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
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SuggestionService
import com.github.damontecres.wholphin.services.SuggestionsResource
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID

@HiltViewModel(assistedFactory = RecommendedMusicViewModel.Factory::class)
class RecommendedMusicViewModel
    @AssistedInject
    constructor(
        @ApplicationContext context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val preferencesDataStore: DataStore<AppPreferences>,
        private val suggestionService: SuggestionService,
        @Assisted val parentId: UUID,
        navigationManager: NavigationManager,
        favoriteWatchManager: FavoriteWatchManager,
        mediaReportService: MediaReportService,
        backdropService: BackdropService,
        mediaManagementService: MediaManagementService,
    ) : RecommendedViewModel(
            context,
            navigationManager,
            favoriteWatchManager,
            mediaReportService,
            backdropService,
            mediaManagementService,
        ) {
        @AssistedFactory
        interface Factory {
            fun create(parentId: UUID): RecommendedMusicViewModel
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

                val jobs = mutableListOf<Deferred<HomeRowLoadingState>>()
                val viewOptions =
                    HomeRowViewOptions(
                        aspectRatio = AspectRatio.SQUARE,
                        heightDp = Cards.HEIGHT_EPISODE,
                        showTitles = true,
                    )

                update(R.string.recently_released, viewOptions) {
                    val request =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            recursive = true,
                            enableUserData = true,
                            sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            startIndex = 0,
                            limit = itemsPerRow,
                            enableTotalRecordCount = false,
                        )
                    GetItemsRequestHandler.execute(api, request).toBaseItems(api, false)
                }.also(jobs::add)

                update(R.string.recently_added, viewOptions) {
                    val request =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            recursive = true,
                            enableUserData = true,
                            sortBy = listOf(ItemSortBy.DATE_CREATED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            startIndex = 0,
                            limit = itemsPerRow,
                            enableTotalRecordCount = false,
                        )
                    GetItemsRequestHandler.execute(api, request).toBaseItems(api, false)
                }.also(jobs::add)

                update(R.string.top_unwatched, viewOptions) {
                    val request =
                        GetItemsRequest(
                            parentId = parentId,
                            fields = SlimItemFields,
                            includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                            recursive = true,
                            enableUserData = true,
                            isPlayed = false,
                            sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            startIndex = 0,
                            limit = itemsPerRow,
                            enableTotalRecordCount = false,
                        )
                    GetItemsRequestHandler.execute(api, request).toBaseItems(api, false)
                }.also(jobs::add)

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        suggestionService
                            .getSuggestionsFlow(parentId, BaseItemKind.MUSIC_ALBUM)
                            .collect { resource ->
                                val state =
                                    when (resource) {
                                        is SuggestionsResource.Loading -> {
                                            HomeRowLoadingState.Loading(
                                                context.getString(R.string.suggestions),
                                            )
                                        }

                                        is SuggestionsResource.Success -> {
                                            HomeRowLoadingState.Success(
                                                context.getString(R.string.suggestions),
                                                resource.items,
                                            )
                                        }

                                        is SuggestionsResource.Empty -> {
                                            HomeRowLoadingState.Success(
                                                context.getString(R.string.suggestions),
                                                emptyList(),
                                            )
                                        }
                                    }
                                update(R.string.suggestions, state)
                            }
                    } catch (ex: Exception) {
                        Timber.e(ex, "Failed to fetch suggestions")
                        update(
                            R.string.suggestions,
                            HomeRowLoadingState.Error(
                                title = context.getString(R.string.suggestions),
                                exception = ex,
                            ),
                        )
                    }
                }

                for (i in 0..<jobs.size) {
                    val result = jobs[i].await()
                    if (result.completed) {
                        Timber.v("First success")
                        loading.setValueOnMain(LoadingState.Success)
                    }
                    break
                }
            }
        }

        override fun update(
            @StringRes title: Int,
            row: HomeRowLoadingState,
        ): HomeRowLoadingState {
            rows.update { current ->
                current.toMutableList().apply { set(rowTitles[title]!!, row) }
            }
            return row
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

@Composable
fun RecommendedMusic(
    preferences: UserPreferences,
    parentId: UUID,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendedMusicViewModel =
        hiltViewModel<RecommendedMusicViewModel, RecommendedMusicViewModel.Factory>(
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
