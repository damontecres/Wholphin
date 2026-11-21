package com.github.damontecres.wholphin.ui.preferences.card

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.CardPreferences
import com.github.damontecres.wholphin.preferences.updateInterfacePreferences
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
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
            title = stringResource(R.string.ui_interface),
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
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(16.dp),
                modifier = modifier,
            ) {
                item {
                    Card(state.movie)
                }
                item {
                    Card(state.movie2)
                }
                item {
                    Card(state.collection)
                }
                item {
                    Card(state.tvShow)
                }
                item {
                    Card(state.tvSeason)
                }
                item {
                    Card(state.episode)
                }
            }
        }
    }
}

@Composable
private fun Card(
    item: BaseItem?,
    modifier: Modifier = Modifier,
    cardHeight: Dp = Cards.height2x3,
) {
    if (item != null) {
        BannerCardWithTitle(
            title = item?.title,
            subtitle = item?.subtitle,
            item = item,
            aspectRatio = item.aspectRatio ?: AspectRatios.MIN,
            imageType = ImageType.PRIMARY,
            imageContentScale = ContentScale.FillBounds,
            cornerText = item?.ui?.episodeUnplayedCornerText,
            played = item?.data?.userData?.played ?: false,
            favorite = item?.favorite ?: false,
            playPercent =
                item?.data?.userData?.playedPercentage
                    ?: 0.0,
            onClick = {},
            onLongClick = {},
            modifier = modifier,
            cardHeight = cardHeight,
            useSeriesForPrimary = false,
        )
    } else {
        Text(
            text = "Not found",
        )
    }
}
