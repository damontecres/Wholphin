package com.github.damontecres.wholphin.ui.main

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun CustomPagePage(
    pageId: String,
    title: String,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: CustomPageViewModel = hiltViewModel(),
) {
    LaunchedEffect(pageId) { viewModel.load(pageId) }
    val state by viewModel.state.collectAsState()

    when (val loading = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(loading, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            var position by rememberPosition()
            val listState = rememberLazyListState()
            HomePageContent(
                homeRows = state.rows,
                position = position,
                onFocusPosition = { position = it },
                onClickItem = { _, item ->
                    viewModel.navigationManager.navigateTo(item.destination())
                },
                onLongClickItem = { _, _ -> },
                onClickPlay = { _, item ->
                    viewModel.navigationManager.navigateTo(Destination.Playback(item))
                },
                showClock = preferences.appPreferences.interfacePreferences.showClock,
                onUpdateBackdrop = viewModel::updateBackdrop,
                showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                showViewMore = false,
                modifier = modifier,
                loadingState = LoadingState.Success,
                listState = listState,
            )
        }
    }
}
