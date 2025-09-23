package com.github.damontecres.dolphin.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.DolphinModel
import com.github.damontecres.dolphin.data.model.convertModel
import com.github.damontecres.dolphin.isNotNullOrBlank
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.cards.DolphinCard
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import timber.log.Timber
import javax.inject.Inject

data class HomeRow(
    val section: HomeSection,
    val items: List<DolphinModel>,
)

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        val api: ApiClient,
    ) : ViewModel() {
        val homeRows = MutableLiveData<List<HomeRow>>()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                val user = api.userApi.getCurrentUser().content
                val displayPrefs =
                    api.displayPreferencesApi
                        .getDisplayPreferences(
                            displayPreferencesId = "usersettings",
                            client = "emby",
                        ).content
                val homeSections =
                    displayPrefs.customPrefs.entries
                        .filter { it.key.startsWith("homesection") && it.value.isNotNullOrBlank() }
                        .sortedBy { it.key }
                        .map { HomeSection.fromKey(it.value ?: "") }
                        .filterNot { it == HomeSection.NONE }

                val homeRows =
                    homeSections
                        .mapNotNull { section ->
                            Timber.v("Loading section: %s", section.name)
                            when (section) {
                                HomeSection.LATEST_MEDIA -> {
                                    user.configuration?.orderedViews?.firstOrNull()?.let { viewId ->
                                        val request =
                                            GetLatestMediaRequest(
                                                fields = listOf(),
                                                imageTypeLimit = 1,
                                                parentId = viewId,
                                                groupItems = true,
                                                limit = 25,
                                            )
                                        val latest =
                                            api.userLibraryApi
                                                .getLatestMedia(request)
                                                .content
                                                .map { convertModel(it, api) }
                                        HomeRow(
                                            section = section,
                                            items = latest,
                                        )
                                    }
                                }

                                // TODO
                                HomeSection.LIBRARY_TILES_SMALL -> null
                                HomeSection.RESUME -> {
                                    val request =
                                        GetResumeItemsRequest(
                                            userId = user.id,
                                            // TODO, more params?
                                        )
                                    val items =
                                        api.itemsApi
                                            .getResumeItems(request)
                                            .content
                                            .items
                                            .map { convertModel(it, api) }
                                    HomeRow(
                                        section = section,
                                        items = items,
                                    )
                                }
                                HomeSection.ACTIVE_RECORDINGS -> null
                                HomeSection.NEXT_UP -> {
                                    val request =
                                        GetNextUpRequest(
                                            fields = listOf(),
                                            imageTypeLimit = 1,
                                            parentId = null,
                                            limit = 25,
                                            enableResumable = false,
                                        )
                                    val nextUp =
                                        api.tvShowsApi
                                            .getNextUp(request)
                                            .content
                                            .items
                                            .map { convertModel(it, api) }
                                    HomeRow(
                                        section = section,
                                        items = nextUp,
                                    )
                                }
                                HomeSection.LIVE_TV -> null

                                // TODO Not supported?
                                HomeSection.LIBRARY_BUTTONS -> null
                                HomeSection.RESUME_AUDIO -> null
                                HomeSection.RESUME_BOOK -> null
                                HomeSection.NONE -> null
                            }
                        }.filter { it.items.isNotEmpty() }
                withContext(Dispatchers.Main) {
                    this@MainViewModel.homeRows.value = homeRows
                }
            }
        }
    }

@Composable
fun MainPage(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    modifier: Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val homeRows by viewModel.homeRows.observeAsState(listOf())
    Column(modifier = modifier) {
        // TODO header?
        LazyColumn {
            homeRows.forEach { row ->
                item {
                    HomePageRow(
                        row = row,
                        onClickItem = {
                            navigationManager.navigateTo(
                                Destination.MediaItem(
                                    it.id,
                                    it.type,
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun HomePageRow(
    row: HomeRow,
    onClickItem: (DolphinModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(row.section.nameRes),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(8.dp),
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth(),
        ) {
            items(row.items) { item ->
                DolphinCard(
                    item = item,
                    onClick = { onClickItem.invoke(item) },
                    modifier = Modifier,
                )
            }
        }
    }
}
