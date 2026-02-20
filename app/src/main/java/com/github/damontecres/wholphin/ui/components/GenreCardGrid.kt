package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
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

@HiltViewModel(assistedFactory = GenreViewModel.Factory::class)
class GenreViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val imageUrlService: ImageUrlService,
        private val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        @Assisted private val itemId: UUID,
        @Assisted private val includeItemTypes: List<BaseItemKind>?,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                itemId: UUID,
                includeItemTypes: List<BaseItemKind>?,
            ): GenreViewModel
        }

        val item = MutableLiveData<BaseItem?>(null)
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val genres = MutableLiveData<List<Genre>>(listOf())

        fun init(cardWidthPx: Int) {
            loading.value = LoadingState.Loading
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
                        includeItemTypes = includeItemTypes,
                    )
                val genres =
                    GetGenresRequestHandler
                        .execute(api, request)
                        .content.items
                        .map {
                            Genre(it.id, it.name ?: "", null)
                        }
                withContext(Dispatchers.Main) {
                    this@GenreViewModel.genres.value = genres
                    loading.value = LoadingState.Success
                }
                val genreToUrl =
                    getGenreImageMap(
                        api = api,
                        scope = viewModelScope,
                        imageUrlService = imageUrlService,
                        genres = genres.map { it.id },
                        parentId = itemId,
                        includeItemTypes = includeItemTypes,
                        cardWidthPx = cardWidthPx,
                    )
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

suspend fun getGenreImageMap(
    api: ApiClient,
    scope: CoroutineScope,
    imageUrlService: ImageUrlService,
    genres: List<UUID>,
    parentId: UUID,
    includeItemTypes: List<BaseItemKind>?,
    cardWidthPx: Int?,
): Map<UUID, String?> {
    val genreToUrl = ConcurrentHashMap<UUID, String?>()
    val semaphore = Semaphore(4)
    genres
        .map { genreId ->
            scope.async(Dispatchers.IO) {
                semaphore.withPermit {
                    val item =
                        GetItemsRequestHandler
                            .execute(
                                api,
                                GetItemsRequest(
                                    parentId = parentId,
                                    recursive = true,
                                    limit = 1,
                                    sortBy = listOf(ItemSortBy.RANDOM),
                                    fields = listOf(ItemFields.GENRES),
                                    imageTypes = listOf(ImageType.BACKDROP),
                                    imageTypeLimit = 1,
                                    includeItemTypes = includeItemTypes,
                                    genreIds = listOf(genreId),
                                    enableTotalRecordCount = false,
                                ),
                            ).content.items
                            .firstOrNull()
                    if (item != null) {
                        genreToUrl[genreId] =
                            imageUrlService.getItemImageUrl(
                                itemId = item.id,
                                itemType = item.type,
                                seriesId = null,
                                useSeriesForPrimary = true,
                                imageType = ImageType.BACKDROP,
                                imageTags = item.imageTags.orEmpty(),
                                fillWidth = cardWidthPx,
                            )
                    }
                }
            }
        }.awaitAll()
    return genreToUrl
}

@Stable
data class Genre(
    val id: UUID,
    val name: String,
    val imageUrl: String?,
) : CardGridItem {
    override val gridId: String get() = id.toString()
    override val playable: Boolean = false
    override val sortName: String get() = name
}

@Composable
fun GenreCardGrid(
    itemId: UUID,
    includeItemTypes: List<BaseItemKind>?,
    modifier: Modifier = Modifier,
    viewModel: GenreViewModel =
        hiltViewModel<GenreViewModel, GenreViewModel.Factory>(
            creationCallback = { it.create(itemId, includeItemTypes) },
        ),
) {
    val columns = 4
    val spacing = 16.dp
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val cardWidthPx =
        remember {
            with(density) {
                // Grid has 16dp padding on either side & 16dp spacing between 4 cards
                // This isn't exact though because it doesn't account for nav drawer or letters, but it's close and the calculation is much faster
                // E.g. on 1080p, this results in 440px versus 395px actual, so only minimal scaling down is required
                (configuration.screenWidthDp.dp - (2 * 16.dp + 3 * spacing))
                    .div(columns)
                    .roundToPx()
            }
        }
    OneTimeLaunchedEffect {
        viewModel.init(cardWidthPx)
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
                                        filter =
                                            GetItemsFilter(
                                                genres = listOf(genre.id),
                                                includeItemTypes = includeItemTypes,
                                            ),
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
                    columns = columns,
                    spacing = spacing,
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
