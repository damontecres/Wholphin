package com.github.damontecres.wholphin.ui.detail.discover

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.TvDetails
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrPermission
import com.github.damontecres.wholphin.data.model.Trailer
import com.github.damontecres.wholphin.data.model.hasPermission
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.SeerrUserConfig
import com.github.damontecres.wholphin.services.TrailerService
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.DiscoverPersonRow
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GenreText
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetailsText
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialog
import com.github.damontecres.wholphin.ui.data.ItemDetailsDialogInfo
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.listToDotString
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.rememberInt
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.successValue
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import kotlin.time.Duration.Companion.minutes

@Composable
fun DiscoverSeriesDetails(
    preferences: UserPreferences,
    destination: Destination.DiscoveredItem,
    modifier: Modifier = Modifier,
    viewModel: DiscoverSeriesViewModel =
        hiltViewModel<DiscoverSeriesViewModel, DiscoverSeriesViewModel.Factory>(
            creationCallback = { it.create(destination.item) },
        ),
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val state by viewModel.state.collectAsState()
    val request4kEnabled by viewModel.request4kEnabled.collectAsState(false)

    var overviewDialog by remember { mutableStateOf<ItemDetailsDialogInfo?>(null) }
    var seasonDialog by remember { mutableStateOf<DialogParams?>(null) }
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }
    var showRequestSeasonDialog by remember { mutableStateOf(false) }

    when (val st = state.tvSeries) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<TvDetails> -> {
            val item = st.data
            val userConfig by viewModel.userConfig.collectAsState(null)
            DiscoverSeriesDetailsContent(
                preferences = preferences,
                series = item,
                userConfig = userConfig,
                rating = state.rating,
                canCancel = state.canCancelRequest,
                seasons = state.seasons,
                people = state.people,
                similar = state.similar,
                recommended = state.recommended,
                modifier = modifier,
                onClickItem = { index, item ->
                    viewModel.navigateTo(Destination.DiscoveredItem(item))
                },
                onClickPerson = {
                    viewModel.navigateTo(Destination.DiscoveredItem(it))
                },
                goToOnClick = {
                    item.mediaInfo?.jellyfinMediaId?.toUUIDOrNull()?.let {
                        viewModel.navigateTo(
                            Destination.MediaItem(
                                itemId = it,
                                type = BaseItemKind.MOVIE,
                            ),
                        )
                    }
                },
                overviewOnClick = {
                    overviewDialog =
                        ItemDetailsDialogInfo(
                            title = item.name ?: resources.getString(R.string.unknown),
                            overview = item.overview,
                            genres = item.genres?.mapNotNull { it.name }.orEmpty(),
                            files = listOf(),
                        )
                },
                trailerOnClick = {
                    TrailerService.onClick(context, it, viewModel::navigateTo)
                },
                trailers = state.trailers,
                requestOnClick = {
                    viewModel.requestOnClick()
                    showRequestSeasonDialog = true
                },
                cancelOnClick = {
                    item.id?.let { viewModel.cancelRequest(it) }
                },
                moreOnClick = {
                },
                onLongClickPerson = { _, _ -> },
                onLongClickSimilar = { _, _ -> },
            )
        }
    }
    overviewDialog?.let { info ->
        ItemDetailsDialog(
            info = info,
            showFilePath = false,
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
    if (showRequestSeasonDialog) {
        RequestSeasonsDialog(
            id = state.tvSeries.successValue?.id ?: -1,
            title = state.tvSeries.successValue?.name ?: "",
            seasons = state.seasons,
            request4kEnabled = request4kEnabled,
            onSubmit = {
                showRequestSeasonDialog = false
                viewModel.request(it)
            },
            loading = state.profileLoading,
            data = state.requestData,
            onDismissRequest = { showRequestSeasonDialog = false },
        )
    }
}

private const val HEADER_ROW = 0
private const val SEASONS_ROW = HEADER_ROW + 1
private const val PEOPLE_ROW = SEASONS_ROW + 1
private const val EXTRAS_ROW = PEOPLE_ROW + 1
private const val SIMILAR_ROW = EXTRAS_ROW + 1
private const val RECOMMENDED_ROW = SIMILAR_ROW + 1

@Composable
fun DiscoverSeriesDetailsContent(
    preferences: UserPreferences,
    userConfig: SeerrUserConfig?,
    series: TvDetails,
    rating: DiscoverRating?,
    canCancel: Boolean,
    seasons: List<RequestSeason>,
    similar: List<DiscoverItem>,
    recommended: List<DiscoverItem>,
    trailers: List<Trailer>,
    people: List<DiscoverItem>,
    requestOnClick: () -> Unit,
    cancelOnClick: () -> Unit,
    trailerOnClick: (Trailer) -> Unit,
    overviewOnClick: () -> Unit,
    goToOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    onClickItem: (Int, DiscoverItem) -> Unit,
    onClickPerson: (DiscoverItem) -> Unit,
    onLongClickPerson: (Int, DiscoverItem) -> Unit,
    onLongClickSimilar: (Int, DiscoverItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    var position by rememberInt()
    val focusRequesters = remember { List(RECOMMENDED_ROW + 1) { FocusRequester() } }
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequesters.getOrNull(position)?.tryRequestFocus()
    }
    var moreDialog by remember { mutableStateOf<DialogParams?>(null) }

    Box(
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier,
            ) {
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(bringIntoViewRequester),
                    ) {
                        DiscoverSeriesDetailsHeader(
                            series = series,
                            rating = rating,
                            overviewOnClick = overviewOnClick,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 32.dp, bottom = 16.dp),
                        )
                        ExpandableDiscoverButtons(
                            availability =
                                SeerrAvailability.from(series.mediaInfo?.status)
                                    ?: SeerrAvailability.UNKNOWN,
                            requestOnClick = requestOnClick,
                            pendingOnClick = requestOnClick,
                            cancelOnClick = cancelOnClick,
                            moreOnClick = moreOnClick,
                            goToOnClick = goToOnClick,
                            buttonOnFocusChanged = {
                                if (it.isFocused) {
                                    position = HEADER_ROW
                                    scope.launch(ExceptionHandler()) {
                                        bringIntoViewRequester.bringIntoView()
                                    }
                                }
                            },
                            canRequest = userConfig.hasPermission(SeerrPermission.REQUEST),
                            canCancel = canCancel,
                            trailers = trailers,
                            trailerOnClick = trailerOnClick,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp)
                                    .focusRequester(focusRequesters[HEADER_ROW]),
                        )
                    }
                }
