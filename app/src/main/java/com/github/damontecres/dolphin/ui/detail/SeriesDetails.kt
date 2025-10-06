package com.github.damontecres.dolphin.ui.detail

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.dolphin.data.model.BaseItem
import com.github.damontecres.dolphin.data.model.Person
import com.github.damontecres.dolphin.preferences.UserPreferences
import com.github.damontecres.dolphin.ui.cards.ItemRow
import com.github.damontecres.dolphin.ui.cards.PersonRow
import com.github.damontecres.dolphin.ui.components.DotSeparatedRow
import com.github.damontecres.dolphin.ui.components.ErrorMessage
import com.github.damontecres.dolphin.ui.components.LoadingPage
import com.github.damontecres.dolphin.ui.components.StarRating
import com.github.damontecres.dolphin.ui.components.StarRatingPrecision
import com.github.damontecres.dolphin.ui.isNotNullOrBlank
import com.github.damontecres.dolphin.ui.letNotEmpty
import com.github.damontecres.dolphin.ui.nav.Destination
import com.github.damontecres.dolphin.ui.nav.NavigationManager
import com.github.damontecres.dolphin.ui.playOnClickSound
import com.github.damontecres.dolphin.ui.playSoundOnFocus
import com.github.damontecres.dolphin.ui.roundMinutes
import com.github.damontecres.dolphin.ui.tryRequestFocus
import com.github.damontecres.dolphin.util.LoadingState
import org.jellyfin.sdk.model.extensions.ticks

@Composable
fun SeriesDetails(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) {
        viewModel.init(preferences, destination.itemId, destination.item, null, null)
    }
    val loading by viewModel.loading.observeAsState(LoadingState.Loading)

    val item by viewModel.item.observeAsState()
    val seasons by viewModel.seasons.observeAsState(ItemListAndMapping.empty())
    val people by viewModel.people.observeAsState(listOf())

    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state)
        LoadingState.Loading -> LoadingPage()
        LoadingState.Success -> {
            item?.let { item ->
                SeriesDetailsContent(
                    preferences = preferences,
                    navigationManager = navigationManager,
                    series = item,
                    seasons = seasons,
                    people = people,
                    modifier = modifier,
                    overviewOnClick = {}, // TODO
                )
            }
        }
    }
}

@Composable
fun SeriesDetailsContent(
    preferences: UserPreferences,
    navigationManager: NavigationManager,
    series: BaseItem,
    seasons: ItemListAndMapping,
    people: List<Person>,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dto = series.data

    val seasonsFocusRequester = remember { FocusRequester() }
    if (seasons.items.isNotEmpty()) {
        LaunchedEffect(Unit) {
            seasonsFocusRequester.tryRequestFocus()
        }
    }

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
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier,
            ) {
                item {
                    SeriesDetailsHeader(
                        series = series,
                        overviewOnClick = overviewOnClick,
                        modifier = Modifier.fillMaxWidth(.7f),
                    )
                }
                item {
                    ItemRow(
                        title = "Seasons",
                        items = seasons.items,
                        onClickItem = { navigationManager.navigateTo(it.destination()) },
                        onLongClickItem = { },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(seasonsFocusRequester),
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
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val dto = series.data
    val details =
        buildList {
            dto.productionYear?.let { add(it.toString()) }
            dto.runTimeTicks
                ?.ticks
                ?.roundMinutes
                ?.let { add(it.toString()) }
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = series.name ?: "Unknown",
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.fillMaxWidth(),
        )
        DotSeparatedRow(
            texts = details,
            textStyle = MaterialTheme.typography.headlineSmall,
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
                    modifier = Modifier.height(32.dp),
                )
            }
        }

        dto.overview?.let { overview ->
            val interactionSource = remember { MutableInteractionSource() }
            val isFocused = interactionSource.collectIsFocusedAsState().value
            val bgColor =
                if (isFocused) {
                    MaterialTheme.colorScheme.onPrimary.copy(alpha = .4f)
                } else {
                    Color.Unspecified
                }
            Box(
                modifier =
                    Modifier
                        .background(bgColor, shape = RoundedCornerShape(8.dp))
                        .playSoundOnFocus(true)
                        .clickable(
                            enabled = true,
                            interactionSource = interactionSource,
                            indication = LocalIndication.current,
                        ) {
                            playOnClickSound(context)
                            overviewOnClick.invoke()
                        },
            ) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .padding(8.dp)
                            .height(60.dp),
                )
            }
        }
    }
}
