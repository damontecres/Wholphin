package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.GetResumeItemsRequestHandler
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import java.util.UUID

private fun getRecommendedRows(parentId: UUID) =
    listOf(
        RecommendedRow(
            title = R.string.continue_watching,
            handler = GetResumeItemsRequestHandler,
            request =
                GetResumeItemsRequest(
                    parentId = parentId,
                    fields = SlimItemFields,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    enableUserData = true,
                    enableTotalRecordCount = false,
                ),
        ),
        RecommendedRow(
            title = R.string.recently_released,
            handler = GetItemsRequestHandler,
            request =
                GetItemsRequest(
                    parentId = parentId,
                    fields = SlimItemFields,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    recursive = true,
                    enableUserData = true,
                    sortBy = listOf(ItemSortBy.PREMIERE_DATE),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    enableTotalRecordCount = false,
                ),
        ),
        RecommendedRow(
            title = R.string.recently_added,
            handler = GetItemsRequestHandler,
            request =
                GetItemsRequest(
                    parentId = parentId,
                    fields = SlimItemFields,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    recursive = true,
                    enableUserData = true,
                    sortBy = listOf(ItemSortBy.DATE_CREATED),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    enableTotalRecordCount = false,
                ),
        ),
        RecommendedRow(
            title = R.string.top_unwatched,
            handler = GetItemsRequestHandler,
            request =
                GetItemsRequest(
                    parentId = parentId,
                    fields = SlimItemFields,
                    includeItemTypes = listOf(BaseItemKind.MOVIE),
                    recursive = true,
                    enableUserData = true,
                    isPlayed = false,
                    sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    enableTotalRecordCount = false,
                ),
        ),
    )

/**
 * The "recommended" tab of a movie library
 */
@Composable
fun RecommendedMovie(
    preferences: UserPreferences,
    parentId: UUID,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendedViewModel =
        hiltViewModel<RecommendedViewModel, RecommendedViewModel.Factory>(
            creationCallback = {
                it.create(
                    parentId = parentId,
                    suggestionsType = BaseItemKind.MOVIE,
                    recommendedRows = getRecommendedRows(parentId),
                    viewOptions = HomeRowViewOptions(),
                )
            },
        ),
) {
    RecommendedContent(
        preferences = preferences,
        viewModel = viewModel,
        onFocusPosition = onFocusPosition,
        modifier = modifier,
    )
}
