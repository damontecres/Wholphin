package com.github.damontecres.wholphin.ui.seerr

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Text
import com.github.damontecres.api.seerr.model.MovieResult
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.SeerrService
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
        modifier = modifier,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(movies) { movie ->
                Text(
                    text = movie.title,
                )
            }
        }
    }
}
