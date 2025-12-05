package com.github.damontecres.wholphin.ui.detail.series

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.cards.BannerCard
import com.github.damontecres.wholphin.ui.cards.PersonRow
import com.github.damontecres.wholphin.ui.components.DetailsBackdropImage
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.SeriesName
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.formatDateTime
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.logTab
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.rememberDelayedNestedScroll
import kotlinx.coroutines.launch
import kotlin.time.Duration

@Composable
fun SeriesOverviewContent(
    preferences: UserPreferences,
    series: BaseItem,
    seasons: List<BaseItem?>,
    episodes: EpisodeList,
    chosenStreams: ChosenStreams?,
    peopleInEpisode: List<Person>,
    position: SeriesOverviewPosition,
    backdropImageUrl: String?,
    firstItemFocusRequester: FocusRequester,
    episodeRowFocusRequester: FocusRequester,
    peopleRowFocusRequester: FocusRequester,
    onFocus: (SeriesOverviewPosition) -> Unit,
    onClick: (BaseItem) -> Unit,
    onLongClick: (BaseItem) -> Unit,
    playOnClick: (Duration) -> Unit,
    watchOnClick: () -> Unit,
    favoriteOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    overviewOnClick: () -> Unit,
    personOnClick: (Person) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var selectedTabIndex by rememberSaveable(position) { mutableIntStateOf(position.seasonTabIndex) }
    LaunchedEffect(selectedTabIndex) {
        logTab("series_overview", selectedTabIndex)
    }
    val tabRowFocusRequester = remember { FocusRequester() }

    val focusedEpisode =
        (episodes as? EpisodeList.Success)?.episodes?.getOrNull(position.episodeRowIndex)
    var pageHasFocus by remember { mutableStateOf(false) }
    var cardRowHasFocus by remember { mutableStateOf(false) }
    val dimming by animateFloatAsState(if (pageHasFocus && !cardRowHasFocus) .4f else 1f)

    val scrollState = rememberScrollState()
    val scrollConnection = rememberDelayedNestedScroll()
    Box(
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        DetailsBackdropImage(backdropImageUrl)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .focusGroup()
                    .nestedScroll(scrollConnection)
                    .verticalScroll(scrollState)
                    .onFocusChanged { pageHasFocus = it.hasFocus },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .focusGroup()
                        .bringIntoViewRequester(bringIntoViewRequester),
            ) {
                val paddingValues =
                    if (preferences.appPreferences.interfacePreferences.showClock) {
                        PaddingValues(start = 16.dp, end = 100.dp)
                    } else {
                        PaddingValues(start = 16.dp, end = 16.dp)
                    }
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    tabs =
                        seasons.mapNotNull {
                            it?.name
                                ?: (stringResource(R.string.tv_season) + " ${it?.data?.indexNumber}")
                        },
                    onClick = {
                        selectedTabIndex = it
                        onFocus.invoke(SeriesOverviewPosition(it, 0))
                    },
                    modifier =
                        Modifier
                            .focusRequester(tabRowFocusRequester)
                            .focusRestorer()
                            .padding(paddingValues)
                            .fillMaxWidth(),
                )
                SeriesName(series.name, Modifier)
                FocusedEpisodeHeader(
                    preferences = preferences,
                    ep = focusedEpisode,
                    chosenStreams = chosenStreams,
                    overviewOnClick = overviewOnClick,
                    overviewOnFocus = {
                        if (it.isFocused) {
                            scope.launch {
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(.6f),
                )

                key(position.seasonTabIndex) {
                    when (val eps = episodes) {
                        EpisodeList.Loading -> LoadingPage()
                        is EpisodeList.Error -> ErrorMessage(eps.message, eps.exception)
                        is EpisodeList.Success -> {
                            val state = rememberLazyListState()
                            OneTimeLaunchedEffect {
                                if (state.firstVisibleItemIndex != position.episodeRowIndex) {
                                    state.scrollToItem(position.episodeRowIndex)
                                }
                                firstItemFocusRequester.tryRequestFocus()
                            }
                            LazyRow(
                                state = state,
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                modifier =
                                    Modifier
                                        .focusRestorer(firstItemFocusRequester)
                                        .focusRequester(episodeRowFocusRequester)
                                        .onFocusChanged {
                                            cardRowHasFocus = it.hasFocus
                                        },
                            ) {
                                itemsIndexed(eps.episodes) { episodeIndex, episode ->
                                    val interactionSource = remember { MutableInteractionSource() }
                                    if (interactionSource.collectIsFocusedAsState().value) {
                                        onFocus.invoke(
                                            SeriesOverviewPosition(
                                                selectedTabIndex,
                                                episodeIndex,
                                            ),
                                        )
                                    }
                                    val cornerText =
                                        episode?.data?.indexNumber?.let { "E$it" }
                                            ?: episode?.data?.premiereDate?.let(::formatDateTime)
                                    BannerCard(
                                        name = episode?.name,
                                        item = episode,
                                        aspectRatio =
                                            episode
                                                ?.data
                                                ?.primaryImageAspectRatio
                                                ?.toFloat()
                                                ?.coerceAtLeast(AspectRatios.FOUR_THREE)
                                                ?: (AspectRatios.WIDE),
                                        cornerText = cornerText,
                                        played = episode?.data?.userData?.played ?: false,
                                        playPercent =
                                            episode?.data?.userData?.playedPercentage
                                                ?: 0.0,
                                        onClick = { if (episode != null) onClick.invoke(episode) },
                                        onLongClick = {
                                            if (episode != null) {
                                                onLongClick.invoke(
                                                    episode,
                                                )
                                            }
                                        },
                                        modifier =
                                            Modifier
                                                .ifElse(
                                                    episodeIndex == position.episodeRowIndex,
                                                    Modifier.focusRequester(firstItemFocusRequester),
                                                ).ifElse(
                                                    episodeIndex != position.episodeRowIndex,
                                                    Modifier
                                                        .background(
                                                            Color.Black,
                                                            shape = RoundedCornerShape(8.dp),
                                                        ).alpha(dimming),
                                                ).onFocusChanged {
                                                    if (it.isFocused) {
                                                        scope.launch {
                                                            bringIntoViewRequester.bringIntoView()
                                                        }
                                                    }
                                                },
                                        interactionSource = interactionSource,
                                        cardHeight = 120.dp,
                                    )
                                }
                            }
                        }
                    }
                }

                focusedEpisode?.let { ep ->
                    FocusedEpisodeFooter(
                        preferences = preferences,
                        ep = ep,
                        chosenStreams = chosenStreams,
                        playOnClick = playOnClick,
                        moreOnClick = moreOnClick,
                        watchOnClick = {
                            watchOnClick.invoke()
                            episodeRowFocusRequester.tryRequestFocus()
                        },
                        favoriteOnClick = favoriteOnClick,
                        buttonOnFocusChanged = {
                            if (it.isFocused) {
                                scope.launch {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = peopleInEpisode.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                PersonRow(
                    people = peopleInEpisode,
                    onClick = personOnClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
//                            .padding(bottom = 80.dp)
                            .focusRequester(peopleRowFocusRequester),
                )
            }
        }
    }
}
