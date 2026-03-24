package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.BannerCardWithTitle
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.ConfirmDeleteDialog
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GenreText
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.LoadingState
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import java.util.UUID

@HiltViewModel(assistedFactory = AlbumViewModel.Factory::class)
class AlbumViewModel
    @AssistedInject
    constructor(
        @ApplicationContext context: Context,
        api: ApiClient,
        musicService: MusicService,
        navigationManager: NavigationManager,
        mediaManagementService: MediaManagementService,
        val serverRepository: ServerRepository,
        val mediaReportService: MediaReportService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        private val imageUrlService: ImageUrlService,
        @Assisted itemId: UUID,
    ) : MusicViewModel(itemId, context, api, musicService, navigationManager, mediaManagementService) {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): AlbumViewModel
        }

        private val _state = MutableStateFlow(AlbumState.EMPTY)
        val state: StateFlow<AlbumState> = _state

        val currentMusic = musicService.state

        init {
            init()
        }

        override fun init() {
            viewModelScope.launchIO {
                try {
                    val itemDeferred =
                        async {
                            api.userLibraryApi
                                .getItem(itemId = itemId)
                                .content
                                .let { BaseItem(it, false) }
                        }
                    val songsDeferred = async { getPagerForAlbum(api, itemId) }
                    val album = itemDeferred.await()
                    val songs = songsDeferred.await()
                    val imageUrl = imageUrlService.getItemImageUrl(album, ImageType.PRIMARY)
                    val allArtists =
                        album.data.artists.orEmpty() +
                            album.data.albumArtists
                                ?.map { it.name }
                                .orEmpty()
                    val isVariousArtists =
                        allArtists.firstOrNull { it?.lowercase() == "various artists" } != null ||
                            album.data.artists
                                .orEmpty()
                                .size > 1
                    _state.update {
                        it.copy(
                            album = album,
                            isVariousArtists = isVariousArtists,
                            imageUrl = imageUrl,
                            songs = songs,
                            loading = LoadingState.Success,
                        )
                    }
                    updateBackDrop()
                    if (state.value.similar.isEmpty()) {
                        viewModelScope.launchIO {
                            val similar =
                                api.libraryApi
                                    .getSimilarItems(
                                        GetSimilarItemsRequest(
                                            userId = serverRepository.currentUser.value?.id,
                                            itemId = itemId,
                                            excludeArtistIds = album.data.albumArtists?.map { it.id },
                                            fields = SlimItemFields,
                                            limit = 25,
                                        ),
                                    ).content.items
                                    .map { BaseItem.from(it, api) }
                            _state.update { it.copy(similar = similar) }
                        }
                    }
                    viewModelScope.launchIO {
                        val request =
                            GetItemsRequest(
                                userId = serverRepository.currentUser.value?.id,
                                albumIds = listOf(itemId),
                                parentId = null,
                                fields = DefaultItemFields,
                                recursive = true,
                                includeItemTypes = listOf(BaseItemKind.MUSIC_VIDEO),
                            )
                        val musicVideos =
                            GetItemsRequestHandler.execute(api, request).toBaseItems(api, false)
                        if (musicVideos.isNotEmpty()) {
                            _state.update {
                                it.copy(musicVideos = musicVideos)
                            }
                        }
                    }
                } catch (ex: Exception) {
                    _state.update { it.copy(loading = LoadingState.Error(ex)) }
                }
            }
        }

        fun updateBackDrop() {
            state.value.album?.let { album ->
                viewModelScope.launchDefault {
                    getBackdropItemForAlbum(api, album)?.let {
                        backdropService.submit(it)
                    }
                }
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            val album =
                api.userLibraryApi
                    .getItem(itemId = itemId)
                    .content
                    .let { BaseItem(it, false) }
            _state.update { it.copy(album = album) }
        }

        fun play(
            shuffled: Boolean,
            startIndex: Int = 0,
        ) {
            val songs = state.value.songs as ApiRequestPager<*>
            play(songs, startIndex, shuffled)
        }
    }

