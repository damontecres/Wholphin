package com.github.damontecres.dolphin.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.DefaultItemFields
import com.github.damontecres.dolphin.ui.cards.ItemRow
import com.github.damontecres.dolphin.ui.components.DotSeparatedRow
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.roundMinutes
import com.github.damontecres.dolphin.ui.timeRemaining
import com.github.damontecres.dolphin.util.formatDateTime
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import org.jellyfin.sdk.api.client.extensions.itemsApi
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.request.GetLatestMediaRequest
import org.jellyfin.sdk.model.api.request.GetNextUpRequest
import org.jellyfin.sdk.model.api.request.GetResumeItemsRequest
import org.jellyfin.sdk.model.extensions.ticks
import timber.log.Timber
import javax.inject.Inject

data class HomeRow(
    val section: HomeSection,
    val items: List<BaseItem>,
)

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        val api: ApiClient,
    ) : ViewModel() {
        val homeRows = MutableLiveData<List<HomeRow>>()

        init {
            viewModelScope.launch(Dispatchers.IO) {
                val user = api.userApi.getCurrentUser().content
                val displayPrefs =
                    api.displayPreferencesApi
                        .getDisplayPreferences(
                            displayPreferencesId = "usersettings",
                            client = "emby",
                        ).content
                val homeSections =
                    displayPrefs.customPrefs.entries
                        .filter { it.key.startsWith("homesection") && it.value.isNotNullOrBlank() }
                        .sortedBy { it.key }
                        .map { HomeSection.fromKey(it.value ?: "") }
                        .filterNot { it == HomeSection.NONE }

                val homeRows =
                    homeSections
                        .mapNotNull { section ->
                            Timber.v("Loading section: %s", section.name)
                            when (section) {
                                HomeSection.LATEST_MEDIA -> {
                                    user.configuration?.orderedViews?.firstOrNull()?.let { viewId ->
                                        val request =
                                            GetLatestMediaRequest(
                                                fields = DefaultItemFields,
                                                imageTypeLimit = 1,
                                                parentId = viewId,
                                                groupItems = true,
                                                limit = 25,
                                            )
                                        val latest =
                                            api.userLibraryApi
                                                .getLatestMedia(request)
                                                .content
                                                .map { BaseItem.from(it, api) }
                                        HomeRow(
                                            section = section,
                                            items = latest,
                                        )
                                    }
                                }

                                // TODO
                                HomeSection.LIBRARY_TILES_SMALL -> null
                                HomeSection.RESUME -> {
                                    val request =
                                        GetResumeItemsRequest(
                                            userId = user.id,
                                            fields = DefaultItemFields,
                                            // TODO, more params?
                                        )
                                    val items =
                                        api.itemsApi
                                            .getResumeItems(request)
                                            .content
                                            .items
                                            .map { BaseItem.from(it, api) }
                                    HomeRow(
                                        section = section,
                                        items = items,
                                    )
                                }
                                HomeSection.ACTIVE_RECORDINGS -> null
                                HomeSection.NEXT_UP -> {
                                    val request =
                                        GetNextUpRequest(
                                            fields = DefaultItemFields,
                                            imageTypeLimit = 1,
                                            parentId = null,
                                            limit = 25,
                                            enableResumable = false,
                                        )
                                    val nextUp =
                                        api.tvShowsApi
                                            .getNextUp(request)
                                            .content
                                            .items
                                            .map { BaseItem.from(it, api) }
                                    HomeRow(
                                        section = section,
                                        items = nextUp,
                                    )
                                }
                                HomeSection.LIVE_TV -> null

                                // TODO Not supported?
                                HomeSection.LIBRARY_BUTTONS -> null
                                HomeSection.RESUME_AUDIO -> null
                                HomeSection.RESUME_BOOK -> null
                                HomeSection.NONE -> null
                            }
                        }.filter { it.items.isNotEmpty() }
                withContext(Dispatchers.Main) {
                    this@MainViewModel.homeRows.value = homeRows
                }
            }
        }
    }

@Composable
fun MainPage(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    modifier: Modifier,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val homeRows by viewModel.homeRows.observeAsState(listOf())
    var focusedItem by remember { mutableStateOf<BaseItem?>(null) }
    Box(modifier = modifier) {
        if (focusedItem?.backdropImageUrl.isNotNullOrBlank()) {
            val gradientColor = MaterialTheme.colorScheme.background
            AsyncImage(
                model = focusedItem?.backdropImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.TopEnd,
                modifier =
                    Modifier
                        .fillMaxHeight(.7f)
                        .fillMaxWidth(.7f)
                        .alpha(.4f)
                        .align(Alignment.TopEnd)
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, gradientColor),
                                    startY = size.height * .33f,
                                ),
                            )
                            drawRect(
                                Brush.horizontalGradient(
                                    colors = listOf(gradientColor, Color.Transparent),
                                    startX = 0f,
                                    endX = size.width * .5f,
                                ),
                            )
                        },
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            MainPageHeader(
                item = focusedItem,
                modifier =
                    Modifier
                        .fillMaxWidth(.6f)
                        .fillMaxHeight(.33f)
                        .padding(16.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding =
                    PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 0.dp,
                        bottom = 48.dp,
                    ),
                modifier = Modifier,
            ) {
                items(homeRows) { row ->
                    ItemRow(
                        title = stringResource(row.section.nameRes),
                        items = row.items,
                        onClickItem = {
                            navigationManager.navigateTo(it.destination())
                        },
                        cardOnFocus = { isFocused, index ->
                            focusedItem = row.items.getOrNull(index)
                        },
                        onLongClickItem = {},
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun MainPageHeader(
    item: BaseItem?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item?.let {
                val dto = item.data
                val isEpisode = item.type == BaseItemKind.EPISODE
                val title = if (isEpisode) dto.seriesName ?: item.name else item.name
                val subtitle = if (isEpisode) dto.name else null
                val overview = dto.overview
                val details =
                    buildList {
                        if (isEpisode) {
                            add("S${dto.parentIndexNumber} E${dto.indexNumber}")
                        }
                        if (isEpisode) {
                            dto.premiereDate?.let { add(formatDateTime(it)) }
                        } else {
                            dto.productionYear?.let { add(it.toString()) }
                        }
                        dto.runTimeTicks?.ticks?.roundMinutes?.let {
                            add(it.toString())
                        }
                        dto.timeRemaining?.roundMinutes?.let {
                            add("$it left")
                        }
                    }
                title?.let {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
                subtitle?.let {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                if (details.isNotEmpty()) {
                    DotSeparatedRow(
                        texts = details,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier,
                    )
                }
                val overviewModifier =
                    Modifier
                        .padding(0.dp)
                        .height(48.dp)
                if (overview.isNotNullOrBlank()) {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = overviewModifier,
                    )
                } else {
                    Spacer(overviewModifier)
                }
            }
        }
    }
}
