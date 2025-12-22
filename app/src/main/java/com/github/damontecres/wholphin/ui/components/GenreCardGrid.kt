package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.GenreCard
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.GetGenresRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@HiltViewModel
class GenreViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val imageUrlService: ImageUrlService,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private lateinit var itemId: UUID

        val item = MutableLiveData<BaseItem?>(null)
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val genres = MutableLiveData<List<Genre>>(listOf())

        fun init(itemId: UUID) {
            loading.value = LoadingState.Loading
            this.itemId = itemId
            viewModelScope.launch(Dispatchers.IO + LoadingExceptionHandler(loading, "Failed to fetch genres")) {
                val item =
                    api.userLibraryApi.getItem(itemId = itemId).content.let {
                        BaseItem(it, false)
                    }
                this@GenreViewModel.item.setValueOnMain(item)
                val request =
                    GetGenresRequest(
                        userId = serverRepository.currentUser.value?.id,
                        parentId = itemId,
                        fields = SlimItemFields,
                    )
                val genres =
                    GetGenresRequestHandler
                        .execute(api, request)
                        .content.items
                        .map {
                            Genre(it.id, it.name ?: "", null, Color.Black)
                        }
//                val pager = ApiRequestPager(api, request, GetGenresRequestHandler, viewModelScope).init()
                withContext(Dispatchers.Main) {
                    this@GenreViewModel.genres.value = genres
                    loading.value = LoadingState.Success
                }
//                val excludeItemIds = mutableSetOf<UUID>()
                val genreToUrl = ConcurrentHashMap<UUID, String>()
                val semaphore = Semaphore(4)
                genres
                    .map { genre ->
                        viewModelScope.async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val item =
                                    GetItemsRequestHandler
                                        .execute(
                                            api,
                                            GetItemsRequest(
//                                                excludeItemIds = excludeItemIds,
                                                parentId = itemId,
                                                recursive = true,
                                                limit = 1,
                                                sortBy = listOf(ItemSortBy.RANDOM),
                                                fields = listOf(ItemFields.GENRES),
                                                imageTypes = listOf(ImageType.THUMB),
                                                imageTypeLimit = 1,
                                                includeItemTypes =
                                                    listOf(
                                                        BaseItemKind.MOVIE,
                                                        BaseItemKind.SERIES,
                                                    ),
                                                genreIds = listOf(genre.id),
                                                enableTotalRecordCount = false,
                                            ),
                                        ).content.items
                                        .firstOrNull()
                                if (item != null) {
//                                    excludeItemIds.add(item.id)
                                    genreToUrl[genre.id] =
                                        imageUrlService.getItemImageUrl(
                                            item.id,
                                            item.type,
                                            null,
                                            false,
                                            ImageType.THUMB,
                                        )
                                }
                            }
                        }
                    }.awaitAll()
                val genresWithImages =
                    genres.map {
                        it.copy(
                            imageUrl = genreToUrl[it.id],
                        )
                    }
                this@GenreViewModel.genres.setValueOnMain(genresWithImages)
            }
        }

        suspend fun positionOfLetter(letter: Char): Int =
            withContext(Dispatchers.IO) {
                val request =
                    GetGenresRequest(
                        parentId = itemId,
                        nameLessThan = letter.toString(),
                        limit = 0,
                        enableTotalRecordCount = true,
                    )
                val result by GetGenresRequestHandler.execute(api, request)
                return@withContext result.totalRecordCount
            }
    }

data class Genre(
    override val id: UUID,
    val name: String,
    val imageUrl: String?,
    val color: Color,
) : CardGridItem {
    override val playable: Boolean = false
    override val sortName: String get() = name
}

@Composable
fun GenreCardGrid(
    itemId: UUID,
    modifier: Modifier = Modifier,
    viewModel: GenreViewModel = hiltViewModel(),
) {
    OneTimeLaunchedEffect {
        viewModel.init(itemId)
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)
    val genres by viewModel.genres.observeAsState(listOf())

    val gridFocusRequester = remember { FocusRequester() }
    when (val st = loading) {
        LoadingState.Pending,
        LoadingState.Loading,
        -> {
            LoadingPage(modifier.focusable())
        }

        is LoadingState.Error -> {
            ErrorMessage(st, modifier.focusable())
        }

        LoadingState.Success -> {
            Box(modifier = modifier) {
                LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
                val item by viewModel.item.observeAsState(null)
                CardGrid(
                    pager = genres,
                    onClickItem = { _, genre ->
                        viewModel.navigationManager.navigateTo(
                            Destination.FilteredCollection(
                                itemId = itemId,
                                filter =
                                    CollectionFolderFilter(
                                        nameOverride =
                                            listOfNotNull(
                                                genre.name,
                                                item?.title,
                                            ).joinToString(" "),
                                        filter = GetItemsFilter(genres = listOf(genre.id)),
                                        useSavedLibraryDisplayInfo = false,
                                    ),
                                recursive = true,
                            ),
                        )
                    },
                    onLongClickItem = { _, _ -> },
                    onClickPlay = { _, _ -> },
                    letterPosition = { viewModel.positionOfLetter(it) },
                    gridFocusRequester = gridFocusRequester,
                    showJumpButtons = false,
                    showLetterButtons = true,
                    modifier = Modifier.fillMaxSize(),
                    initialPosition = 0,
                    positionCallback = { columns, position ->
                    },
                    columns = 4,
                    cardContent = { item: Genre?, onClick: () -> Unit, onLongClick: () -> Unit, mod: Modifier ->
                        GenreCard(
                            genre = item,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            modifier = mod,
                        )
                    },
                )
            }
        }
    }
}
