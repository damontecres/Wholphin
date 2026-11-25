package com.github.damontecres.wholphin.ui.preferences.card

import android.annotation.SuppressLint
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.CardPreferences
import com.github.damontecres.wholphin.preferences.updateInterfacePreferences
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.BannerCardWithTitle
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.preferences.SimplePreferencesContent
import com.github.damontecres.wholphin.ui.titleStringRes
import com.github.damontecres.wholphin.util.LoadingState
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType

@OptIn(UnstableApi::class)
@Composable
fun CardInterfacePreferencesPage(
    initialPreferences: AppPreferences,
    modifier: Modifier = Modifier,
    viewModel: CardPreferencesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var preferences by remember { mutableStateOf(initialPreferences) }
    LaunchedEffect(Unit) {
        viewModel.preferenceDataStore.data.collect {
            preferences = it
        }
    }

    Row(
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier =
                Modifier
                    .fillMaxSize()
                    .weight(1f),
        ) {
            CardExamples(
                state = state,
                modifier = Modifier.fillMaxSize(),
            )
        }
        SimplePreferencesContent(
            title = stringResource(R.string.card_customization),
            preferences = preferences.interfacePreferences.cardInterfacePreferences,
            prefList = CardPreferences.Preferences,
            onPreferenceChange = { newPrefs ->
                viewModel.preferenceDataStore.updateData {
                    it.updateInterfacePreferences {
                        cardInterfacePreferences = newPrefs
                    }
                }
            },
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(.33f),
        )
    }
}

@SuppressLint("PrivateResource")
@Composable
fun CardExamples(
    state: CardPreferencesState,
    modifier: Modifier = Modifier,
) {
    when (val s = state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(s, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier, focusEnabled = false)
        }

        LoadingState.Success -> {
            var showTitle by remember { mutableStateOf(true) }
            Column(modifier = modifier.padding(16.dp)) {
                AnimatedVisibility(
                    visible = showTitle,
                    enter = expandVertically(expandFrom = Alignment.Top),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top),
                ) {
                    Text(
                        text = stringResource(R.string.samples_title),
                        style = MaterialTheme.typography.displaySmall,
                    )
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(CardPreferencesViewModel.dataTypes) { index, type ->
                        ItemRow(
                            title = stringResource(type.titleStringRes),
                            items = state.data[type].orEmpty(),
                            onClickItem = { _, _ -> },
                            onLongClickItem = { _, _ -> },
                            cardContent = @Composable {
                                index: Int,
                                item: BaseItem?,
                                mod: Modifier,
                                onClick: () -> Unit,
                                onLongClick: () -> Unit,
                                ->
                                val title =
                                    when (index) {
                                        0 -> stringResource(R.string.unwatched)
                                        1 -> stringResource(androidx.compose.ui.R.string.in_progress)
                                        2 -> stringResource(R.string.played)
                                        else -> stringResource(R.string.unknown)
                                    }
                                Card(title, item, mod)
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged {
                                        if (it.hasFocus) {
                                            showTitle = index == 0
                                        }
                                    },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Card(
    title: String,
    item: BaseItem?,
    modifier: Modifier = Modifier,
    cardHeight: Dp = if (item?.type == BaseItemKind.EPISODE) Cards.heightEpisode else Cards.height2x3,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier,
        ) {
            Text(
                text = title,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            if (item != null) {
                BannerCardWithTitle(
                    title = item.title,
                    subtitle = item.subtitle,
                    item = item,
                    aspectRatio = item.aspectRatio ?: AspectRatios.MIN,
                    imageType = ImageType.PRIMARY,
                    imageContentScale = ContentScale.FillBounds,
                    cornerText = item.ui.episodeUnplayedCornerText,
                    cornerTextAlt = item.ui.seasonText,
                    played = item.data.userData?.played ?: false,
                    favorite = item.favorite,
                    playPercent = item.data.userData?.playedPercentage ?: 0.0,
                    onClick = {},
                    onLongClick = {},
                    modifier = Modifier,
                    cardHeight = cardHeight,
                    useSeriesForPrimary = false,
                    numberOfVersions = item.data.mediaSourceCount ?: 0,
                )
            } else {
                Text(
                    text = stringResource(R.string.no_results),
                )
            }
        }
    }
}
