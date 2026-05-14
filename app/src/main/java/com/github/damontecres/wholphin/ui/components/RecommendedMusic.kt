package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.AspectRatio
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID

private fun getRecommendedRows(parentId: UUID) =
    listOf(
        RecommendedRow(
            title = R.string.recently_released,
            handler = GetItemsRequestHandler,
            request =
                GetItemsRequest(
                    parentId = parentId,
                    fields = SlimItemFields,
                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
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
                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
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
                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                    recursive = true,
                    enableUserData = true,
                    isPlayed = false,
                    sortBy = listOf(ItemSortBy.COMMUNITY_RATING),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    enableTotalRecordCount = false,
                ),
        ),
    )

@Composable
fun RecommendedMusic(
    preferences: UserPreferences,
    parentId: UUID,
    onFocusPosition: (RowColumn) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecommendedViewModel =
        hiltViewModel<RecommendedViewModel, RecommendedViewModel.Factory>(
            creationCallback = {
                it.create(
                    parentId = parentId,
                    suggestionsType = BaseItemKind.MUSIC_ALBUM,
                    recommendedRows = getRecommendedRows(parentId),
                    viewOptions =
                        HomeRowViewOptions(
                            aspectRatio = AspectRatio.SQUARE,
                            heightDp = Cards.HEIGHT_EPISODE,
                            showTitles = true,
                        ),
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
