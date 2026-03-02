package com.github.damontecres.wholphin.ui.detail.music

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MediaReportService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.DefaultItemFields
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.BannerCardWithTitle
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GenreText
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.Optional
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.data.AddPlaylistViewModel
import com.github.damontecres.wholphin.ui.detail.PlaylistDialog
import com.github.damontecres.wholphin.ui.detail.PlaylistLoadingState
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.toBaseItems
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
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import java.util.UUID

@HiltViewModel(assistedFactory = ArtistViewModel.Factory::class)
class ArtistViewModel
    @AssistedInject
    constructor(
        api: ApiClient,
        musicService: MusicService,
        navigationManager: NavigationManager,
        @param:ApplicationContext private val context: Context,
        val serverRepository: ServerRepository,
        val mediaReportService: MediaReportService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val userPreferencesService: UserPreferencesService,
        private val backdropService: BackdropService,
        private val imageUrlService: ImageUrlService,
        @Assisted val itemId: UUID,
    ) : MusicViewModel(api, musicService, navigationManager) {
        @AssistedFactory
        interface Factory {
            fun create(itemId: UUID): ArtistViewModel
        }

        private val _state = MutableStateFlow(ArtistState.EMPTY)
        val state: StateFlow<ArtistState> = _state

        val currentMusic = musicService.state

        init {
            viewModelScope.launchIO {
                try {
                    val itemDeferred =
                        async {
                            api.userLibraryApi
                                .getItem(itemId = itemId)
                                .content
                                .let { BaseItem(it, false) }
                        }
                    val albumsDeferred =
                        async {
                            val request =
                                GetItemsRequest(
                                    parentId = itemId,
                                    fields = DefaultItemFields,
                                    includeItemTypes = listOf(BaseItemKind.MUSIC_ALBUM),
                                    sortBy =
                                        listOf(
                                            ItemSortBy.PREMIERE_DATE,
                                            ItemSortBy.SORT_NAME,
                                        ),
                                    sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
                                )
                            ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
                        }
                    val artist = itemDeferred.await()
                    val albums = albumsDeferred.await()
                    val imageUrl = imageUrlService.getItemImageUrl(artist, ImageType.PRIMARY)
                    _state.update {
                        it.copy(
                            artist = artist,
                            imageUrl = imageUrl,
                            albums = albums,
                            loading = LoadingState.Success,
                        )
                    }

                    viewModelScope.launchIO {
                        val request =
                            GetItemsRequest(
                                parentId = itemId,
                                fields = DefaultItemFields,
                                recursive = true,
                                includeItemTypes = listOf(BaseItemKind.AUDIO),
                                minCommunityRating = 1.0,
                                sortBy =
                                    listOf(
                                        ItemSortBy.COMMUNITY_RATING,
                                    ),
                                sortOrder = listOf(SortOrder.DESCENDING),
                                limit = 10,
                            )
                        val topSongs =
                            GetItemsRequestHandler.execute(api, request).toBaseItems(api, false)
                        if (topSongs.isNotEmpty()) {
                            _state.update {
                                it.copy(topSongs = topSongs)
                            }
                        }
                    }
                    if (state.value.similar.isEmpty()) {
                        viewModelScope.launchIO {
                            val similar =
                                api.libraryApi
                                    .getSimilarItems(
                                        GetSimilarItemsRequest(
                                            userId = serverRepository.currentUser.value?.id,
                                            itemId = itemId,
                                            excludeArtistIds = listOf(itemId),
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
                                artistIds = listOf(itemId),
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

        fun init() {
            viewModelScope.launchDefault {
                state.value.artist?.let {
                    backdropService.submit(it)
                }
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            val artist =
                api.userLibraryApi
                    .getItem(itemId = itemId)
                    .content
                    .let { BaseItem(it, false) }
            _state.update { it.copy(artist = artist) }
        }
    }

data class ArtistState(
    val artist: BaseItem?,
    val imageUrl: String?,
    val topSongs: List<BaseItem?>,
    val albums: List<BaseItem?>,
    val similar: List<BaseItem>,
    val loading: LoadingState,
    val musicVideos: List<BaseItem?>,
) {
    companion object {
        val EMPTY =
            ArtistState(
                null,
                null,
                emptyList(),
                emptyList(),
                emptyList(),
                LoadingState.Pending,
                emptyList(),
            )
    }
}

@Composable
fun ArtistDetailsPage(
    itemId: UUID,
    modifier: Modifier = Modifier,
    viewModel: ArtistViewModel =
        hiltViewModel<ArtistViewModel, ArtistViewModel.Factory>(
            creationCallback = { it.create(itemId) },
        ),
    playlistViewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by viewModel.state.collectAsState()
    val currentMusic by viewModel.currentMusic.collectAsState()

    var showPlaylistDialog by remember { mutableStateOf<Optional<UUID>>(Optional.absent()) }
    val playlistState by playlistViewModel.playlistState.observeAsState(PlaylistLoadingState.Pending)
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }
    val moreDialogActions =
        remember {
            MusicMoreDialogActions(
                onNavigate = { viewModel.navigationManager.navigateTo(it) },
                onClickPlay = { index, item -> viewModel.play(item) },
                onClickPlayNext = { _, item -> viewModel.playNext(item) },
                onClickAddToQueue = { index, item -> viewModel.addToQueue(item, index) },
                onClickFavorite = { itemId, favorite -> viewModel.setFavorite(itemId, favorite) },
                onClickAddPlaylist = { itemId ->
                    playlistViewModel.loadPlaylists(MediaType.AUDIO)
                    showPlaylistDialog.makePresent(itemId)
                },
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
            val artist = state.artist!!
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.requestFocus()
                viewModel.init()
            }
            val bringIntoViewRequester = remember { BringIntoViewRequester() }
            Box(modifier = modifier) {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier =
                        Modifier
                            .fillMaxSize(),
                ) {
                    item {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .bringIntoViewRequester(bringIntoViewRequester)
                                    .padding(bottom = 16.dp),
                        ) {
                            ArtistHeader(
                                artist = artist,
                                imageUrl = state.imageUrl,
                                overviewOnClick = {},
                                bringIntoViewRequester = bringIntoViewRequester,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            MusicExpandableButtons(
                                actions =
                                    remember {
                                        MusicButtonActions(
                                            onClickPlay = { viewModel.play(artist) },
                                            onClickInstantMix = { viewModel.startInstantMix(artist.id) },
                                            onClickFavorite = {
                                                viewModel.setFavorite(
                                                    artist.id,
                                                    !artist.favorite,
                                                )
                                            },
                                            onClickMore = {
                                                moreDialog =
                                                    DialogParams(
                                                        fromLongClick = false,
                                                        title = artist.name ?: "",
                                                        items =
                                                            buildMoreDialogForMusic(
                                                                context = context,
                                                                actions = moreDialogActions,
                                                                item = artist,
                                                                index = 0,
                                                            ),
                                                    )
                                            },
                                        )
                                    },
                                favorite = artist.favorite,
                                modifier =
                                    Modifier
                                        .onFocusChanged {
                                            if (it.hasFocus) scope.launch { bringIntoViewRequester.bringIntoView() }
                                        }.focusRequester(focusRequester),
                            )
                        }
                    }
                    if (state.topSongs.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.popular_songs),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                        itemsIndexed(state.topSongs) { index, song ->
                            SongListItem(
                                song = song,
                                onClick = { song?.let { viewModel.play(it) } },
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
                                                    ),
                                            )
                                    }
                                },
                                modifier = Modifier.padding(horizontal = 16.dp),
                                showArtist = false,
                                isPlaying = song != null && currentMusic.currentItemId == song.id,
                            )
                        }
                    }
                    item {
                        ItemRow(
                            title = stringResource(R.string.albums),
                            items = state.albums,
                            onClickItem = { index, album ->
                                viewModel.navigationManager.navigateTo(album.destination())
                            },
                            onLongClickItem = { index, album ->
                                // TODO
                            },
                            cardContent = { index: Int, album: BaseItem?, mod: Modifier, onClick: () -> Unit, onLongClick: () -> Unit ->
                                BannerCardWithTitle(
                                    title = album?.name,
                                    subtitle = album?.data?.productionYear?.toString(),
                                    item = album,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    aspectRatio = AspectRatios.SQUARE,
                                )
                            },
                        )
                    }
                    if (state.musicVideos.isNotEmpty()) {
                        item {
                            ItemRow(
                                title = stringResource(R.string.music_videos),
                                items = state.musicVideos,
                                onClickItem = { index, item ->
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
                            )
                        }
                    }
                    if (state.similar.isNotEmpty()) {
                        item {
                            ItemRow(
                                title = stringResource(R.string.more_like_this),
                                items = state.similar,
                                onClickItem = { index, item ->
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
                                        aspectRatio = AspectRatios.SQUARE,
                                        modifier = mod,
                                    )
                                },
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
}

@Composable
fun ArtistHeader(
    artist: BaseItem,
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
                text = artist.artistsString ?: "",
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
                text = artist.name ?: "",
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
                    artist.ui.quickDetails,
                    null,
                    Modifier.padding(start = 8.dp),
                )

                artist.data.genres?.letNotEmpty {
                    GenreText(it, Modifier.padding(start = 8.dp))
                }

                // Description
                artist.data.overview?.let { overview ->
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
