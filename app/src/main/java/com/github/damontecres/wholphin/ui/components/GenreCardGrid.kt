package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.createGenreDestination
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.GenreCard
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.GetGenresRequestHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.mayakapps.kache.InMemoryKache
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetGenresRequest
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.hours

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

        private val _state = MutableStateFlow(GenreGridState())
        val state: StateFlow<GenreGridState> = _state

        fun init(cardWidthPx: Int) {
            _state.update { it.copy(item = DataLoadingState.Loading) }
            viewModelScope.launchIO {
                try {
                    val item =
                        api.userLibraryApi.getItem(itemId = itemId).content.let {
                            BaseItem(it, false)
                        }
                    val request =
                        GetGenresRequest(
                            userId = serverRepository.currentUser?.id,
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
                    _state.update {
                        it.copy(
                            item = DataLoadingState.Success(item),
                            genres = genres,
                        )
                    }
                    val genreToUrl =
                        getGenreImageMap(
                            api = api,
                            userId = serverRepository.currentUser?.id,
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
                    _state.update {
                        it.copy(
                            genres = genresWithImages,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching genres")
                    _state.update { it.copy(item = DataLoadingState.Error(ex)) }
                }
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
                        includeItemTypes = includeItemTypes,
                    )
                val result by GetGenresRequestHandler.execute(api, request)
                return@withContext result.totalRecordCount
            }
    }

data class GenreGridState(
    val item: DataLoadingState<BaseItem> = DataLoadingState.Pending,
    val genres: List<Genre> = emptyList(),
)

data class GenreCacheKey(
    val userId: UUID?,
    val parentId: UUID,
)

private val genreCache by lazy {
    InMemoryKache<GenreCacheKey, Map<UUID, String?>>(8) {
        expireAfterWriteDuration = 2.hours
    }
}

/**
 * Create a mapping from genre IDs to image URLs using random items within each genre
 */
suspend fun getGenreImageMap(
    api: ApiClient,
    userId: UUID?,
    scope: CoroutineScope,
    imageUrlService: ImageUrlService,
    genres: List<UUID>,
    parentId: UUID,
    includeItemTypes: List<BaseItemKind>?,
    cardWidthPx: Int?,
    useCache: Boolean = true,
): Map<UUID, String?> {
    val key = GenreCacheKey(userId, parentId)
    if (useCache) {
        genreCache.getIfAvailable(key)?.let {
            Timber.v("Got cached entry")
            return it
        }
    }
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
                                    userId = userId,
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
                                backdropTags = item.backdropImageTags.orEmpty(),
                            )
                    }
                }
            }
        }.awaitAll()
    genreCache.put(key, genreToUrl)
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

/**
 * Show an optimized grid of genres for a library
 */
@Composable
fun GenreCardGrid(
    itemId: UUID,
    includeItemTypes: List<BaseItemKind>?,
    collectionType: CollectionType,
    modifier: Modifier = Modifier,
    initialPosition: Int = 0,
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
    val state by viewModel.state.collectAsState()

    val gridFocusRequester = remember { FocusRequester() }
    when (val st = state.item) {
        DataLoadingState.Pending,
        DataLoadingState.Loading,
        -> {
            LoadingPage(modifier.focusable())
        }

        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier.focusable())
        }

        is DataLoadingState.Success<BaseItem> -> {
            Box(modifier = modifier) {
                LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
                val item = st.data
                CardGrid(
                    pager = state.genres,
                    onClickItem = { _, genre ->
                        viewModel.navigationManager.navigateTo(
                            createGenreDestination(
                                genreId = genre.id,
                                genreName = genre.name,
                                parentId = itemId,
                                parentName = item.title,
                                includeItemTypes = includeItemTypes,
                                collectionType = collectionType,
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
                    initialPosition = initialPosition,
                    positionCallback = { columns, position ->
                    },
                    columns = columns,
                    spacing = spacing,
                    cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
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
