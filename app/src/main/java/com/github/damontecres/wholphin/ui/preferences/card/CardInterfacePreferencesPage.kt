package com.github.damontecres.wholphin.ui.preferences.card

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalGridApi
import androidx.compose.foundation.layout.Grid
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.github.damontecres.wholphin.ui.cards.BannerCardWithTitle
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.util.LoadingState
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber

@OptIn(UnstableApi::class)
@Composable
fun CardInterfacePreferencesPage(
    initialPreferences: AppPreferences,
    modifier: Modifier = Modifier,
    viewModel: CardPreferencesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
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
        CardInterfacePreferencesContent(
            title = stringResource(R.string.card_customization),
            preferences = preferences.interfacePreferences.cardInterfacePreferences,
            prefList = CardPreferences.Preferences,
            onPreferenceChange = { newPrefs ->
                viewModel.preferenceDataStore.updateData {
                    it.updateInterfacePreferences {
                        cardInterfacePreferences = newPrefs
                    }
                }
                Timber.v("Card prefs: %s", preferences.interfacePreferences.cardInterfacePreferences)
            },
            onFocus = { groupIndex, prefIndex ->
            },
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(.33f),
        )
    }
}

@kotlin.OptIn(ExperimentalGridApi::class)
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
            val movieStr = stringResource(R.string.movies_title)
            val epStr = stringResource(R.string.episodes)
            val inProgressStr = stringResource(androidx.compose.ui.R.string.in_progress)
            val watchedStr = stringResource(R.string.played)
            Box(modifier = modifier.padding(bottom = 16.dp)) {
                Grid(
                    config = {
                        repeat(3) { column(1.fr) }
                        repeat(2) { row(1.fr) }
                        gap(8.dp)
                    },
                    modifier = Modifier.padding(8.dp),
                ) {
                    Card("$watchedStr: $movieStr", state.movie)
                    Card("$inProgressStr: $movieStr", state.movie2)
                    Card(stringResource(R.string.collections), state.collection)
                    Card(stringResource(R.string.tv_shows_title), state.tvShow)
                    Card(stringResource(R.string.tv_seasons), state.tvSeason)
                    Card("$inProgressStr: $epStr", state.episode, cardHeight = 136.dp)
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
    cardHeight: Dp = 160.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
