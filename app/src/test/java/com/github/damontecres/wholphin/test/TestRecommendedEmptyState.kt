package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.RecommendedState
import com.github.damontecres.wholphin.ui.components.recommendedLoadingState
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TestRecommendedEmptyState {
    private val title = ResStringProvider(R.string.suggestions)

    private fun loading() = HomeRowLoadingState.Loading(title)

    private fun empty() = HomeRowLoadingState.Success(title, emptyList())

    private fun content() = HomeRowLoadingState.Success(title, listOf(mockk<BaseItem>()))

    @Test
    fun emptyRowsFinishAfterSuggestionsComplete() {
        val initial = RecommendedState(LoadingState.Loading, listOf(loading(), loading(), loading()))
        val rowsDone = initial.withRow(0, empty()).withRow(1, empty())
        assertEquals(LoadingState.Loading, rowsDone.loading)
        val complete = rowsDone.withRow(2, empty())
        assertEquals(LoadingState.Success, complete.loading)
        assertTrue(complete.rows.all { it is HomeRowLoadingState.Success && it.items.isEmpty() })
        assertTrue(initial.rows.all { it is HomeRowLoadingState.Loading })
    }

    @Test
    fun laterRowContentDoesNotWaitForEarlierRow() {
        val state = RecommendedState(LoadingState.Loading, listOf(loading(), loading(), loading()))
        assertEquals(LoadingState.Success, state.withRow(1, content()).loading)
    }

    @Test
    fun suggestionsOnlyContentFinishesLoading() {
        val state = RecommendedState(LoadingState.Loading, listOf(loading(), loading()))
        assertEquals(LoadingState.Success, state.withRow(1, content()).loading)
    }

    @Test
    fun noConfiguredRowsFinishesWithEmptySuggestions() {
        val state = RecommendedState(LoadingState.Loading, listOf(loading()))
        assertEquals(LoadingState.Success, state.withRow(0, empty()).loading)
        assertEquals(LoadingState.Success, recommendedLoadingState(emptyList()))
    }

    @Test
    fun failuresCompleteWithoutLosingErrorRows() {
        val error = HomeRowLoadingState.Error(title, "Failed", null)
        val state = RecommendedState(LoadingState.Loading, listOf(loading(), loading()))
        val complete = state.withRow(0, error).withRow(1, empty())
        assertEquals(LoadingState.Success, complete.loading)
        assertEquals(error, complete.rows.first())
        assertEquals(LoadingState.Success, recommendedLoadingState(listOf(error, error)))
    }

    @Test
    fun pendingRowsDoNotBecomeEmptySuccess() {
        assertEquals(
            LoadingState.Loading,
            recommendedLoadingState(listOf(HomeRowLoadingState.Pending(title), empty())),
        )
    }

    @Test
    fun suggestionRefreshDoesNotRestoreFullScreenSpinner() {
        val complete = RecommendedState(LoadingState.Success, listOf(empty(), empty()))
        assertEquals(LoadingState.Success, complete.withRow(1, loading()).loading)
    }
}
