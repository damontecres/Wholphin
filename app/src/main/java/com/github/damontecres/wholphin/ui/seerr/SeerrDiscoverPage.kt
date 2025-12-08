package com.github.damontecres.wholphin.ui.seerr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.api.seerr.model.MovieResult
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.SeerrService
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.cards.ItemRow
import com.github.damontecres.wholphin.ui.cards.SeasonCard
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.setValueOnMain
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SeerrDiscoverViewModel
    @Inject
    constructor(
        val seerrService: SeerrService,
    ) : ViewModel() {
        val discoverMovies = MutableLiveData<List<MovieResult>>(listOf())

        init {
            viewModelScope.launchIO {
                val movies = seerrService.discoverMovies()
                discoverMovies.setValueOnMain(movies)
            }
        }
    }

@Composable
fun SeerrDiscoverPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: SeerrDiscoverViewModel = hiltViewModel(),
) {
    val movies by viewModel.discoverMovies.observeAsState(listOf())
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            modifier
                .padding(16.dp),
    ) {
        ItemRow(
            title = "Movies",
            items = movies,
            onClickItem = { index, item -> },
            onLongClickItem = { index, item -> },
            cardContent = { index: Int, item: MovieResult?, mod: Modifier, onClick: () -> Unit, onLongClick: () -> Unit ->
                SeasonCard(
                    title = item?.title,
                    subtitle = null,
                    name = item?.title,
                    imageUrl = item?.posterPath?.let { "https://image.tmdb.org/t/p/w500$it" }, // TODO
                    isFavorite = false,
                    isPlayed = false,
                    unplayedItemCount = -1,
                    playedPercentage = -1.0,
                    onClick = onClick,
                    onLongClick = onLongClick,
                    imageHeight = Cards.height2x3,
                    modifier = mod,
                )
            },
        )
    }
}