suspend fun getBackdropItemForAlbum(
    api: ApiClient,
    album: BaseItem,
): BaseItem? {
    if (album.data.backdropImageTags?.isNotEmpty() == true) {
        return album
    } else {
        val artistIds =
            album.data.albumArtists
                ?.shuffled()
                ?.take(50)
                ?.map { it.id }
        return api.itemsApi
            .getItems(
                ids = artistIds,
                imageTypes = listOf(ImageType.BACKDROP),
            ).content.items
            .firstOrNull()
            ?.let { BaseItem(it, false) }
    }
}

data class AlbumState(
    val album: BaseItem?,
    val isVariousArtists: Boolean,
    val imageUrl: String?,
    val songs: List<BaseItem?>,
    val similar: List<BaseItem>,
    val loading: LoadingState,
    val musicVideos: List<BaseItem?> = emptyList(),
) {
    companion object {
        val EMPTY = AlbumState(null, false, null, emptyList(), emptyList(), LoadingState.Pending)
    }
}

private const val HEADER_ROW = 0
private const val SONG_ROW = HEADER_ROW + 1
private const val MUSIC_VIDEO_ROW = SONG_ROW + 1
private const val SIMILAR_ROW = MUSIC_VIDEO_ROW + 1

@Composable
fun AlbumDetailsPage(
    itemId: UUID,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: AlbumViewModel =
        hiltViewModel<AlbumViewModel, AlbumViewModel.Factory>(
            creationCallback = { it.create(itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val currentMusic by viewModel.currentMusic.collectAsState()

    var position by rememberPosition(0, 0)
    val focusRequesters =
        remember { List(SIMILAR_ROW + 1) { FocusRequester() } }
    val focusManager = LocalFocusManager.current
    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }
    var showDeleteDialog by remember { mutableStateOf<BaseItem?>(null) }
    val moreDialogActions =
        remember {
            MusicMoreDialogActions(
                onNavigate = { viewModel.navigationManager.navigateTo(it) },
                onClickPlay = { index, _ -> viewModel.play(false, index) },
                onClickPlayNext = { _, song -> viewModel.playNext(song) },
                onClickAddToQueue = { index, item -> viewModel.addToQueue(item, index) },
                onClickFavorite = { itemId, favorite -> viewModel.setFavorite(itemId, favorite) },
                onClickAddPlaylist = { itemId ->
                    playlistViewModel.loadPlaylists(MediaType.AUDIO)
                    showPlaylistDialog.makePresent(itemId)
                },
                onClickRemoveFromQueue = {},
                onClickDelete = { showDeleteDialog = it },
            )
        }
    when (val loading = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(loading, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            val album = state.album!!

            val firstFocusRequester = remember { FocusRequester() }
            val firstBringIntoViewRequester = remember { BringIntoViewRequester() }
            val bringIntoViewRequester = remember { BringIntoViewRequester() }
            val listState = rememberLazyListState()
            val itemsBefore = 2

            val songFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                if (position.row == SONG_ROW) {
                    songFocusRequester.tryRequestFocus()
                } else {
                    focusRequesters.getOrNull(position.row)?.tryRequestFocus()
                }
                viewModel.updateBackDrop()
            }
            val backHandlerActive by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > itemsBefore
                }
            }
            BackHandler(backHandlerActive) {
                scope.launch {
                    listState.animateScrollToItem(itemsBefore)
                    firstBringIntoViewRequester.bringIntoView()
                    firstFocusRequester.tryRequestFocus()
                }
            }
            Box(modifier = modifier) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(bringIntoViewRequester)
                                    .padding(bottom = 32.dp),
                        ) {
                            AlbumHeader(
                                album = album,
                                imageUrl = state.imageUrl,
                                overviewOnClick = {},
                                bringIntoViewRequester = bringIntoViewRequester,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MusicExpandableButtons(
                                actions =
                                    remember {
                                        MusicButtonActions(
                                            onClickPlay = { viewModel.play(it, 0) },
                                            onClickInstantMix = { viewModel.startInstantMix(album.id) },
                                            onClickFavorite = {
                                                viewModel.setFavorite(
                                                    album.id,
                                                    !album.favorite,
                                                )
                                            },
                                            onClickMore = {
                                                moreDialog =
                                                    DialogParams(
                                                        fromLongClick = false,
                                                        title = album.name + " (${album.data.productionYear ?: ""})",
                                                        items =
                                                            buildMoreDialogForMusic(
                                                                context = context,
                                                                actions = moreDialogActions,
                                                                item = album,
                                                                index = 0,
                                                                canRemove = false,
                                                                canDelete =
                                                                    viewModel.canDelete(
                                                                        album,
                                                                        preferences.appPreferences,
                                                                    ),
                                                            ),
                                                    )
                                            },
                                        )
                                    },
                                favorite = album.favorite,
                                buttonOnFocusChanged = {
                                    if (it.isFocused) {
                                        position = RowColumn(HEADER_ROW, 0)
                                        scope.launch { bringIntoViewRequester.bringIntoView() }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .onFocusChanged {
                                            if (it.hasFocus) scope.launch { bringIntoViewRequester.bringIntoView() }
                                        }.focusRequester(focusRequesters[HEADER_ROW]),
                            )
                        }
                    }
                    item {
                        Text(
                            text = stringResource(R.string.songs) + " (${state.songs.size})",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    itemsIndexed(state.songs) { index, song ->
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            SongListItem(
                                song = song,
                                onClick = {
                                    position = RowColumn(SONG_ROW, index)
                                    viewModel.play(false, index)
                                },
                                onLongClick = {
                                    if (song != null) {
                                        moreDialog =
                                            DialogParams(
                                                fromLongClick = true,
                                                title = song.name ?: "",
                                                items =
                                                    buildMoreDialogForMusic(
                                                        context = context,
                                                        actions = moreDialogActions,
                                                        item = song,
                                                        index = index,
                                                        canRemove = false,
                                                        canDelete =
                                                            viewModel.canDelete(
                                                                song,
                                                                preferences.appPreferences,
                                                            ),
                                                    ),
                                            )
                                    }
                                },
                                onClickMore = {
                                    if (song != null) {
                                        moreDialog =
                                            DialogParams(
                                                fromLongClick = false,
                                                title = song.name ?: "",
                                                items =
                                                    buildMoreDialogForMusic(
                                                        context = context,
                                                        actions = moreDialogActions,
                                                        item = song,
                                                        index = index,
                                                        canRemove = false,
                                                        canDelete =
                                                            viewModel.canDelete(
                                                                song,
                                                                preferences.appPreferences,
                                                            ),
                                                    ),
                                            )
                                    }
                                },
                                showArtist = state.isVariousArtists,
                                isPlaying = song != null && currentMusic.currentItemId == song.id,
                                isQueued = song != null && song.id in currentMusic.queuedIds,
                                modifier =
                                    Modifier
                                        .fillMaxWidth(.75f)
                                        .ifElse(
                                            index == 0,
                                            Modifier
                                                .focusRequester(firstFocusRequester)
                                                .bringIntoViewRequester(firstBringIntoViewRequester),
                                        ).ifElse(
                                            position.row == SONG_ROW && position.column == index,
                                            Modifier.focusRequester(songFocusRequester),
                                        ),
                            )
                        }
                    }
                    if (state.musicVideos.isNotEmpty()) {
                        item {
                            ItemRow(
                                title = stringResource(R.string.music_videos),
                                items = state.musicVideos,
                                onClickItem = { index, item ->
                                    position = RowColumn(MUSIC_VIDEO_ROW, index)
                                    viewModel.navigationManager.navigateTo(item.destination())
                                },
                                onLongClickItem = { index, item ->
                                    // TODO
                                },
                                cardContent = { index: Int, item: BaseItem?, mod: Modifier, onClick: () -> Unit, onLongClick: () -> Unit ->
                                    BannerCardWithTitle(
                                        title = item?.name,
                                        subtitle = item?.data?.productionYear?.toString(),
                                        item = item,
                                        onClick = onClick,
                                        onLongClick = onLongClick,
                                        aspectRatio = AspectRatios.WIDE,
                                        modifier = mod,
                                    )
                                },
                                modifier = Modifier.focusRequester(focusRequesters[MUSIC_VIDEO_ROW]),
                            )
                        }
                    }
                    if (state.similar.isNotEmpty()) {
                        item {
                            ItemRow(
                                title = stringResource(R.string.more_like_this),
                                items = state.similar,
                                onClickItem = { index, item ->
                                    position = RowColumn(SIMILAR_ROW, index)
                                    viewModel.navigationManager.navigateTo(item.destination())
                                },
                                onLongClickItem = { index, item ->
                                    moreDialog =
                                        DialogParams(
                                            fromLongClick = true,
                                            title = item.name ?: "",
                                            items =
                                                buildMoreDialogForMusic(
                                                    context = context,
                                                    actions = moreDialogActions,
                                                    item = item,
                                                    index = index,
                                                    canRemove = false,
                                                    canDelete =
                                                        viewModel.canDelete(
                                                            item,
                                                            preferences.appPreferences,
                                                        ),
                                                ),
                                        )
                                },
                                cardContent = { index: Int, item: BaseItem?, mod: Modifier, onClick: () -> Unit, onLongClick: () -> Unit ->
                                    BannerCardWithTitle(
                                        title = item?.name,
                                        subtitle = item?.data?.productionYear?.toString(),
                                        item = item,
                                        onClick = onClick,
                                        onLongClick = onLongClick,
                                        aspectRatio = AspectRatios.SQUARE,
                                        modifier = mod,
                                    )
                                },
                                modifier = Modifier.focusRequester(focusRequesters[SIMILAR_ROW]),
                            )
                        }
                    }
                }
            }
        }
    }
    moreDialog?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { moreDialog = null },
            dismissOnClick = true,
            waitToLoad = params.fromLongClick,
        )
    }
    showPlaylistDialog.compose { itemId ->
        PlaylistDialog(
            title = stringResource(R.string.add_to_playlist),
            state = playlistState,
            onDismissRequest = { showPlaylistDialog.makeAbsent() },
            onClick = {
                playlistViewModel.addToPlaylist(it.id, itemId)
                showPlaylistDialog.makeAbsent()
            },
            createEnabled = true,
            onCreatePlaylist = {
                playlistViewModel.createPlaylistAndAddItem(it, itemId)
                showPlaylistDialog.makeAbsent()
            },
            elevation = 3.dp,
        )
    }
    showDeleteDialog?.let { item ->
        ConfirmDeleteDialog(
            itemTitle = item.title ?: "",
            onCancel = { showDeleteDialog = null },
            onConfirm = {
                viewModel.deleteItem(item)
                focusRequesters.getOrNull(position.row)?.tryRequestFocus()
                showDeleteDialog = null
            },
        )
    }
}

