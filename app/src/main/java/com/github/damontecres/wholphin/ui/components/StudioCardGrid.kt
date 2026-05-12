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
import com.github.damontecres.wholphin.data.model.createStudioDestination
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.StudioCard
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.GetStudiosRequestHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.request.GetStudiosRequest
import java.util.UUID

@HiltViewModel(assistedFactory = StudioViewModel.Factory::class)
class StudioViewModel
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
            ): StudioViewModel
        }

        val item = MutableLiveData<BaseItem?>(null)
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val studios = MutableLiveData<List<Studio>>(listOf())

        fun init(cardWidthPx: Int) {
            loading.value = LoadingState.Loading
            viewModelScope.launch(Dispatchers.IO + LoadingExceptionHandler(loading, "Failed to fetch genres")) {
                val item =
                    api.userLibraryApi.getItem(itemId = itemId).content.let {
                        BaseItem(it, false)
                    }
                this@StudioViewModel.item.setValueOnMain(item)
                val request =
                    GetStudiosRequest(
                        userId = serverRepository.currentUser.value?.id,
                        parentId = itemId,
                        fields = SlimItemFields,
                        includeItemTypes = includeItemTypes,
                    )
                val studios =
                    GetStudiosRequestHandler
                        .execute(api, request)
                        .content.items
                        .map {
                            val imageUrl =
                                imageUrlService.getItemImageUrl(
                                    itemId = it.id,
                                    imageType = ImageType.THUMB,
                                    fillWidth = cardWidthPx,
                                )
                            Studio(it.id, it.name ?: "", imageUrl)
                        }
                withContext(Dispatchers.Main) {
                    this@StudioViewModel.studios.value = studios
                    loading.value = LoadingState.Success
                }
            }
        }

        suspend fun positionOfLetter(letter: Char): Int =
            withContext(Dispatchers.IO) {
                val request =
                    GetStudiosRequest(
                        userId = serverRepository.currentUser.value?.id,
                        parentId = itemId,
                        nameLessThan = letter.toString(),
                        limit = 0,
                        enableTotalRecordCount = true,
                        includeItemTypes = includeItemTypes,
                    )
                val result by GetStudiosRequestHandler.execute(api, request)
                return@withContext result.totalRecordCount
            }
    }

@Stable
data class Studio(
    val id: UUID,
    val name: String,
    val imageUrl: String?,
) : CardGridItem {
    override val gridId: String get() = id.toString()
    override val playable: Boolean = false
    override val sortName: String get() = name
}

@Composable
fun StudioCardGrid(
    itemId: UUID,
    includeItemTypes: List<BaseItemKind>?,
    modifier: Modifier = Modifier,
    viewModel: StudioViewModel =
        hiltViewModel<StudioViewModel, StudioViewModel.Factory>(
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
    val studios by viewModel.studios.observeAsState(listOf())

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
                    pager = studios,
                    onClickItem = { _, studio ->
                        viewModel.navigationManager.navigateTo(
                            createStudioDestination(
                                studioId = studio.id,
                                name = studio.name,
                                parentId = itemId,
                                parentName = item?.title,
                                includeItemTypes = includeItemTypes,
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
                    cardContent = { (item, index, onClick, onLongClick, widthPx, mod) ->
                        StudioCard(
                            studio = item,
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