//                item {
//                    ItemRow(
//                        title = stringResource(R.string.tv_seasons) + " (${seasons.size})",
//                        items = seasons,
//                        onClickItem = { index, item ->
//                            position = SEASONS_ROW
// //                            onClickItem.invoke(index, item)
//                        },
//                        onLongClickItem = { index, item ->
//                            position = SEASONS_ROW
//                        },
//                        modifier =
//                            Modifier
//                                .fillMaxWidth()
//                                .focusRequester(focusRequesters[SEASONS_ROW]),
//                        cardContent = @Composable { index, item, mod, onClick, onLongClick ->
//                            SeasonCard(
//                                item = item,
//                                onClick = onClick,
//                                onLongClick = onLongClick,
//                                imageHeight = Cards.height2x3,
//                                imageWidth = Dp.Unspecified,
//                                showImageOverlay = true,
//                                modifier = mod,
//                            )
//                        },
//                    )
//                }
                if (people.isNotEmpty()) {
                    item {
                        DiscoverPersonRow(
                            people = people,
                            onClick = {
                                position = PEOPLE_ROW
                                onClickPerson.invoke(it)
                            },
                            onLongClick = { index, person ->
                                position = PEOPLE_ROW
                                onLongClickPerson.invoke(index, person)
                            },
                            modifier = Modifier.focusRequester(focusRequesters[PEOPLE_ROW]),
                        )
                    }
                }
                if (similar.isNotEmpty()) {
                    item {
                        ItemRow(
                            title = stringResource(R.string.more_like_this),
                            items = similar,
                            onClickItem = { index, item ->
                                position = SIMILAR_ROW
                                onClickItem.invoke(index, item)
                            },
                            onLongClickItem = { index, similar ->
                                position = SIMILAR_ROW
                                onLongClickSimilar.invoke(index, similar)
                            },
                            cardContent = { index, item, mod, onClick, onLongClick ->
                                DiscoverItemCard(
                                    item = item,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    showOverlay = true,
                                    modifier = mod,
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[SIMILAR_ROW]),
                        )
                    }
                }
                if (recommended.isNotEmpty()) {
                    item {
                        ItemRow(
                            title = stringResource(R.string.recommended),
                            items = recommended,
                            onClickItem = { index, item ->
                                position = RECOMMENDED_ROW
                                onClickItem.invoke(index, item)
                            },
                            onLongClickItem = { index, similar ->
                                position = RECOMMENDED_ROW
                                onLongClickSimilar.invoke(index, similar)
                            },
                            cardContent = { index, item, mod, onClick, onLongClick ->
                                DiscoverItemCard(
                                    item = item,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    showOverlay = true,
                                    modifier = mod,
                                )
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequesters[RECOMMENDED_ROW]),
                        )
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
}

@Composable
fun DiscoverSeriesDetailsHeader(
    series: TvDetails,
    rating: DiscoverRating?,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = series.name ?: stringResource(R.string.unknown),
            style = MaterialTheme.typography.displaySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(.75f),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            val padding = 4.dp
            val details =
                remember(series, rating) {
                    buildList {
                        series.firstAirDate?.let(::add)
                        series.episodeRunTime
                            ?.average()
                            ?.takeIf { !it.isNaN() && it > 0 }
                            ?.minutes
                            ?.roundMinutes
                            ?.toString()
                            ?.let(::add)
                        // TODO
                    }.let {
                        listToDotString(
                            it,
                            rating?.audienceRating,
                            rating?.criticRating?.toFloat(),
                        )
                    }
                }

            QuickDetailsText(details)
            series.genres?.mapNotNull { it.name }?.letNotEmpty {
                GenreText(it, Modifier.padding(bottom = padding))
            }
            series.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                )
            }
        }
    }
}