@Composable
fun AlbumHeader(
    album: BaseItem,
    imageUrl: String?,
    overviewOnClick: () -> Unit,
    bringIntoViewRequester: BringIntoViewRequester,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.padding(top = 32.dp),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier =
                Modifier
                    .fillMaxWidth(.20f)
                    .clip(RoundedCornerShape(16.dp)),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Artist
            Text(
                text = album.artistsString ?: "",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth(.75f)
                        .padding(start = 8.dp),
            )
            Text(
                text = album.name ?: "",
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth(.75f)
                        .padding(start = 8.dp),
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(.60f),
            ) {
                QuickDetails(
                    album.ui.quickDetails,
                    null,
                    Modifier.padding(start = 8.dp),
                )

                album.data.genres?.letNotEmpty {
                    GenreText(it, Modifier.padding(start = 8.dp))
                }

                // Description
                album.data.overview?.let { overview ->
                    OverviewText(
                        overview = overview,
                        maxLines = 3,
                        onClick = overviewOnClick,
                        textBoxHeight = Dp.Unspecified,
                        modifier =
                            Modifier.onFocusChanged {
                                if (it.isFocused) {
                                    scope.launch(ExceptionHandler()) {
                                        bringIntoViewRequester.bringIntoView()
                                    }
                                }
                            },
                    )
                }
            }
        }
    }
}
