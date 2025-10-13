package com.github.damontecres.dolphin.ui.detail.movie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import coil3.compose.AsyncImage
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.data.model.Chapter
import com.github.damontecres.dolphin.data.model.Person
import com.github.damontecres.dolphin.data.model.Video
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.cards.ChapterRow
import com.github.damontecres.dolphin.ui.cards.PersonRow
import com.github.damontecres.dolphin.ui.components.DialogItem
import com.github.damontecres.dolphin.ui.components.DialogParams
import com.github.damontecres.dolphin.ui.components.DialogPopup
import com.github.damontecres.dolphin.ui.components.ErrorMessage
import com.github.damontecres.dolphin.ui.components.ExpandablePlayButtons
import com.github.damontecres.dolphin.ui.components.LoadingPage
import com.github.damontecres.dolphin.ui.data.ItemDetailsDialog
import com.github.damontecres.dolphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.dolphin.ui.detail.LoadingItemViewModel
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.letNotEmpty
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.tryRequestFocus
import com.github.damontecres.dolphin.util.ExceptionHandler
import com.github.damontecres.dolphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration

@HiltViewModel
class MovieViewModel
    @Inject
    constructor(
        api: ApiClient,
        val navigationManager: NavigationManager,
    ) : LoadingItemViewModel<Video>(api) {
        private lateinit var itemId: UUID
        val people = MutableLiveData<List<Person>>(listOf())
        val chapters = MutableLiveData<List<Chapter>>(listOf())

        override fun init(
            itemId: UUID,
            potential: BaseItem?,
        ): Job? {
            this.itemId = itemId
            return viewModelScope.launch(ExceptionHandler()) {
                super.init(itemId, potential)?.join()
                item.value?.let { item ->
                    people.value =
                        item.data.people
                            ?.letNotEmpty { people ->
                                people.map { Person.fromDto(it, api) }
                            }.orEmpty()
                    chapters.value = Chapter.fromDto(item.data, api)
                }
            }
        }

        fun setWatched(played: Boolean) =
            viewModelScope.launch(ExceptionHandler() + Dispatchers.IO) {
                if (played) {
                    api.playStateApi.markPlayedItem(itemId)
                } else {
                    api.playStateApi.markUnplayedItem(itemId)
                }
                init(itemId, null)
            }
    }

@Composable
fun MovieDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: MovieViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.init(destination.itemId, destination.item)
    }
    val item by viewModel.item.observeAsState()
    val people by viewModel.people.observeAsState(listOf())
    val chapters by viewModel.chapters.observeAsState(listOf())
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)
        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()
        LoadingState.Success -> {
            item?.let { movie ->
                MovieDetailsContent(
                    preferences = preferences,
                    movie = movie,
                    people = people,
                    chapters = chapters,
                    playOnClick = {
                        viewModel.navigationManager.navigateTo(
                            Destination.Playback(
                                movie.id,
                                it.inWholeMilliseconds,
                                movie,
                            ),
                        )
                    },
                    overviewOnClick = {
                        overviewDialog =
                            ItemDetailsDialogInfo(
                                title = movie.name ?: "Unknown",
                                overview = movie.data.overview,
                                files =
                                    movie.data.mediaSources
                                        ?.mapNotNull { it.path }
                                        .orEmpty(),
                            )
                    },
                    moreOnClick = {
                        moreDialog =
                            DialogParams(
                                fromLongClick = false,
                                title = movie.name + " (${movie.data.productionYear ?: ""})",
                                items =
                                    listOf(
                                        DialogItem(
                                            "Play",
                                            Icons.Default.PlayArrow,
                                            iconColor = Color.Green.copy(alpha = .8f),
                                        ) {
                                            viewModel.navigationManager.navigateTo(
                                                Destination.Playback(movie),
                                            )
                                        },
//                                        DialogItem(
//                                            "Playback Settings",
//                                            Icons.Default.Settings,
// //                                                iconColor = Color.Green.copy(alpha = .8f),
//                                        ) {
//                                            // TODO choose audio or subtitle tracks?
//                                        },
//                                        DialogItem(
//                                            "Play Version",
//                                            Icons.Default.PlayArrow,
//                                            iconColor = Color.Green.copy(alpha = .8f),
//                                        ) {
//                                            // TODO only show for multiple files
//                                        },
                                    ),
                            )
                    },
                    watchOnClick = {
                        viewModel.setWatched((movie.data.userData?.played ?: false).not())
                    },
                    modifier = modifier,
                )
            }
        }
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            onDismissRequest = { overviewDialog = null },
        )
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
}

@Composable
fun MovieDetailsContent(
    preferences: UserPreferences,
    movie: BaseItem,
    people: List<Person>,
    chapters: List<Chapter>,
    playOnClick: (Duration) -> Unit,
    overviewOnClick: () -> Unit,
    watchOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val dto = movie.data
    val backdropImageUrl = movie.backdropImageUrl
    val resumePosition = dto.userData?.playbackPositionTicks?.ticks ?: Duration.ZERO

    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(Unit) {
//        bringIntoViewRequester.bringIntoView()
        focusRequester.tryRequestFocus()
    }
    Box(modifier = modifier) {
        if (backdropImageUrl.isNotNullOrBlank()) {
            val gradientColor = MaterialTheme.colorScheme.background
            AsyncImage(
                model = backdropImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopEnd,
                modifier =
                    Modifier
                        .fillMaxHeight(.75f)
                        .alpha(.5f)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, gradientColor),
                                    startY = size.height * .5f,
                                ),
                            )
                            drawRect(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, gradientColor),
                                    endX = 0f,
                                    startX = size.width * .75f,
                                ),
                            )
                        },
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(32.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .bringIntoViewRequester(bringIntoViewRequester)
                            .padding(bottom = 56.dp),
                ) {
                    MovieDetailsHeader(
                        movie = movie,
                        bringIntoViewRequester = bringIntoViewRequester,
                        overviewOnClick = overviewOnClick,
                        Modifier
                            .fillMaxWidth(.7f)
                            .padding(bottom = 8.dp),
                    )
                    ExpandablePlayButtons(
                        resumePosition = resumePosition,
                        watched = dto.userData?.played ?: false,
                        playOnClick = playOnClick,
                        moreOnClick = moreOnClick,
                        watchOnClick = watchOnClick,
                        buttonOnFocusChanged = {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                        modifier = Modifier.focusRequester(focusRequester),
                    )
                }
            }
            if (people.isNotEmpty()) {
                item {
                    PersonRow(
                        people = people,
                        onClick = {},
                        onLongClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (chapters.isNotEmpty()) {
                item {
                    ChapterRow(
                        chapters = chapters,
                        onClick = { playOnClick.invoke(it.position) },
                        onLongClick = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
