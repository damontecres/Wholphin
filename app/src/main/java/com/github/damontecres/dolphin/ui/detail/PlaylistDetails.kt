package com.github.damontecres.dolphin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.data.model.Library
import com.github.damontecres.dolphin.ui.DefaultItemFields
import com.github.damontecres.dolphin.ui.cards.ItemCardImage
import com.github.damontecres.dolphin.ui.components.ErrorMessage
import com.github.damontecres.dolphin.ui.components.LoadingPage
import com.github.damontecres.dolphin.ui.components.OverviewText
import com.github.damontecres.dolphin.ui.ifElse
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.roundMinutes
import com.github.damontecres.dolphin.ui.tryRequestFocus
import com.github.damontecres.dolphin.util.ApiRequestPager
import com.github.damontecres.dolphin.util.GetPlaylistItemsRequestHandler
import com.github.damontecres.dolphin.util.LoadingExceptionHandler
import com.github.damontecres.dolphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.request.GetPlaylistItemsRequest
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel
    @Inject
    constructor(
        api: ApiClient,
        val navigationManager: NavigationManager,
    ) : ItemViewModel<Library>(api) {
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val items = MutableLiveData<List<BaseItem?>>(listOf())

        fun init(playlistId: UUID) {
            loading.value = LoadingState.Loading
            viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(loading, "Failed to fetch playlist $playlistId"),
            ) {
                val playlist = fetchItem(playlistId, null)
                val request =
                    GetPlaylistItemsRequest(
                        playlistId = playlist.id,
                        fields = DefaultItemFields,
                    )
                val pager = ApiRequestPager(api, request, GetPlaylistItemsRequestHandler, viewModelScope).init()
                withContext(Dispatchers.Main) {
                    items.value = pager
                    loading.value = LoadingState.Success
                }
            }
        }
    }

@Composable
fun PlaylistDetails(
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.init(destination.itemId)
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)
    val playlist by viewModel.item.observeAsState(null)
    val items by viewModel.items.observeAsState(listOf())

    when (val st = loading) {
        is LoadingState.Error -> ErrorMessage(st, modifier)
        LoadingState.Pending, LoadingState.Loading -> LoadingPage(modifier)
        LoadingState.Success ->
            playlist?.let {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                PlaylistDetailsContent(
                    playlist = it,
                    items = items,
                    onClickIndex = { index ->
                        viewModel.navigationManager.navigateTo(
                            Destination.Playback(
                                itemId = it.id,
                                positionMs = 0L,
                                startIndex = index,
                            ),
                        )
                    },
                    modifier = modifier.focusRequester(focusRequester),
                )
            }
    }
}

@Composable
fun PlaylistDetailsContent(
    playlist: BaseItem,
    items: List<BaseItem?>,
    onClickIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedIndex by remember { mutableIntStateOf(savedIndex) }
    val focusRequester = remember { FocusRequester() }

    val focusedItem = items.getOrNull(focusedIndex)

    Box(
        modifier = modifier,
    ) {
        if (focusedItem?.backdropImageUrl.isNotNullOrBlank()) {
            val gradientColor = MaterialTheme.colorScheme.background
            AsyncImage(
                model = focusedItem.backdropImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier =
                    Modifier
                        .fillMaxHeight(.85f)
                        .alpha(.4f)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, gradientColor),
                                    startY = 500f,
                                ),
                            )
                            drawRect(
                                Brush.horizontalGradient(
                                    colors = listOf(gradientColor, Color.Transparent),
                                    endX = 400f,
                                    startX = 100f,
                                ),
                            )
                        },
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .padding(start = 16.dp, top = 16.dp)
                    .fillMaxSize(),
        ) {
            Text(
                text = playlist.name ?: "Playlist",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.displayMedium,
            )
            PlaylistDetailsHeader(
                focusedItem = focusedItem,
                modifier =
                    Modifier
                        .padding(start = 16.dp)
                        .fillMaxWidth(.66f),
            )
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth(.8f)
                        .align(Alignment.CenterHorizontally)
                        .background(
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                            shape = RoundedCornerShape(16.dp),
                        ).focusGroup()
                        .focusRestorer(focusRequester),
            ) {
                itemsIndexed(items) { index, item ->
                    val interactionSource = remember { MutableInteractionSource() }
                    ListItem(
                        selected = false,
                        onClick = {
                            savedIndex = index
                            onClickIndex.invoke(index)
                        },
                        interactionSource = interactionSource,
                        headlineContent = {
                            Text(
                                text = item?.title ?: "",
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        supportingContent = {
                            Text(
                                text = item?.subtitle ?: "",
                                style = MaterialTheme.typography.titleSmall,
                            )
                        },
                        trailingContent = {
                            item?.data?.runTimeTicks?.ticks?.roundMinutes?.let {
                                Text(
                                    text = it.toString(),
                                )
                            }
                        },
                        leadingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                ItemCardImage(
                                    imageUrl = item?.imageUrl,
                                    name = item?.name,
                                    showOverlay = true,
                                    favorite = item?.data?.userData?.isFavorite ?: false,
                                    watched = item?.data?.userData?.played ?: false,
                                    unwatchedCount = item?.data?.userData?.unplayedItemCount ?: -1,
                                    watchedPercent = 0.0,
                                    modifier = Modifier.width(160.dp),
                                    useFallbackText = false,
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .height(80.dp)
                                .ifElse(
                                    index == savedIndex,
                                    Modifier.focusRequester(focusRequester),
                                ).onFocusChanged {
                                    if (it.isFocused) {
                                        focusedIndex = index
                                    }
                                },
                    )
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailsHeader(
    focusedItem: BaseItem?,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = focusedItem?.title ?: "",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineLarge,
        )
        Text(
            text = focusedItem?.subtitle ?: "",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.headlineSmall,
        )
        OverviewText(
            overview = focusedItem?.data?.overview ?: "",
            maxLines = 2,
            onClick = {},
            enabled = false,
        )
    }
}
