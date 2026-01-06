package com.github.damontecres.wholphin.ui.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.logTab
import com.github.damontecres.wholphin.ui.nav.NavDrawerItem
import com.github.damontecres.wholphin.ui.preferences.PreferencesViewModel
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun DiscoverPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    preferencesViewModel: PreferencesViewModel = hiltViewModel(),
) {
    val rememberedTabIndex =
        remember { preferencesViewModel.getRememberedTab(preferences, NavDrawerItem.Discover.id, 0) }

    val tabs =
        listOf(
            stringResource(R.string.discover),
            stringResource(R.string.request),
        )
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(rememberedTabIndex) }
    val focusRequester = remember { FocusRequester() }

    val firstTabFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstTabFocusRequester.tryRequestFocus() }

    LaunchedEffect(selectedTabIndex) {
        logTab("discover", selectedTabIndex)
        preferencesViewModel.saveRememberedTab(preferences, NavDrawerItem.Discover.id, selectedTabIndex)
        preferencesViewModel.backdropService.clearBackdrop()
    }

    var showHeader by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Column(
        modifier = modifier,
    ) {
        AnimatedVisibility(
            showHeader,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier =
                    Modifier
                        .padding(start = 32.dp, top = 16.dp, bottom = 16.dp)
                        .focusRequester(firstTabFocusRequester),
                tabs = tabs,
                onClick = { selectedTabIndex = it },
            )
        }
        when (selectedTabIndex) {
            // Discover
            0 -> {
                SeerrDiscoverPage(
                    preferences = preferences,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Requests
            1 -> {
                SeerrRequestsPage(
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                ErrorMessage("Invalid tab index $selectedTabIndex", null)
            }
        }
    }
}
