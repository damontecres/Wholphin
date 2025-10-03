package com.github.damontecres.dolphin.ui.detail.movie

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.data.model.Person
import com.github.damontecres.dolphin.data.model.Video
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.cards.PersonCard
import com.github.damontecres.dolphin.ui.components.ErrorMessage
import com.github.damontecres.dolphin.ui.components.ExpandablePlayButtons
import com.github.damontecres.dolphin.ui.components.LoadingPage
import com.github.damontecres.dolphin.ui.detail.LoadingItemViewModel
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.letNotEmpty
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.tryRequestFocus
import com.github.damontecres.dolphin.util.ExceptionHandler
import com.github.damontecres.dolphin.util.LoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration

@HiltViewModel
class MovieViewModel
    @Inject
    constructor(
        api: ApiClient,
    ) : LoadingItemViewModel<Video>(api) {
        val people = MutableLiveData<List<Person>>(listOf())

        override fun init(
            itemId: UUID,
            potential: BaseItem?,
        ): Job? =
            viewModelScope.launch(ExceptionHandler()) {
                super.init(itemId, potential)?.join()
                item.value?.let { item ->
                    people.value =
                        item.data.people?.letNotEmpty { people ->
                            people.map { Person.fromDto(it, api) }
                        }
                }
            }
    }

@Composable
fun MovieDetails(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: MovieViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.init(destination.itemId, destination.item)
    }
    val item by viewModel.item.observeAsState()
    val people by viewModel.people.observeAsState(listOf())
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)
    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)
        LoadingState.Loading -> LoadingPage()
        LoadingState.Success -> {
            item?.let {
                MovieDetailsContent(
                    preferences = preferences,
                    navigationManager = navigationManager,
                    movie = it,
                    people = people,
                    overviewOnClick = {},
                    moreOnClick = {},
                    watchOnClick = {},
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
fun MovieDetailsContent(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    movie: BaseItem,
    people: List<Person>,
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(32.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
                ) {
                    MovieDetailsHeader(
                        movie = movie,
                        bringIntoViewRequester = bringIntoViewRequester,
                        overviewOnClick = overviewOnClick,
                        Modifier
                            .fillMaxWidth(.7f),
                    )
                    ExpandablePlayButtons(
                        resumePosition = resumePosition,
                        watched = dto.userData?.played ?: false,
                        playOnClick = {
                            navigationManager.navigateTo(
                                Destination.Playback(
                                    movie.id,
                                    it.inWholeMilliseconds,
                                    movie,
                                ),
                            )
                        },
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 120.dp),
                    ) {
                        Text(
                            text = "People",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        LazyRow(
                            state = rememberLazyListState(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            contentPadding = PaddingValues(8.dp),
                            modifier =
                                Modifier
                                    .padding(start = 16.dp)
                                    .fillMaxWidth()
                                    .focusRestorer(),
                        ) {
                            itemsIndexed(people) { index, item ->
                                PersonCard(
                                    item = item,
                                    onClick = {},
                                    onLongClick = {},
                                    modifier = Modifier,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
