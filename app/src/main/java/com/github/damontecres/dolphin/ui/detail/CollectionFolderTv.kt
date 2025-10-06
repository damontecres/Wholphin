package com.github.damontecres.dolphin.ui.detail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.components.ErrorMessage
import com.github.damontecres.dolphin.ui.components.RecommendedTvShow
import com.github.damontecres.dolphin.ui.ifElse
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.tryRequestFocus

@Composable
fun CollectionFolderTv(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf("Recommended", "Library")
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val firstTabFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Column(
        modifier = modifier,
    ) {
        TabRow(
            selectedTabIndex = selectedTabIndex,
            modifier =
                Modifier
                    .padding(start = 32.dp, top = 16.dp)
                    .focusRestorer(firstTabFocusRequester),
            tabs = {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        onFocus = {},
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier =
                                Modifier
                                    .padding(8.dp)
                                    .ifElse(index == 0, Modifier.focusRequester(firstTabFocusRequester)),
                        )
                    }
                }
            },
        )
        when (selectedTabIndex) {
            0 -> {
                RecommendedTvShow(
                    preferences = preferences,
                    navigationManager = navigationManager,
                    parentId = destination.itemId,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                )
            }
            1 -> {
                CollectionFolderDetails(
                    preferences = preferences,
                    navigationManager = navigationManager,
                    destination = destination,
                    showTitle = false,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxSize()
                            .focusRequester(focusRequester),
                )
            }
            else -> ErrorMessage("Invalid tab index $selectedTabIndex", null)
        }
    }
}