fun buildDialogForSeason(
    resources: Resources,
    s: BaseItem,
    onClickItem: (BaseItem) -> Unit,
    markPlayed: (Boolean) -> Unit,
    onClickPlay: (Boolean) -> Unit,
): DialogParams {
    val items =
        buildList {
            add(
                DialogItem(resources.getString(R.string.go_to), Icons.Default.PlayArrow) {
                    onClickItem.invoke(s)
                },
            )
            if (s.data.userData?.played == true) {
                add(
                    DialogItem(resources.getString(R.string.mark_unwatched), R.string.fa_eye) {
                        markPlayed.invoke(false)
                    },
                )
            } else {
                add(
                    DialogItem(resources.getString(R.string.mark_watched), R.string.fa_eye_slash) {
                        markPlayed.invoke(true)
                    },
                )
            }
            add(
                DialogItem(
                    resources.getString(R.string.play),
                    Icons.Default.PlayArrow,
                ) {
                    onClickPlay.invoke(false)
                },
            )
            add(
                DialogItem(
                    resources.getString(R.string.shuffle),
                    R.string.fa_shuffle,
                ) {
                    onClickPlay.invoke(true)
                },
            )
        }
    return DialogParams(
        title = s.name ?: resources.getString(R.string.tv_season),
        fromLongClick = true,
        items = items,
    )
}
