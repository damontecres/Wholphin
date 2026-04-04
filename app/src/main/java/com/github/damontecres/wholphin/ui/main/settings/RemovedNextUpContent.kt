package com.github.damontecres.wholphin.ui.main.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.ui.components.DeleteButton
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.formatDateTime
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class RemovedNextUpContentViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val latestNextUpService: LatestNextUpService,
        private val imageUrlService: ImageUrlService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(RemovedNextUpState())
        val state: StateFlow<RemovedNextUpState> = _state

        init {
            viewModelScope.launchDefault {
                serverRepository.currentUser.asFlow().collectLatest { user ->
                    _state.update { RemovedNextUpState() }
                    if (user == null) {
                        return@collectLatest
                    }
                    try {
                        val removed = latestNextUpService.getRemovedFromNextUp(user.id)
                        val series = mutableListOf<RemovedItem>()
                        removed.keys.chunked(50).forEach { ids ->
                            val results =
                                api.itemsApi
                                    .getItems(
                                        userId = user.id,
                                        ids = ids,
                                    ).toBaseItems(api, false)
                            results.forEach {
                                val imageUrl = imageUrlService.getItemImageUrl(it, ImageType.PRIMARY)
                                series.add(RemovedItem(it, imageUrl, removed[it.id]!!))
                            }
                        }
                        _state.update { it.copy(loading = DataLoadingState.Success(series)) }
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error fetching removed series")
                        _state.update { it.copy(loading = DataLoadingState.Error(ex)) }
                    }
                }
            }
        }
    }

@Stable
data class RemovedItem(
    val series: BaseItem,
    val imageUrl: String?,
    val datetime: LocalDateTime,
)

data class RemovedNextUpState(
    val loading: DataLoadingState<List<RemovedItem>> = DataLoadingState.Pending,
    val mutating: Boolean = false,
)

@Composable
fun RemovedNextUpContent(
    modifier: Modifier = Modifier,
    viewModel: RemovedNextUpContentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    Column(
        modifier = modifier,
    ) {
        Text(
            text = "Removed from next up",
            style = MaterialTheme.typography.displaySmall,
        )
        when (val s = state.loading) {
            DataLoadingState.Pending,
            DataLoadingState.Loading,
            -> {
                LoadingPage(Modifier.fillMaxSize())
            }

            is DataLoadingState.Error -> {
                ErrorMessage(s, Modifier.fillMaxSize())
            }

            is DataLoadingState.Success<List<RemovedItem>> -> {
                if (s.data.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_results),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(s.data, key = { it.series.id }) { item ->
                            RemovedListItem(
                                item = item,
                                onClickRemove = {},
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RemovedListItem(
    item: RemovedItem,
    onClickRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
    ) {
        ListItem(
            selected = false,
            onClick = {},
            leadingContent = {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.series.title,
                )
            },
            headlineContent = {
                Text(
                    text = item.series.title ?: item.series.id.toString(),
                )
            },
            supportingContent = {
                Text(
                    text = formatDateTime(item.datetime),
                )
            },
            modifier = Modifier,
        )
        DeleteButton(
            onClick = onClickRemove,
        )
    }
}
