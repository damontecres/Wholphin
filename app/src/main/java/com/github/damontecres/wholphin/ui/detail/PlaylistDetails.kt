package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.cards.ItemCardImage
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetPlaylistItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.request.GetPlaylistItemsRequest
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration

@HiltViewModel
class PlaylistViewModel
    @Inject
    constructor(
        api: ApiClient,
        val navigationManager: NavigationManager,
    ) : ItemViewModel(api) {
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val items = MutableLiveData<List<BaseItem?>>(listOf())

        fun init(playlistId: UUID) {
            loading.value = LoadingState.Loading
            viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(loading, "Failed to fetch playlist $playlistId"),
            ) {
                val playlist = fetchItem(playlistId)
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
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.init(destination.itemId)
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)
    val playlist by viewModel.item.observeAsState(null)
    val items by viewModel.items.observeAsState(listOf())

    var longClickDialog by remember { mutableStateOf<DialogParams?>(null) }

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
                    focusRequester = focusRequester,
                    onClickIndex = { index, _ ->
                        viewModel.navigationManager.navigateTo(
                            Destination.PlaybackList(
                                itemId = it.id,
                                startIndex = index,
                                shuffle = false,
                            ),
                        )
                    },
                    onClickPlay = { shuffle ->
                        viewModel.navigationManager.navigateTo(
                            Destination.PlaybackList(
                                itemId = it.id,
                                startIndex = 0,
                                shuffle = shuffle,
                            ),
                        )
                    },
                    onLongClickIndex = { index, item ->
                        longClickDialog =
                            DialogParams(
                                fromLongClick = true,
                                title = item.name ?: "",
                                items =
                                    listOf(
                                        DialogItem(
                                            context.getString(R.string.go_to),
                                            Icons.Default.ArrowForward,
                                        ) {
                                            viewModel.navigationManager.navigateTo(item.destination())
                                        },
                                        DialogItem(
                                            context.getString(R.string.play_from_here),
                                            Icons.Default.PlayArrow,
                                        ) {
                                            viewModel.navigationManager.navigateTo(
                                                Destination.PlaybackList(
                                                    itemId = it.id,
                                                    startIndex = index,
                                                    shuffle = false,
                                                ),
                                            )
                                        },
                                    ),
                            )
                    },
                    modifier = modifier,
                )
            }
    }
    longClickDialog?.let { params ->
        DialogPopup(
            params = params,
            onDismissRequest = { longClickDialog = null },
        )
    }
}

@Composable
fun PlaylistDetailsContent(
    playlist: BaseItem,
    items: List<BaseItem?>,
    onClickIndex: (Int, BaseItem) -> Unit,
    onLongClickIndex: (Int, BaseItem) -> Unit,
    onClickPlay: (shuffle: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester = remember { FocusRequester() },
) {
    var savedIndex by rememberSaveable { mutableIntStateOf(0) }
    var focusedIndex by remember { mutableIntStateOf(savedIndex) }
    val focus = remember { FocusRequester() }
    val focusedItem = items.getOrNull(focusedIndex)

    val playButtonFocusRequester = remember { FocusRequester() }

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
                    .padding(top = 16.dp)
                    .fillMaxSize(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp)
                        .fillMaxWidth(),
            ) {
                PlaylistDetailsHeader(
                    focusedItem = focusedItem,
                    onClickPlay = onClickPlay,
                    playButtonFocusRequester = playButtonFocusRequester,
                    modifier =
                        Modifier
                            .padding(start = 16.dp)
                            .fillMaxWidth(.25f),
                )
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                ) {
                    Text(
                        text = playlist.name ?: stringResource(R.string.playlist),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.displayMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyColumn(
                        contentPadding = PaddingValues(8.dp),
                        modifier =
                            Modifier
                                .padding(bottom = 32.dp)
                                .fillMaxHeight()
//                            .fillMaxWidth(.8f)
                                .weight(1f)
                                .background(
                                    MaterialTheme.colorScheme
                                        .surfaceColorAtElevation(1.dp)
                                        .copy(alpha = .75f),
                                    shape = RoundedCornerShape(16.dp),
                                ).focusRequester(focusRequester)
                                .focusGroup()
                                .focusRestorer(focus),
                    ) {
                        itemsIndexed(items) { index, item ->
                            PlaylistItem(
                                item = item,
                                index = index,
                                onClick = {
                                    savedIndex = index
                                    item?.let {
                                        onClickIndex.invoke(index, item)
                                    }
                                },
                                onLongClick = {
                                    savedIndex = index
                                    item?.let {
                                        onLongClickIndex.invoke(index, item)
                                    }
                                },
                                modifier =
                                    Modifier
                                        .height(80.dp)
                                        .ifElse(
                                            index == savedIndex,
                                            Modifier.focusRequester(focus),
                                        ).onFocusChanged {
                                            if (it.isFocused) {
                                                focusedIndex = index
                                            }
                                        }.focusProperties {
                                            left = playButtonFocusRequester
                                            previous = playButtonFocusRequester
                                        },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistDetailsHeader(
    focusedItem: BaseItem?,
    onClickPlay: (shuffle: Boolean) -> Unit,
    playButtonFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.focusRequester(playButtonFocusRequester),
        ) {
            ExpandablePlayButton(
                title = R.string.play,
                resume = Duration.ZERO,
                icon = Icons.Default.PlayArrow,
                onClick = { onClickPlay.invoke(false) },
            )
            ExpandableFaButton(
                title = R.string.shuffle,
                iconStringRes = R.string.fa_shuffle,
                onClick = { onClickPlay.invoke(true) },
            )
        }
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
            maxLines = 10,
            onClick = {},
            enabled = false,
        )
    }
}

@Composable
fun PlaylistItem(
    item: BaseItem?,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        headlineContent = {
            Text(
                text = item?.title ?: "",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.enableMarquee(focused),
            )
        },
        supportingContent = {
            Text(
                text = item?.subtitle ?: "",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.enableMarquee(focused),
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
        modifier = modifier,
    )
}
