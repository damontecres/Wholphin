package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.data.model.DiscoverRating
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.GenreText
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.QuickDetails
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.ui.listToDotString
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

@Composable
fun DiscoverMovieDetailsHeader(
    preferences: UserPreferences,
    movie: MovieDetails,
    rating: DiscoverRating?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        // Title
        Text(
            text = movie.title ?: "",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.displaySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(.75f),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            val padding = 4.dp
            val details =
                remember(movie, rating) {
                    buildList {
                        movie.releaseDate?.let(::add)
                        movie.runtime
                            ?.toDouble()
                            ?.minutes
                            ?.roundMinutes
                            ?.toString()
                            ?.let(::add)
                        val release =
                            movie.releases
                                ?.results
                                ?.firstOrNull { it.iso31661 == Locale.getDefault().country }
                                ?: movie.releases
                                    ?.results
                                    ?.firstOrNull { it.iso31661 == Locale.US.country }
                                ?: movie.releases
                                    ?.results
                                    ?.firstOrNull()

                        release
                            ?.releaseDates
                            ?.firstOrNull()
                            ?.certification
                            ?.takeIf { it.isNotNullOrBlank() }
                            ?.let(::add)
                    }.let {
                        listToDotString(
                            it,
                            rating?.audienceRating,
                            rating?.criticRating?.toFloat(),
                        )
                    }
                }

            QuickDetails(details, null)
            movie.genres?.mapNotNull { it.name }?.letNotEmpty {
                GenreText(it, Modifier.padding(bottom = padding))
            }

            val tagline = remember { movie.tagline?.takeIf { it.isNotNullOrBlank() } }
            tagline?.let {
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier,
                )
            }

            // Description
            movie.overview?.let { overview ->
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    modifier =
                        Modifier.onFocusChanged {
                            if (it.isFocused) {
                                scope.launch(ExceptionHandler()) {
                                    bringIntoViewRequester.bringIntoView()
                                }
                            }
                        },
                )
            }

            val directorName =
                remember(movie.credits?.crew) {
                    movie.credits
                        ?.crew
                        ?.filter { it.job == "Director" && it.name.isNotNullOrBlank() }
                        ?.joinToString(", ") { it.name!! }
                        ?.takeIf { it.isNotNullOrBlank() }
                }

            directorName
                ?.let {
                    Text(
                        text = stringResource(R.string.directed_by, it),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
        }
    }
}
