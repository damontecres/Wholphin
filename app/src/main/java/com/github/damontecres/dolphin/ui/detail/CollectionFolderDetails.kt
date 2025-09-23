package com.github.damontecres.dolphin.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.OneTimeLaunchedEffect
import com.github.damontecres.dolphin.data.model.Library
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.util.DolphinPager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
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
        val pager = MutableLiveData<DolphinPager?>()

        override fun init(
            itemId: UUID,
            potential: BaseItemDto?,
        ): Job =
            viewModelScope.launch {
                super.init(itemId, potential)?.join()
                setup()
            }

        private suspend fun setup() {
            if (!pager.isInitialized) {
                item.value?.let { item ->
                    val request =
                        GetItemsRequest(
                            parentId = item.id,
                            isSeries = true,
                            mediaTypes = null,
//                            recursive = true,
                            enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                            includeItemTypes = listOf(BaseItemKind.SERIES),
                            sortBy = listOf(ItemSortBy.SORT_NAME),
                            sortOrder = listOf(SortOrder.ASCENDING),
                            fields = listOf(ItemFields.PRIMARY_IMAGE_ASPECT_RATIO),
                        )
                    val newPager = DolphinPager(api, request, viewModelScope)
                    newPager.init()
                    pager.value = newPager
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
    val item by viewModel.item.observeAsState()
    val library by viewModel.model.observeAsState()
    val pager by viewModel.pager.observeAsState()
    if (library == null) {
        Text("Loading library...")
    } else {
        pager?.let { pager ->
            when (library!!.collectionType) {
                CollectionType.UNKNOWN -> TODO()
                CollectionType.MOVIES -> TODO()
                CollectionType.TVSHOWS -> {
                    TVShowCollectionDetails(viewModel.api, preferences, navigationManager, library!!, item!!, pager, modifier)
                }

                CollectionType.MUSIC -> TODO()
                CollectionType.MUSICVIDEOS -> TODO()
                CollectionType.TRAILERS -> TODO()
                CollectionType.HOMEVIDEOS -> TODO()
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

@Composable
fun TVShowCollectionDetails(
    api: ApiClient,
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    library: Library,
    item: BaseItemDto,
    pager: DolphinPager,
    modifier: Modifier = Modifier,
) {
    val gridFocusRequester = remember { FocusRequester() }
    CardGrid(
        api = api,
        pager = pager,
        itemOnClick = { navigationManager.navigateTo(Destination.MediaItem(it.id, it.type, it)) },
        longClicker = {},
        letterPosition = { 0 },
        requestFocus = true,
        gridFocusRequester = gridFocusRequester,
        navigationManager = navigationManager,
        modifier = modifier,
        initialPosition = 0,
        positionCallback = { _, _ -> },
    )
}
