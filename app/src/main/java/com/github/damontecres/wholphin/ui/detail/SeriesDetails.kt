package com.github.damontecres.wholphin.ui.detail

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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.components.ConfirmDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.DotSeparatedRow
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.StarRating
import com.github.damontecres.wholphin.ui.components.StarRatingPrecision
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberPosition
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration

@Composable
fun SeriesDetails(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    LifecycleStartEffect(destination.itemId) {
        viewModel.maybePlayThemeSong(
            destination.itemId,
            preferences.appPreferences.interfacePreferences.playThemeSongs,
        )
        onStopOrDispose {
            viewModel.release()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.init(preferences, destination.itemId, destination.item, null, null)
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)

    val item by viewModel.item.observeAsState()
    val seasons by viewModel.seasons.observeAsState(ItemListAndMapping.empty())
    val people by viewModel.people.observeAsState(listOf())

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var showWatchConfirmation by remember { mutableStateOf(false) }
    var seasonDialog by remember { mutableStateOf<DialogParams?>(null) }

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)
        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage()
        LoadingState.Success -> {
            item?.let { item ->
                val played = item.data.userData?.played ?: false
                SeriesDetailsContent(
                    preferences = preferences,
                    series = item,
                    seasons = seasons,
                    people = people,
                    played = played,
                    modifier = modifier,
                    onClickItem = { viewModel.navigateTo(it.destination()) },
                    onLongClickItem = { season ->
                        seasonDialog =
                            buildDialogForSeason(
                                season,
                                onClickItem = { viewModel.navigateTo(it.destination()) },
                                markPlayed = { played ->
                                    viewModel.setSeasonWatched(season.id, played)
                                },
                            )
                    },
                    overviewOnClick = {
                        overviewDialog =
                            ItemDetailsDialogInfo(
                                title = item.name ?: "Unknown",
                                overview = item.data.overview,
                                files = listOf(),
                            )
                    },
                    playOnClick = { viewModel.playNextUp() },
                    watchOnClick = { showWatchConfirmation = true },
                )
                if (showWatchConfirmation) {
                    ConfirmDialog(
                        title = item.name ?: "",
                        body = if (played) "Mark entire series as unplayed?" else "Mark entire series as played?",
                        onCancel = {
                            showWatchConfirmation = false
                        },
                        onConfirm = {
                            viewModel.setWatchedSeries(!played)
                            showWatchConfirmation = false
                        },
                    )
                }
            }
        }
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            onDismissRequest = { overviewDialog = null },
        )
    }
    seasonDialog?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            waitToLoad = params.fromLongClick,
            onDismissRequest = { seasonDialog = null },
        )
    }
}

@Composable
fun SeriesDetailsContent(
    preferences: UserPreferences,
    series: BaseItem,
    seasons: ItemListAndMapping,
    people: List<Person>,
    played: Boolean,
    onClickItem: (BaseItem) -> Unit,
    onLongClickItem: (BaseItem) -> Unit,
    overviewOnClick: () -> Unit,
    playOnClick: () -> Unit,
    watchOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    var position by rememberPosition()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }

    Box(
        modifier = modifier,
    ) {
        if (series.backdropImageUrl.isNotNullOrBlank()) {
            val gradientColor = MaterialTheme.colorScheme.background
            AsyncImage(
                model = series.backdropImageUrl,
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

        Column(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier,
            ) {
                item {
                    SeriesDetailsHeader(
                        series = series,
                        played = played,
                        overviewOnClick = overviewOnClick,
                        playOnClick = playOnClick,
                        watchOnClick = watchOnClick,
                        bringIntoViewRequester = bringIntoViewRequester,
                        modifier =
                            Modifier
                                .fillMaxWidth(.6f)
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .padding(bottom = 80.dp)
                                .ifElse(position.row < 0, Modifier.focusRequester(focusRequester)),
                    )
                }
                item {
                    ItemRow(
                        title = "Seasons",
                        items = seasons.items,
                        onClickItem = onClickItem,
                        onLongClickItem = onLongClickItem,
                        cardOnFocus = { isFocused, index ->
//                            if (isFocused) {
//                                scope.launch(ExceptionHandler()) {
//                                    bringIntoViewRequester.bringIntoView()
//                                }
//                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth(),
                        cardContent = @Composable { index, item, mod, onClick, onLongClick ->
                            SeasonCard(
                                item = item,
                                onClick = {
                                    position = RowColumn(0, index)
                                    onClick.invoke()
                                },
                                onLongClick = onLongClick,
                                imageHeight = Cards.height2x3,
                                imageWidth = Dp.Unspecified,
                                showImageOverlay = true,
                                modifier =
                                    mod
                                        .ifElse(
                                            position.row == 0 && position.column == index,
                                            Modifier.focusRequester(focusRequester),
                                        ),
                            )
                        },
                    )
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
            }
        }
    }
}

@Composable
fun SeriesDetailsHeader(
    series: BaseItem,
    played: Boolean,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    playOnClick: () -> Unit,
    watchOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val dto = series.data
    val details =
        buildList {
            dto.productionYear?.let { add(it.toString()) }
            dto.runTimeTicks
                ?.ticks
                ?.roundMinutes
                ?.let { add(it.toString()) }
            dto.officialRating?.let(::add)
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = series.name ?: "Unknown",
            style = MaterialTheme.typography.displaySmall,
            modifier = Modifier.fillMaxWidth(),
        )
        DotSeparatedRow(
            texts = details,
            textStyle = MaterialTheme.typography.titleMedium,
        )

        dto.genres?.letNotEmpty {
            Text(
                text = it.joinToString(", "),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier,
            )
        }

        dto.communityRating?.let {
            if (it >= 0f) {
                StarRating(
                    rating100 = (it * 10).toInt(),
                    onRatingChange = {},
                    enabled = false,
                    precision = StarRatingPrecision.HALF,
                    playSoundOnFocus = true,
                    modifier = Modifier.height(24.dp),
                )
            }
        }

        dto.overview?.let { overview ->
            OverviewText(
                overview = overview,
                maxLines = 3,
                onClick = overviewOnClick,
                textBoxHeight = Dp.Unspecified,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(start = 16.dp),
        ) {
            ExpandablePlayButton(
                title = R.string.play,
                resume = Duration.ZERO,
                icon = Icons.Default.PlayArrow,
                onClick = { playOnClick.invoke() },
                modifier =
                    Modifier.onFocusChanged {
                        if (it.isFocused) {
                            scope.launch(ExceptionHandler()) {
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
            )
            ExpandableFaButton(
                title = if (played) R.string.mark_unwatched else R.string.mark_watched,
                iconStringRes = if (played) R.string.fa_eye else R.string.fa_eye_slash,
                onClick = watchOnClick,
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

fun buildDialogForSeason(
    s: BaseItem,
    onClickItem: (BaseItem) -> Unit,
    markPlayed: (Boolean) -> Unit,
): DialogParams {
    val items =
        buildList {
            add(
                DialogItem("Go to", Icons.Default.PlayArrow) {
                    onClickItem.invoke(s)
                },
            )
            if (s.data.userData?.played == true) {
                add(
                    DialogItem("Mark as unplayed", R.string.fa_eye) {
                        markPlayed.invoke(false)
                    },
                )
            } else {
                add(
                    DialogItem("Mark as played", R.string.fa_eye_slash) {
                        markPlayed.invoke(true)
                    },
                )
            }
        }
    return DialogParams(
        title = s.name ?: "Season",
        fromLongClick = true,
        items = items,
    )
}
