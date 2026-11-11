package com.github.damontecres.wholphin.ui.discover

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.RequestStatus
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrDownloadItem
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.data.model.overallProgressPercent
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.SeerrServerRepository
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.cards.DiscoverItemCard
import com.github.damontecres.wholphin.ui.cards.ItemCardImage
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.HeaderUtils
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabRow
import com.github.damontecres.wholphin.ui.components.TitleOrLogo
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.formatBytes
import com.github.damontecres.wholphin.ui.formatDate
import com.github.damontecres.wholphin.ui.formatSeasonEpisode
import com.github.damontecres.wholphin.ui.formatSeasonNumber
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import timber.log.Timber
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import kotlin.math.roundToInt

private const val TAB_ALL = 0
private const val TAB_DOWNLOADING = 1
private const val TAB_RELEASED_NOT_FOUND = 2
private const val TAB_FUTURE_RELEASES = 3

internal fun List<RequestGridItem>.filterAndGroupActiveRequests(): List<RequestGridItem> =
    filter { it.isActiveRequest }
        .groupBy { "${it.item.type}_${it.item.id}" }
        .map { (_, group) ->
            val primary =
                group.maxByOrNull {
                    val downloadBonus = if (it.item.isActivelyDownloading) 1_000 else 0
                    downloadBonus + it.requestStatus.status
                } ?: group.first()
            val mergedSeasons =
                group
                    .flatMap { it.requestedSeasons }
                    .distinct()
                    .sorted()
            val mergedDownloads =
                group
                    .flatMap { it.item.downloads }
                    .distinct()
            val mergedScheduledReleases =
                group
                    .flatMap { it.scheduledReleases }
                    .distinct()
                    .sorted()
            val mergedHasReleasedMissing =
                group.any { it.hasReleasedMissingContent }
            val enrichedItem =
                primary.item.copy(
                    downloads = mergedDownloads,
                    downloadProgress = mergedDownloads.overallProgressPercent(),
                )
            primary.copy(
                item = enrichedItem,
                requestedSeasons = mergedSeasons,
                scheduledReleases = mergedScheduledReleases,
                hasReleasedMissingContent = mergedHasReleasedMissing,
            )
        }

data class TimelineReleaseEvent(
    val id: String,
    val requestItem: RequestGridItem,
    val release: ScheduledRelease,
)

internal fun buildTimelineReleaseEvents(items: List<RequestGridItem>): List<TimelineReleaseEvent> =
    items
        .filter { it.isFutureRelease }
        .flatMap { reqItem ->
            val releases =
                reqItem.scheduledReleases.ifEmpty {
                    listOf(
                        ScheduledRelease(
                            date = reqItem.nextReleaseDate,
                            overview = reqItem.item.overview,
                        ),
                    )
                }
            releases.take(10).mapIndexed { idx, rel ->
                TimelineReleaseEvent(
                    id = "${reqItem.gridId}_${rel.date}_${rel.seasonNumber}_${rel.episodeNumber}_$idx",
                    requestItem = reqItem,
                    release = rel,
                )
            }
        }.sortedWith(
            compareBy<TimelineReleaseEvent, LocalDate?>(nullsLast()) { it.release.date }
                .thenBy { it.requestItem.item.title ?: "" }
                .thenBy { it.release.seasonNumber ?: 0 }
                .thenBy { it.release.episodeNumber ?: 0 },
        )

private fun formatRelativeReleaseCountdown(
    context: Context,
    date: LocalDate?,
    today: LocalDate = LocalDate.now(),
): String {
    if (date == null) return context.getString(R.string.date_tba)
    val days = ChronoUnit.DAYS.between(today, date).toInt()
    return when {
        days <= 0 -> context.getString(R.string.today)
        days == 1 -> context.getString(R.string.tomorrow)
        else -> context.getString(R.string.in_days, days)
    }
}

