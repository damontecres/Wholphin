package com.github.damontecres.wholphin.ui.detail.livetv

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.seasonEpisode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class DvrScheduleViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val loading = MutableLiveData<LoadingState>(LoadingState.Loading)
        val active = MutableLiveData<List<BaseItem>>()
        val scheduled = MutableLiveData<Map<LocalDate, List<BaseItem>>>()

        fun init() {
//            loading.value = LoadingState.Loading
            viewModelScope.launchIO(LoadingExceptionHandler(loading, "Error fetching DVR Schedule")) {
                val active =
                    api.liveTvApi
                        .getRecordings(
                            isInProgress = true,
                            fields = SlimItemFields,
                            limit = 100,
                        ).content.items
                        .map { BaseItem.from(it, api, true) }
                val scheduled =
                    api.liveTvApi
                        .getTimers(
                            isActive = false,
                            isScheduled = true,
                        ).content.items
                        .map { BaseItem.from(it.programInfo!!, api, true) } // TODO this probably breaks for time based recordings
                        .groupBy {
                            it.data.startDate!!.toLocalDate()
                        }

                withContext(Dispatchers.Main) {
                    this@DvrScheduleViewModel.active.value = active
                    this@DvrScheduleViewModel.scheduled.value = scheduled
                    loading.value = LoadingState.Success
                }
            }
        }

        fun cancelRecording(
            timerId: String,
            series: Boolean,
        ) {
            viewModelScope.launchIO(ExceptionHandler(autoToast = true)) {
                if (series) {
                    api.liveTvApi.cancelSeriesTimer(timerId)
                } else {
                    api.liveTvApi.cancelTimer(timerId)
                }
                init()
            }
        }
    }

@Composable
fun DvrSchedule(
    requestFocusAfterLoading: Boolean,
    modifier: Modifier = Modifier,
    viewModel: DvrScheduleViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.init()
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)
    val active by viewModel.active.observeAsState(listOf())
    val recordings by viewModel.scheduled.observeAsState(mapOf())
    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state, modifier)

        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage(modifier)

        LoadingState.Success -> {
            var showDialog by remember { mutableStateOf<BaseItem?>(null) }
            val focusRequester = remember { FocusRequester() }
            if (requestFocusAfterLoading) {
                LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
            }
            DvrScheduleContent(
                activeRecordings = active,
                scheduledRecordings = recordings,
                onClickItem = {
                    showDialog = it
                },
                modifier =
                    modifier
                        .focusRequester(focusRequester),
            )
            showDialog?.let { item ->
                ProgramDialog(
                    item = item,
                    canRecord = true,
                    loading = LoadingState.Success,
                    onDismissRequest = {
                        showDialog = null
                    },
                    onWatch = {
                        item.data.channelId?.let {
                            viewModel.navigationManager.navigateTo(
                                Destination.Playback(
                                    itemId = it,
                                    positionMs = 0L,
                                ),
                            )
                        }
                    },
                    onRecord = {
                        // no-op
                    },
                    onCancelRecord = { series ->
                        showDialog = null
                        val timerId = if (series) item.data.seriesTimerId else item.data.timerId
                        if (timerId != null) {
                            viewModel.cancelRecording(timerId, series)
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun DvrScheduleContent(
    activeRecordings: List<BaseItem>,
    scheduledRecordings: Map<LocalDate, List<BaseItem>>,
    onClickItem: (BaseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth(.6f)
                    .background(MaterialTheme.colorScheme.surface),
        ) {
            if (activeRecordings.isEmpty() && scheduledRecordings.isEmpty()) {
                item {
                    Text(
                        text = "No scheduled recordings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (activeRecordings.isNotEmpty()) {
                item {
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                items(activeRecordings) { item ->
                    Recording(
                        item = item,
                        onClick = { onClickItem.invoke(item) },
                        modifier = Modifier,
                    )
                }
            }
            scheduledRecordings.keys.sorted().forEach { date ->
                val formattedDate =
                    DateUtils.formatDateTime(
                        context,
                        date
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .epochSecond * 1000,
                        DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE,
                    )
                item {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                val programs = scheduledRecordings[date].orEmpty()
                items(programs) { item ->
                    Recording(
                        item = item,
                        onClick = { onClickItem.invoke(item) },
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

@Composable
fun Recording(
    item: BaseItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    ListItem(
        selected = false,
        onClick = onClick,
        modifier = modifier,
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
            ),
        leadingContent = {
            // TODO
//                        AsyncImage(
//                            model = item.imageUrl,
//                            contentDescription = null,
//                        )
        },
        headlineContent = {
            Text(
                text = item.title ?: "",
            )
        },
        supportingContent = {
            if (item.data.isSeries ?: false) {
                listOfNotNull(
                    item.data.seasonEpisode,
                    item.data.episodeTitle,
                ).joinToString(" - ")
                    .ifBlank { null }
                    ?.let {
                        Text(
                            text = it,
                            modifier = Modifier,
                        )
                    }
            }
        },
        trailingContent = {
            val time =
                DateUtils.formatDateRange(
                    context,
                    item.data.startDate!!
                        .toInstant(OffsetDateTime.now().offset)
                        .epochSecond * 1000,
                    item.data.endDate!!
                        .toInstant(OffsetDateTime.now().offset)
                        .epochSecond * 1000,
                    DateUtils.FORMAT_SHOW_TIME,
                )
            Text(
                text = time,
            )
        },
    )
}
