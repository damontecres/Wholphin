package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.CollectionFolderView
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopupContent
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.components.ViewOptions
import com.github.damontecres.wholphin.ui.data.VideoSortOptions
import com.github.damontecres.wholphin.ui.detail.livetv.DvrSchedule
import com.github.damontecres.wholphin.ui.detail.livetv.TvGuideGrid
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.liveTvApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.RecordingStatus
import org.jellyfin.sdk.model.api.TimerInfoDto
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LiveTvCollectionViewModel
    @Inject
    constructor(
        val api: ApiClient,
        val serverRepository: ServerRepository,
        val navigationManager: NavigationManager,
        val backdropService: BackdropService,
    ) : ViewModel() {
        val state = MutableStateFlow(LiveTvRecordingFolderState())

        init {
            viewModelScope.launchIO {
                try {
                    val folders =
                        api.liveTvApi
                            .getRecordingFolders(userId = serverRepository.currentUser?.id)
                            .content.items
                            .map { TabId(it.name ?: "Recordings", it.id) }
                    state.value =
                        LiveTvRecordingFolderState(DataLoadingState.Success(folders))
                } catch (ex: CancellationException) {
                    throw ex
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching recording folders")
                    state.value =
                        LiveTvRecordingFolderState(DataLoadingState.Error(ex))
                }
            }
        }

        fun lookupTimerFor(item: BaseItem) {
            viewModelScope.launchIO {
                val timerId = item.data.timerId
                if (timerId != null) {
                    state.update { it.copy(recordingLookup = DataLoadingState.Loading) }
                    try {
                        val timer by api.liveTvApi.getTimer(timerId)
                        state.update { it.copy(recordingLookup = DataLoadingState.Success(TimerInfo(item, timer))) }
                    } catch (ex: Exception) {
                        state.update { it.copy(recordingLookup = DataLoadingState.Error(ex)) }
                    }
                } else {
                    state.update { it.copy(recordingLookup = DataLoadingState.Error("No timer for recording")) }
                }
            }
        }

        fun clearTimer() {
            state.update { it.copy(recordingLookup = DataLoadingState.Pending) }
        }
    }

data class LiveTvRecordingFolderState(
    val folders: DataLoadingState<List<TabId>> = DataLoadingState.Pending,
    val recordingLookup: DataLoadingState<TimerInfo> = DataLoadingState.Pending,
)

data class TabId(
    val title: String,
    val id: UUID,
)

data class TimerInfo(
    val item: BaseItem,
    val timerInfo: TimerInfoDto,
)

val BaseItem.isInProgressRecording: Boolean get() = type == BaseItemKind.RECORDING && data.status == RecordingStatus.IN_PROGRESS.serialName

@Composable
fun CollectionFolderLiveTv(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: LiveTvCollectionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    when (val st = state.folders) {
        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier)
        }

        DataLoadingState.Loading,
        DataLoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        is DataLoadingState.Success<List<TabId>> -> {
            val tabs =
                remember {
                    listOf(
                        TabDetails(R.string.tv_guide),
                        TabDetails(R.string.tv_dvr_schedule),
                    ) +
                        st.data.map {
                            TabDetails(StringStringProvider(it.title))
                        }
                }
            val onClickItem =
                remember {
                    { position: Int, item: BaseItem ->
                        if (item.isInProgressRecording) {
                            viewModel.lookupTimerFor(item)
                        } else {
                            viewModel.navigationManager.navigateTo(item.destination())
                        }
                    }
                }

            var showHeader by rememberSaveable { mutableStateOf(true) }
            TabbedPage(
                itemId = destination.itemId.toString(),
                tabs = tabs,
                modifier = modifier,
                showTabs = showHeader,
            ) { tabIndex, tabDetails ->
                when (tabIndex) {
                    0 -> {
                        TvGuideGrid(
                            preferences = preferences,
                            onRowPosition = {
                                showHeader = it <= 0
                            },
                            Modifier
                                .fillMaxSize()
                                .focusRequester(tabDetails.contentFocusRequester),
                        )
                    }

                    1 -> {
                        DvrSchedule(
                            requestFocusAfterLoading = true,
                            focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .focusRequester(tabDetails.contentFocusRequester),
                        )
                    }

                    else -> {
                        val folderIndex = tabIndex - 2
                        if (folderIndex in st.data.indices) {
                            CollectionFolderView(
                                preferences = preferences,
                                onClickItem = onClickItem,
                                itemId = st.data[folderIndex].id,
                                initialFilter = CollectionFolderFilter(),
                                showTitle = false,
                                recursive = false,
                                sortOptions = VideoSortOptions,
                                modifier = Modifier.focusRequester(tabDetails.contentFocusRequester),
                                positionCallback = { columns, position ->
                                    showHeader = position < columns
                                },
                                playEnabled = false,
                                defaultViewOptions = ViewOptions(),
                                focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                            )
                        } else {
                            ErrorMessage("Invalid tab index $tabIndex", null)
                        }
                    }
                }
            }
        }
    }
    WatchFromStartDialog(
        timer = state.recordingLookup,
        onDismissRequest = { viewModel.clearTimer() },
        onClickPlay = { id ->
            viewModel.navigationManager.navigateTo(Destination.Playback(id, 0))
        },
    )
}

@Composable
fun WatchFromStartDialog(
    timer: DataLoadingState<TimerInfo>,
    onDismissRequest: () -> Unit,
    onClickPlay: (UUID) -> Unit,
) {
    when (timer) {
        DataLoadingState.Pending -> {
        }

        else -> {
            BasicDialog(
                onDismissRequest = onDismissRequest,
            ) {
                val focusRequester = remember { FocusRequester() }
                val modifier = Modifier.padding(24.dp)
                when (val t = timer) {
                    is DataLoadingState.Error -> {
                        ErrorMessage(t, modifier)
                    }

                    DataLoadingState.Loading -> {
                        Box(modifier) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.border,
                                modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .size(48.dp),
                            )
                        }
                    }

                    is DataLoadingState.Success<TimerInfo> -> {
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                        DialogPopupContent(
                            title = t.data.item.title ?: "",
                            waiting = false,
                            onDismissRequest = onDismissRequest,
                            dialogItems =
                                buildList {
                                    add(
                                        DialogItem(
                                            text = R.string.play_from_start,
                                            iconStringRes = R.string.fa_rotate_left,
                                            onClick = { onClickPlay.invoke(t.data.item.id) },
                                        ),
                                    )
                                    val channelId = t.data.timerInfo.channelId
                                    if (channelId != null) {
                                        add(
                                            DialogItem(
                                                text = R.string.watch_live,
                                                iconStringRes = R.string.fa_play,
                                                onClick = { onClickPlay.invoke(channelId) },
                                            ),
                                        )
                                    }
                                },
                            modifier = modifier.focusRequester(focusRequester),
                        )
                    }
                }
            }
        }
    }
}
