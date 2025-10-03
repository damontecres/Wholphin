package com.github.damontecres.dolphin.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.data.model.Library
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.dolphin.ui.components.ErrorMessage
import com.github.damontecres.dolphin.ui.components.LoadingPage
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.tryRequestFocus
import com.github.damontecres.dolphin.util.ApiRequestPager
import com.github.damontecres.dolphin.util.GetItemsRequestHandler
import com.github.damontecres.dolphin.util.LoadingExceptionHandler
import com.github.damontecres.dolphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CollectionFolderViewModel
    @Inject
    constructor(
        api: ApiClient,
    ) : ItemViewModel<Library>(api) {
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val pager = MutableLiveData<ApiRequestPager<GetItemsRequest>?>()

        fun init(
            itemId: UUID,
            potential: BaseItem?,
        ): Job =
            viewModelScope.launch(
                LoadingExceptionHandler(
                    loading,
                    "Error loading collection $itemId",
                ) + Dispatchers.IO,
            ) {
                fetchItem(itemId, potential)
                setup()
            }

        private suspend fun setup() =
            withContext(Dispatchers.IO) {
                if (!pager.isInitialized) {
                    item.value?.let { item ->
                        val includeItemTypes =
                            when (item.data.collectionType) {
                                CollectionType.UNKNOWN -> TODO()
                                CollectionType.MOVIES -> listOf(BaseItemKind.MOVIE)
                                CollectionType.TVSHOWS -> listOf(BaseItemKind.SERIES)
                                CollectionType.MUSIC -> TODO()
                                CollectionType.MUSICVIDEOS -> TODO()
                                CollectionType.TRAILERS -> TODO()
                                CollectionType.HOMEVIDEOS -> listOf(BaseItemKind.VIDEO)
                                CollectionType.BOXSETS -> TODO()
                                CollectionType.BOOKS -> TODO()
                                CollectionType.PHOTOS -> TODO()
                                CollectionType.LIVETV -> TODO()
                                CollectionType.PLAYLISTS -> TODO()
                                CollectionType.FOLDERS -> TODO()
                                null -> TODO()
                            }
                        val request =
                            GetItemsRequest(
                                parentId = item.id,
                                isSeries = true,
                                mediaTypes = null,
//                            recursive = true,
                                enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                                includeItemTypes = includeItemTypes,
                                sortBy = listOf(ItemSortBy.SORT_NAME),
                                sortOrder = listOf(SortOrder.ASCENDING),
                                fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                            )
                        val newPager =
                            ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope)
                        newPager.init()
                        newPager.getBlocking(0)
                        withContext(Dispatchers.Main) {
                            pager.value = newPager
                            loading.value = LoadingState.Success
                        }
                    }
                }
            }
    }

@Composable
fun CollectionFolderDetails(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: CollectionFolderViewModel = hiltViewModel(),
) {
    OneTimeLaunchedEffect {
        viewModel.init(destination.itemId, destination.item)
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    val item by viewModel.item.observeAsState()
    val library by viewModel.model.observeAsState()
    val pager by viewModel.pager.observeAsState()

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)
        LoadingState.Loading -> LoadingPage()
        LoadingState.Success -> {
            pager?.let { pager ->
                when (library!!.collectionType) {
                    CollectionType.UNKNOWN -> TODO()

                    // TODO?
                    CollectionType.MOVIES ->
                        TVShowCollectionDetails(
                            preferences,
                            navigationManager,
                            library!!,
                            item!!,
                            pager,
                            modifier,
                        )

                    CollectionType.TVSHOWS -> {
                        TVShowCollectionDetails(
                            preferences,
                            navigationManager,
                            library!!,
                            item!!,
                            pager,
                            modifier,
                        )
                    }

                    // TODO?
                    CollectionType.HOMEVIDEOS ->
                        TVShowCollectionDetails(
                            preferences,
                            navigationManager,
                            library!!,
                            item!!,
                            pager,
                            modifier,
                        )

                    CollectionType.MUSIC -> TODO()
                    CollectionType.MUSICVIDEOS -> TODO()
                    CollectionType.TRAILERS -> TODO()
                    CollectionType.BOXSETS -> TODO()
                    CollectionType.BOOKS -> TODO()
                    CollectionType.PHOTOS -> TODO()
                    CollectionType.LIVETV -> TODO()
                    CollectionType.PLAYLISTS -> TODO()
                    CollectionType.FOLDERS -> TODO()
                }
            }
        }
    }
}

@Composable
fun TVShowCollectionDetails(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    library: Library,
    item: BaseItem,
    pager: List<BaseItem?>,
    modifier: Modifier = Modifier,
) {
    val title = library.name ?: item.data.name ?: item.data.collectionType?.name ?: "Collection"

    val gridFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
    Column(
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = modifier,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        CardGrid(
            pager = pager,
            itemOnClick = {
                navigationManager.navigateTo(
                    Destination.MediaItem(
                        it.id,
                        it.type,
                        it,
                    ),
                )
            },
            longClicker = {},
            letterPosition = { 0 },
            requestFocus = true,
            gridFocusRequester = gridFocusRequester,
            navigationManager = navigationManager,
            modifier = Modifier.fillMaxSize(),
            initialPosition = 0,
            positionCallback = { _, _ -> },
        )
    }
}
