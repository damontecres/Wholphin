package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class ScreensaverViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val imageUrlService: ImageUrlService,
        val preferencesDataStore: DataStore<AppPreferences>,
    ) : ViewModel() {
        val currentItem = MutableStateFlow<CurrentItem?>(null)

        private var job: Job? = null

        init {
            addCloseable { stop() }
        }

        fun init() {
            job =
                viewModelScope.launchIO {
                    val request =
                        GetItemsRequest(
                            recursive = true,
                            includeItemTypes = listOf(BaseItemKind.MOVIE, BaseItemKind.SERIES),
                            imageTypes = listOf(ImageType.BACKDROP),
                            sortBy = listOf(ItemSortBy.RANDOM),
                        )
                    val pager = ApiRequestPager(api, request, GetItemsRequestHandler, viewModelScope).init()
                    var index = 0
                    while (isActive) {
                        val item = pager.getBlocking(index)
                        Timber.v("Next index=%s, item=%s", index, item?.id)
                        if (item != null) {
                            val backdropUrl = imageUrlService.getItemImageUrl(item, ImageType.BACKDROP)
                            val logoUrl = imageUrlService.getItemImageUrl(item, ImageType.LOGO)
                            if (backdropUrl != null) {
                                currentItem.value = CurrentItem(item, backdropUrl, logoUrl, item.title ?: "")
                                delay(5.seconds)
                            }
                        }
                        index++
                    }
                }
        }

        fun stop() {
            Timber.v("Stopping")
            job?.cancel()
        }
    }

data class CurrentItem(
    val item: BaseItem,
    val backdropUrl: String,
    val logoUrl: String?,
    val title: String,
)

@Composable
fun AppScreensaver(
    modifier: Modifier = Modifier,
    viewModel: ScreensaverViewModel = hiltViewModel(),
) {
    LifecycleStartEffect(Unit) {
        viewModel.init()
        onStopOrDispose {
            viewModel.stop()
        }
    }
    val prefs by viewModel.preferencesDataStore.data.collectAsState(AppPreferences.getDefaultInstance())
    val currentItem by viewModel.currentItem.collectAsState()
    Box(
        modifier
            .background(Color.Black),
    ) {
        currentItem?.let { currentItem ->
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(currentItem.backdropUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )

            var logoError by remember(currentItem) { mutableStateOf(false) }
            if (!logoError) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(currentItem.logoUrl)
                            .crossfade(true)
                            .build(),
                    contentDescription = "Logo",
                    alignment = Alignment.BottomStart,
                    onError = {
                        logoError = true
                    },
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .size(width = 240.dp, height = 120.dp)
                            .padding(16.dp),
                )
            } else {
                Text(
                    text = currentItem.title,
                    modifier =
                        Modifier
                            .align(Alignment.BottomStart)
                            .size(width = 240.dp, height = 120.dp)
                            .padding(16.dp),
                )
            }
        }
        if (prefs.interfacePreferences.showClock) {
            TimeDisplay()
        }
    }
}