@HiltViewModel
class ActiveRequestsViewModel
    @Inject
    constructor(
        private val seerrServerRepository: SeerrServerRepository,
        private val seerrService: SeerrService,
        val navigationManager: NavigationManager,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        val state = MutableStateFlow(SeerrRequestsState.EMPTY)

        init {
            viewModelScope.launchIO {
                backdropService.clearBackdrop()
            }
            seerrServerRepository.connection
                .onEach { user ->
                    state.update { it.copy(requests = DataLoadingState.Loading) }
                    if (user != null) {
                        val results =
                            fetchRequestGridItems(viewModelScope, seerrService, take = 100)
                                .filterAndGroupActiveRequests()
                        state.update { it.copy(requests = DataLoadingState.Success(results)) }
                    }
                }.catch { ex ->
                    Timber.e(ex, "Error fetching active requests")
                    state.update { it.copy(requests = DataLoadingState.Error(ex)) }
                }.launchIn(viewModelScope)
        }

        fun refresh() {
            viewModelScope.launchIO {
                try {
                    if (seerrServerRepository.connection.firstOrNull() != null) {
                        val results =
                            fetchRequestGridItems(viewModelScope, seerrService, take = 100)
                                .filterAndGroupActiveRequests()
                        state.update { it.copy(requests = DataLoadingState.Success(results)) }
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Error refreshing active requests")
                }
            }
        }

        fun updateBackdrop(item: DiscoverItem?) {
            viewModelScope.launchIO {
                if (item != null) {
                    backdropService.submit("discover_${item.id}", item.backDropUrl)
                } else {
                    backdropService.clearBackdrop()
                }
            }
        }
    }

@Composable
fun ActiveRequestsPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: ActiveRequestsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState(SeerrRequestsState.EMPTY)
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(TAB_ALL) }
    val tabFocusRequesters = remember { List(4) { FocusRequester() } }
    val contentFocusRequesters = remember { List(4) { FocusRequester() } }

    LaunchedEffect(Unit) {
        while (isActive) {
            delay(5_000L)
            viewModel.refresh()
        }
    }

    when (val requestsState = state.requests) {
        is DataLoadingState.Error -> {
            ErrorMessage(requestsState.message, requestsState.exception, modifier.focusable())
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier.focusable())
        }

        is DataLoadingState.Success<List<RequestGridItem>> -> {
            val items = requestsState.data
            val downloadingItems =
                remember(items) {
                    items.filter { it.item.isActivelyDownloading }
                }
            val releasedNotFoundItems =
                remember(items) {
                    items.filter { !it.item.isActivelyDownloading && !it.isFutureRelease }
                }
            val futureReleaseItems =
                remember(items) {
                    items
                        .filter { !it.item.isActivelyDownloading && it.isFutureRelease }
                        .sortedWith(
                            compareBy<RequestGridItem, LocalDate?>(nullsLast()) { it.nextReleaseDate }
                                .thenBy { it.item.title ?: "" },
                        )
                }
            val timelineEvents =
                remember(items) {
                    buildTimelineReleaseEvents(items)
                }

            val allTitle = "${stringResource(R.string.all_active_requests)} (${items.size})"
            val downloadingTitle = "${stringResource(R.string.actively_downloading)} (${downloadingItems.size})"
            val releasedTitle = "${stringResource(R.string.released_not_found)} (${releasedNotFoundItems.size})"
            val futureTitle = "${stringResource(R.string.future_releases)} (${futureReleaseItems.size})"

            val tabs =
                remember(allTitle, downloadingTitle, releasedTitle, futureTitle) {
                    listOf(
                        TabDetails(
                            title = StringStringProvider(allTitle),
                            tabFocusRequester = tabFocusRequesters[TAB_ALL],
                            contentFocusRequester = contentFocusRequesters[TAB_ALL],
                        ),
                        TabDetails(
                            title = StringStringProvider(downloadingTitle),
                            tabFocusRequester = tabFocusRequesters[TAB_DOWNLOADING],
                            contentFocusRequester = contentFocusRequesters[TAB_DOWNLOADING],
                        ),
                        TabDetails(
                            title = StringStringProvider(releasedTitle),
                            tabFocusRequester = tabFocusRequesters[TAB_RELEASED_NOT_FOUND],
                            contentFocusRequester = contentFocusRequesters[TAB_RELEASED_NOT_FOUND],
                        ),
                        TabDetails(
                            title = StringStringProvider(futureTitle),
                            tabFocusRequester = tabFocusRequesters[TAB_FUTURE_RELEASES],
                            contentFocusRequester = contentFocusRequesters[TAB_FUTURE_RELEASES],
                        ),
                    )
                }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier.fillMaxSize(),
            ) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    tabs = tabs,
                    onClick = { selectedTabIndex = it },
                    modifier =
                        Modifier
                            .padding(
                                top = 16.dp,
                                start = HeaderUtils.startPadding,
                                end = 184.dp,
                            ),
                )

                when (selectedTabIndex) {
                    TAB_ALL -> {
                        ActiveRequestsAllSectionsContent(
                            allItems = items,
                            downloadingItems = downloadingItems,
                            releasedNotFoundItems = releasedNotFoundItems,
                            futureReleaseItems = futureReleaseItems,
                            showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                            tabFocusRequester = tabFocusRequesters[TAB_ALL],
                            onFocusItem = { viewModel.updateBackdrop(it?.item) },
                            onClickItem = {
                                viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(it.item))
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    TAB_DOWNLOADING -> {
                        ActiveRequestsCategoryGridContent(
                            items = downloadingItems,
                            emptyMessage = stringResource(R.string.no_downloading_requests),
                            showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                            tabFocusRequester = tabFocusRequesters[TAB_DOWNLOADING],
                            context = context,
                            onFocusItem = { viewModel.updateBackdrop(it?.item) },
                            onClickItem = {
                                viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(it.item))
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    TAB_RELEASED_NOT_FOUND -> {
                        ActiveRequestsCategoryGridContent(
                            items = releasedNotFoundItems,
                            emptyMessage = stringResource(R.string.no_released_not_found_requests),
                            showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                            tabFocusRequester = tabFocusRequesters[TAB_RELEASED_NOT_FOUND],
                            context = context,
                            onFocusItem = { viewModel.updateBackdrop(it?.item) },
                            onClickItem = {
                                viewModel.navigationManager.navigateTo(Destination.DiscoveredItem(it.item))
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    TAB_FUTURE_RELEASES -> {
                        FutureReleasesTimelineContent(
                            events = timelineEvents,
                            showLogo = preferences.appPreferences.interfacePreferences.showLogos,
                            tabFocusRequester = tabFocusRequesters[TAB_FUTURE_RELEASES],
                            onFocusEvent = { viewModel.updateBackdrop(it?.requestItem?.item) },
                            onClickEvent = {
                                viewModel.navigationManager.navigateTo(
                                    Destination.DiscoveredItem(it.requestItem.item),
                                )
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveRequestsAllSectionsContent(
    allItems: List<RequestGridItem>,
    downloadingItems: List<RequestGridItem>,
    releasedNotFoundItems: List<RequestGridItem>,
    futureReleaseItems: List<RequestGridItem>,
    showLogo: Boolean,
    tabFocusRequester: FocusRequester,
    onFocusItem: (RequestGridItem?) -> Unit,
    onClickItem: (RequestGridItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val firstCardFocusRequester = remember { FocusRequester() }
    var focusedItem by remember(allItems) {
        mutableStateOf(
            downloadingItems.firstOrNull()
                ?: releasedNotFoundItems.firstOrNull()
                ?: futureReleaseItems.firstOrNull(),
        )
    }
    var didInitialFocus by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(focusedItem?.gridId) {
        onFocusItem(focusedItem)
    }

    LaunchedEffect(allItems.isNotEmpty()) {
        if (!didInitialFocus) {
            didInitialFocus = true
            if (allItems.isNotEmpty()) {
                firstCardFocusRequester.tryRequestFocus()
            } else {
                tabFocusRequester.tryRequestFocus()
            }
        }
    }

    if (allItems.isEmpty()) {
        EmptyActiveRequestsBox(
            message = stringResource(R.string.no_active_requests),
            modifier = modifier,
        )
        return
    }

    val firstNonEmptySection =
        when {
            downloadingItems.isNotEmpty() -> TAB_DOWNLOADING
            releasedNotFoundItems.isNotEmpty() -> TAB_RELEASED_NOT_FOUND
            else -> TAB_FUTURE_RELEASES
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        ActiveRequestHeader(
            requestItem = focusedItem,
            showLogo = showLogo,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = HeaderUtils.startPadding,
                        end = 32.dp,
                    ),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding =
                PaddingValues(
                    start = HeaderUtils.startPadding,
                    end = 32.dp,
                    bottom = 48.dp,
                ),
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRestorer(),
        ) {
            if (downloadingItems.isNotEmpty()) {
                item(key = "section_downloading") {
                    ActiveRequestsSectionRow(
                        title = stringResource(R.string.actively_downloading),
                        iconRes = R.string.fa_download,
                        accentColor = AppColors.Discover.Blue,
                        items = downloadingItems,
                        firstItemFocusRequester =
                            if (firstNonEmptySection == TAB_DOWNLOADING) firstCardFocusRequester else null,
                        context = context,
                        onFocusItem = { focusedItem = it },
                        onClickItem = onClickItem,
                    )
                }
            }

            if (releasedNotFoundItems.isNotEmpty()) {
                item(key = "section_released_not_found") {
                    ActiveRequestsSectionRow(
                        title = stringResource(R.string.released_not_found),
                        iconRes = R.string.fa_clock,
                        accentColor = AppColors.Discover.Yellow,
                        items = releasedNotFoundItems,
                        firstItemFocusRequester =
                            if (firstNonEmptySection == TAB_RELEASED_NOT_FOUND) firstCardFocusRequester else null,
                        context = context,
                        onFocusItem = { focusedItem = it },
                        onClickItem = onClickItem,
                    )
                }
            }

            if (futureReleaseItems.isNotEmpty()) {
                item(key = "section_future_releases") {
                    ActiveRequestsSectionRow(
                        title = stringResource(R.string.future_releases),
                        iconRes = R.string.fa_calendar_days,
                        accentColor = AppColors.Discover.Purple,
                        items = futureReleaseItems,
                        firstItemFocusRequester =
                            if (firstNonEmptySection == TAB_FUTURE_RELEASES) firstCardFocusRequester else null,
                        context = context,
                        onFocusItem = { focusedItem = it },
                        onClickItem = onClickItem,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveRequestsSectionRow(
    title: String,
    iconRes: Int,
    accentColor: Color,
    items: List<RequestGridItem>,
    firstItemFocusRequester: FocusRequester?,
    context: Context,
    onFocusItem: (RequestGridItem) -> Unit,
    onClickItem: (RequestGridItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(12.dp),
                ).border(
                    width = 1.dp,
                    color = accentColor.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                ).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(26.dp)
                        .background(accentColor.copy(alpha = 0.22f), CircleShape),
            ) {
                Text(
                    text = stringResource(iconRes),
                    fontFamily = FontAwesome,
                    fontSize = 12.sp,
                    color = accentColor,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = items.size.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier =
                    Modifier
                        .background(accentColor.copy(alpha = 0.8f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(items, key = { _, item -> item.gridId }) { index, reqItem ->
                val cardMod =
                    if (index == 0 && firstItemFocusRequester != null) {
                        Modifier.focusRequester(firstItemFocusRequester)
                    } else {
                        Modifier
                    }
                DiscoverItemCard(
                    item = reqItem.item,
                    onClick = { onClickItem(reqItem) },
                    onLongClick = {},
                    showOverlay = true,
                    subtitleOverride = reqItem.statusSubtitle(context),
                    modifier =
                        cardMod.onFocusChanged { state ->
                            if (state.isFocused) {
                                onFocusItem(reqItem)
                            }
                        },
                )
            }
        }
    }
}

@Composable
private fun ActiveRequestsCategoryGridContent(
    items: List<RequestGridItem>,
    emptyMessage: String,
    showLogo: Boolean,
    tabFocusRequester: FocusRequester,
    context: Context,
    onFocusItem: (RequestGridItem?) -> Unit,
    onClickItem: (RequestGridItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val columns = 6
    var focusedIndex by rememberSaveable { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }
    val focusedItem = items.getOrNull(focusedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)))

    LaunchedEffect(focusedItem?.gridId) {
        onFocusItem(focusedItem)
    }

    LaunchedEffect(items.isNotEmpty()) {
        if (items.isNotEmpty()) {
            focusRequester.tryRequestFocus()
        } else {
            tabFocusRequester.tryRequestFocus()
        }
    }

    if (items.isEmpty()) {
        EmptyActiveRequestsBox(
            message = emptyMessage,
            modifier = modifier,
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier,
    ) {
        ActiveRequestHeader(
            requestItem = focusedItem,
            showLogo = showLogo,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = HeaderUtils.startPadding,
                        end = 32.dp,
                    ),
        )
        CardGrid(
            pager = items,
            onClickItem = { _: Int, item: RequestGridItem ->
                onClickItem(item)
            },
            onLongClickItem = { _: Int, _: RequestGridItem ->
            },
            onClickPlay = { _, _ ->
            },
            letterPosition = { _: Char -> 0 },
            gridFocusRequester = focusRequester,
            showJumpButtons = false,
            showLetterButtons = false,
            spacing = 16.dp,
            cardContent = @Composable { (item, _, onClick, onLongClick, _, mod) ->
                DiscoverItemCard(
                    item = item?.item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    showOverlay = true,
                    subtitleOverride = item?.statusSubtitle(context),
                    modifier = mod,
                )
            },
            columns = columns,
            positionCallback = { _, position ->
                focusedIndex = position
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun FutureReleasesTimelineContent(
    events: List<TimelineReleaseEvent>,
    showLogo: Boolean,
    tabFocusRequester: FocusRequester,
    onFocusEvent: (TimelineReleaseEvent?) -> Unit,
    onClickEvent: (TimelineReleaseEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstRowFocusRequester = remember { FocusRequester() }
    var focusedEvent by remember(events) { mutableStateOf(events.firstOrNull()) }

    LaunchedEffect(focusedEvent?.id) {
        onFocusEvent(focusedEvent)
    }

    LaunchedEffect(events.isNotEmpty()) {
        if (events.isNotEmpty()) {
            firstRowFocusRequester.tryRequestFocus()
        } else {
            tabFocusRequester.tryRequestFocus()
        }
    }

    if (events.isEmpty()) {
        EmptyActiveRequestsBox(
            message = stringResource(R.string.no_future_releases),
            modifier = modifier,
        )
        return
    }

    val monthFormatter =
        remember {
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
        }
    val tbaTitle = stringResource(R.string.unreleased_tba)
    val groupedByMonth =
        remember(events, tbaTitle) {
            events.groupBy { event ->
                event.release.date?.let { YearMonth.from(it).format(monthFormatter) } ?: tbaTitle
            }
        }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier,
    ) {
        ActiveRequestHeader(
            requestItem = focusedEvent?.requestItem,
            focusedReleaseOverride = focusedEvent?.release,
            showLogo = showLogo,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        start = HeaderUtils.startPadding,
                        end = 32.dp,
                    ),
        )

        val firstEventId = events.firstOrNull()?.id

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding =
                PaddingValues(
                    start = HeaderUtils.startPadding,
                    end = 32.dp,
                    bottom = 48.dp,
                ),
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRestorer(),
        ) {
            groupedByMonth.forEach { (monthTitle, monthEvents) ->
                item(key = "month_header_$monthTitle") {
                    TimelineMonthHeader(
                        title = monthTitle,
                        count = monthEvents.size,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }

                itemsIndexed(monthEvents, key = { _, ev -> ev.id }) { idx, event ->
                    val isFirstOverall = event.id == firstEventId
                    val isLastInMonth = idx == monthEvents.lastIndex
                    TimelineReleaseRow(
                        event = event,
                        isLastInGroup = isLastInMonth,
                        onClick = { onClickEvent(event) },
                        onFocused = { focusedEvent = event },
                        modifier =
                            if (isFirstOverall) {
                                Modifier.focusRequester(firstRowFocusRequester)
                            } else {
                                Modifier
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineMonthHeader(
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.fa_calendar_days),
            fontFamily = FontAwesome,
            fontSize = 14.sp,
            color = AppColors.Discover.Purple,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier =
                Modifier
                    .background(AppColors.Discover.Purple.copy(alpha = 0.8f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)),
        )
    }
}

@Composable
private fun TimelineReleaseRow(
    event: TimelineReleaseEvent,
    isLastInGroup: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val item = event.requestItem.item
    val release = event.release
    val date = release.date

    LaunchedEffect(focused) {
        if (focused) {
            onFocused()
        }
    }

    val accentColor =
        when (item.type) {
            SeerrItemType.MOVIE -> AppColors.Discover.Blue
            SeerrItemType.TV -> AppColors.Discover.Purple
            else -> AppColors.Discover.Green
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
    ) {
        // Calendar date leaf
        CalendarDateBadge(
            date = date,
            accentColor = accentColor,
            highlighted = focused,
        )

        // Vertical timeline rail + node dot
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .width(16.dp)
                    .fillMaxHeight(),
        ) {
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(accentColor.copy(alpha = 0.35f)),
            )
            Box(
                modifier =
                    Modifier
                        .size(if (focused) 12.dp else 9.dp)
                        .background(
                            color = if (focused) Color.White else accentColor,
                            shape = CircleShape,
                        ).border(
                            width = 2.dp,
                            color = accentColor,
                            shape = CircleShape,
                        ),
            )
            Box(
                modifier =
                    Modifier
                        .width(2.dp)
                        .weight(1f)
                        .background(
                            if (isLastInGroup) Color.Transparent else accentColor.copy(alpha = 0.35f),
                        ),
            )
        }

        // Focusable timeline card
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        color =
                            if (focused) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                            },
                    ).border(
                        width = if (focused) 2.dp else 1.dp,
                        color =
                            if (focused) {
                                MaterialTheme.colorScheme.border
                            } else {
                                Color.White.copy(alpha = 0.1f)
                            },
                        shape = RoundedCornerShape(10.dp),
                    ).clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    ).padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(width = 46.dp, height = 68.dp)
                        .clip(RoundedCornerShape(6.dp)),
            ) {
                ItemCardImage(
                    imageUrl = item.posterUrl,
                    name = item.title,
                    showOverlay = false,
                    favorite = false,
                    watched = false,
                    unwatchedCount = 0,
                    watchedPercent = null,
                    numberOfVersions = -1,
                    useFallbackText = false,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.title ?: stringResource(R.string.unknown),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    val typeRes =
                        when (item.type) {
                            SeerrItemType.MOVIE -> R.plurals.movies
                            SeerrItemType.TV -> R.plurals.tv_shows
                            else -> null
                        }
                    typeRes?.let {
                        Text(
                            text = pluralStringResource(it, 1),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier =
                                Modifier
                                    .background(accentColor.copy(alpha = 0.85f), CircleShape)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    if (event.requestItem.request.is4k == true) {
                        Text(
                            text = stringResource(R.string.resolution_4k),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier =
                                Modifier
                                    .background(AppColors.Discover.Purple.copy(alpha = 0.85f), CircleShape)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }

                val epCode = formatSeasonEpisode(release.seasonNumber, release.episodeNumber, padded = true)
                val scheduleTitle =
                    when {
                        epCode != null && release.episodeTitle.isNotNullOrBlank() ->
                            "$epCode \u2022 \"${release.episodeTitle}\""
                        epCode != null -> epCode
                        release.seasonNumber != null -> formatSeasonNumber(release.seasonNumber)
                        release.label.equals("Digital", ignoreCase = true) ->
                            stringResource(R.string.digital_release)
                        item.type == SeerrItemType.MOVIE ->
                            listOfNotNull(
                                stringResource(R.string.movie_release),
                                release.label?.takeIf { !it.equals("Released", ignoreCase = true) },
                            ).joinToString(" \u2022 ")
                        else -> release.label ?: stringResource(R.string.future_releases)
                    }
                val dateLabel = date?.let { formatDate(it) } ?: stringResource(R.string.date_tba)

                Text(
                    text = "$scheduleTitle  \u2022  $dateLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val overviewText =
                    release.overview?.takeIf { it.isNotNullOrBlank() }
                        ?: item.overview?.takeIf { it.isNotNullOrBlank() }
                if (overviewText != null) {
                    Text(
                        text = overviewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            val countdownText = formatRelativeReleaseCountdown(context, date)
            val daysUntil = date?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).toInt() }
            val countdownBg =
                when {
                    daysUntil == null -> Color.DarkGray.copy(alpha = 0.7f)
                    daysUntil <= 1 -> AppColors.Discover.Green.copy(alpha = 0.85f)
                    daysUntil <= 7 -> AppColors.Discover.Blue.copy(alpha = 0.85f)
                    else -> accentColor.copy(alpha = 0.75f)
                }

            Text(
                text = countdownText,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier =
                    Modifier
                        .background(countdownBg, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CalendarDateBadge(
    date: LocalDate?,
    accentColor: Color,
    highlighted: Boolean,
    modifier: Modifier = Modifier,
) {
    val locale = Locale.getDefault()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .width(64.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(
                        alpha = if (highlighted) 0.9f else 0.55f,
                    ),
                ).border(
                    width = if (highlighted) 1.5.dp else 1.dp,
                    color = if (highlighted) accentColor else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(8.dp),
                ),
    ) {
        val monthShort =
            date?.month?.getDisplayName(TextStyle.SHORT, locale)?.uppercase(locale)
                ?: stringResource(R.string.date_tba)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(accentColor.copy(alpha = 0.85f))
                    .padding(vertical = 2.dp),
        ) {
            Text(
                text = monthShort,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
        if (date != null) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
            val dowShort = date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).uppercase(locale)
            Text(
                text = dowShort,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        } else {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun EmptyActiveRequestsBox(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ActiveRequestHeader(
    requestItem: RequestGridItem?,
    showLogo: Boolean,
    modifier: Modifier = Modifier,
    focusedReleaseOverride: ScheduledRelease? = null,
) {
    if (requestItem == null) return
    val item = requestItem.item
    val request = requestItem.request
    val requestedBy =
        request.requestedBy?.let {
            it.username?.takeIf { u -> u.isNotNullOrBlank() }
                ?: it.plexUsername?.takeIf { p -> p.isNotNullOrBlank() }
                ?: it.email?.takeIf { e -> e.isNotNullOrBlank() }
        }
    val nextRelease = focusedReleaseOverride ?: requestItem.nextScheduledRelease
    val releaseDateToShow = nextRelease?.date ?: requestItem.nextReleaseDate ?: item.releaseDate

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        TitleOrLogo(
            title = item.title,
            logoImageUrl = item.logoUrl,
            showLogo = showLogo,
            modifier = Modifier.fillMaxWidth(0.75f),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val typeTextRes =
                when (item.type) {
                    SeerrItemType.MOVIE -> R.plurals.movies
                    SeerrItemType.TV -> R.plurals.tv_shows
                    SeerrItemType.PERSON -> R.plurals.people
                    SeerrItemType.UNKNOWN -> null
                }
            val typeColor =
                when (item.type) {
                    SeerrItemType.MOVIE -> AppColors.Discover.Blue
                    SeerrItemType.TV -> AppColors.Discover.Purple
                    SeerrItemType.PERSON -> AppColors.Discover.Green
                    SeerrItemType.UNKNOWN -> Color.DarkGray
                }
            typeTextRes?.let {
                Text(
                    text = pluralStringResource(it, 1),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier =
                        Modifier
                            .background(typeColor.copy(alpha = 0.85f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (request.is4k == true) {
                Text(
                    text = stringResource(R.string.resolution_4k),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier =
                        Modifier
                            .background(AppColors.Discover.Purple.copy(alpha = 0.85f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            if (requestItem.isFutureRelease) {
                val releaseBadgeText =
                    if (releaseDateToShow != null && releaseDateToShow.isAfter(LocalDate.now().minusDays(1))) {
                        stringResource(R.string.releases_on, formatDate(releaseDateToShow))
                    } else {
                        stringResource(R.string.unreleased_tba)
                    }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier =
                        Modifier
                            .background(AppColors.Discover.Purple.copy(alpha = 0.85f), CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.fa_calendar_days),
                        fontFamily = FontAwesome,
                        fontSize = 10.sp,
                        color = Color.White,
                    )
                    Text(
                        text = releaseBadgeText,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
            val metaParts =
                buildList {
                    if (!requestItem.isFutureRelease) {
                        item.releaseDate?.let { add(formatDate(it)) }
                    }
                    if (requestItem.requestedSeasons.isNotEmpty()) {
                        if (requestItem.requestedSeasons.size == 1) {
                            add(formatSeasonNumber(requestItem.requestedSeasons.first()))
                        } else {
                            add("${stringResource(R.string.tv_seasons)}: ${requestItem.requestedSeasons.joinToString(", ")}")
                        }
                    }
                    if (requestedBy != null) {
                        add(stringResource(R.string.requested_by, requestedBy))
                    }
                }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString("  \u2022  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        SeerrRequestProgressSection(
            item = item,
            requestStatus = requestItem.requestStatus,
            scheduledReleases =
                if (focusedReleaseOverride != null) {
                    listOf(focusedReleaseOverride) +
                        requestItem.scheduledReleases.filterNot { it == focusedReleaseOverride }
                } else {
                    requestItem.scheduledReleases
                },
            isFutureRelease = requestItem.isFutureRelease,
            modifier = Modifier.fillMaxWidth(0.65f),
        )

        val overviewToDisplay =
            focusedReleaseOverride?.overview?.takeIf { it.isNotNullOrBlank() }
                ?: item.overview?.takeIf { it.isNotNullOrBlank() }
        overviewToDisplay?.let { overview ->
            Text(
                text = overview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(0.65f),
            )
        }
    }
}

@Composable
fun SeerrRequestProgressSection(
    item: DiscoverItem,
    requestStatus: RequestStatus = RequestStatus.UNKNOWN,
    scheduledReleases: List<ScheduledRelease> = emptyList(),
    isFutureRelease: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (item.isActivelyDownloading) {
        SeerrDownloadProgressBanner(
            downloads = item.downloads,
            overallProgress = item.downloadProgress,
            modifier = modifier,
        )
    } else if (isFutureRelease) {
        val primaryRelease = scheduledReleases.firstOrNull { it.date != null } ?: scheduledReleases.firstOrNull()
        val releaseDate = primaryRelease?.date ?: item.releaseDate?.takeIf { it.isAfter(LocalDate.now()) }
        val epCode = formatSeasonEpisode(primaryRelease?.seasonNumber, primaryRelease?.episodeNumber, padded = true)
        val epLabel =
            when {
                epCode != null && primaryRelease?.episodeTitle.isNotNullOrBlank() ->
                    "$epCode \"${primaryRelease?.episodeTitle}\""
                epCode != null -> epCode
                primaryRelease?.seasonNumber != null -> formatSeasonNumber(primaryRelease.seasonNumber)
                else -> null
            }
        val headline =
            when {
                epLabel != null && releaseDate != null ->
                    stringResource(
                        R.string.next_episode_release,
                        epLabel,
                        "${formatDate(releaseDate)} (${formatRelativeReleaseCountdown(context, releaseDate)})",
                    )
                releaseDate != null ->
                    "${stringResource(R.string.scheduled_release, formatDate(releaseDate))}  \u2022  ${formatRelativeReleaseCountdown(context, releaseDate)}"
                else -> stringResource(R.string.unreleased_tba)
            }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                    ).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.fa_calendar_days),
                    fontFamily = FontAwesome,
                    fontSize = 12.sp,
                    color = AppColors.Discover.Purple,
                )
                Text(
                    text = headline,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (scheduledReleases.size > 1) {
                val upcomingPills =
                    scheduledReleases.drop(1).take(3).mapNotNull { rel ->
                        val code = formatSeasonEpisode(rel.seasonNumber, rel.episodeNumber, padded = true)
                        val dt = rel.date?.let { formatDate(it) }
                        if (code != null && dt != null) "$code \u2022 $dt" else dt
                    }
                if (upcomingPills.isNotEmpty()) {
                    Text(
                        text = upcomingPills.joinToString("     "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    } else {
        val isPending =
            requestStatus == RequestStatus.PENDING ||
                item.availability == SeerrAvailability.PENDING
        val statusLabel =
            when {
                isPending -> stringResource(R.string.pending)
                item.availability == SeerrAvailability.PARTIALLY_AVAILABLE ->
                    "${stringResource(R.string.partially_available)}  \u2022  ${stringResource(R.string.released_not_found)}"
                else -> stringResource(R.string.released_searching)
            }
        val accentColor =
            when {
                isPending -> AppColors.Discover.Yellow
                item.availability == SeerrAvailability.PARTIALLY_AVAILABLE -> AppColors.Discover.Green
                else -> AppColors.Discover.Yellow
            }

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                modifier
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp),
                    ).padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(if (isPending) R.string.fa_bell else R.string.fa_clock),
                    fontFamily = FontAwesome,
                    fontSize = 12.sp,
                    color = accentColor,
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.15f)),
            ) {
                val indicatorFraction =
                    when {
                        isPending -> 0.15f
                        item.availability == SeerrAvailability.PARTIALLY_AVAILABLE -> 0.5f
                        else -> 0.3f
                    }
                Box(
                    modifier =
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(indicatorFraction)
                            .background(accentColor.copy(alpha = 0.85f)),
                )
            }
        }
    }
}

@Composable
fun SeerrDownloadProgressBanner(
    downloads: List<SeerrDownloadItem>,
    overallProgress: Double? = downloads.overallProgressPercent(),
    modifier: Modifier = Modifier,
) {
    val pct = (overallProgress ?: 0.0).coerceIn(0.0, 100.0)
    val animatedFraction by animateFloatAsState(
        targetValue = (pct / 100.0).toFloat().coerceIn(0.02f, 1f),
        label = "seerrDownloadProgress",
    )
    val totalSize = downloads.sumOf { it.size.coerceAtLeast(0L) }
    val downloadedSize = downloads.sumOf { it.downloadedBytes }
    val firstTimeLeft = downloads.firstNotNullOfOrNull { it.timeLeft }

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .widthIn(max = 640.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(8.dp),
                ).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.fa_download),
                    fontFamily = FontAwesome,
                    fontSize = 12.sp,
                    color = AppColors.Discover.Blue,
                )
                Text(
                    text =
                        if (pct > 0.0) {
                            stringResource(R.string.downloading_progress, pct.roundToInt())
                        } else {
                            stringResource(R.string.queued)
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            val rightDetails =
                buildList {
                    if (totalSize > 0L) {
                        add("${formatBytes(downloadedSize)} / ${formatBytes(totalSize)}")
                    }
                    if (firstTimeLeft != null) {
                        add(stringResource(R.string.time_left, firstTimeLeft))
                    }
                }
            if (rightDetails.isNotEmpty()) {
                Text(
                    text = rightDetails.joinToString("  \u2022  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.White.copy(alpha = 0.18f)),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedFraction)
                        .background(MaterialTheme.colorScheme.tertiary),
            )
        }

        if (downloads.size > 1 || downloads.any { it.seasonNumber != null && it.episodeNumber != null }) {
            downloads.take(2).forEach { dl ->
                val epLabel = formatSeasonEpisode(dl.seasonNumber, dl.episodeNumber, padded = true)
                val label =
                    listOfNotNull(
                        if (dl.is4k) "4K" else null,
                        epLabel,
                        dl.title?.takeIf { it.isNotNullOrBlank() },
                    ).joinToString(" \u2022 ")
                val itemPct = dl.progressPercent?.roundToInt()
                val itemRight =
                    listOfNotNull(
                        itemPct?.let { "$it%" },
                        dl.timeLeft,
                    ).joinToString(" \u2022 ")
                if (label.isNotBlank()) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (itemRight.isNotBlank()) {
                            Text(
                                text = itemRight,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
