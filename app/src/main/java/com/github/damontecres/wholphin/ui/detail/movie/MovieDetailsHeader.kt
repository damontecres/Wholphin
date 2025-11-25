package com.github.damontecres.wholphin.ui.detail.movie

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.DotSeparatedRow
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.VideoStreamDetails
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.timeRemaining
import org.jellyfin.sdk.model.api.PersonKind
import org.jellyfin.sdk.model.extensions.ticks

@Composable
fun MovieDetailsHeader(
    preferences: UserPreferences,
    movie: BaseItem,
    chosenStreams: ChosenStreams?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dto = movie.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        // Title
        Text(
            text = movie.name ?: "",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.displayMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(.75f),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            val details =
                remember(dto) {
                    buildList {
                        dto.productionYear?.let { add(it.toString()) }
                        val duration = dto.runTimeTicks?.ticks
                        duration
                            ?.roundMinutes
                            ?.toString()
                            ?.let(::add)
                        dto.timeRemaining?.roundMinutes?.let { add("$it left") }
                        dto.officialRating?.let(::add)
                    }
                }
            DotSeparatedRow(
                texts = details,
                rating = dto.communityRating,
                textStyle = MaterialTheme.typography.titleMedium,
                modifier = Modifier,
            )

            VideoStreamDetails(
                preferences = preferences,
                dto = dto,
                itemPlayback = chosenStreams?.itemPlayback,
                modifier = Modifier,
            )
            dto.taglines?.firstOrNull()?.let { tagline ->
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }

            // Description
            dto.overview?.let { overview ->
                val interactionSource = remember { MutableInteractionSource() }
                val focused = interactionSource.collectIsFocusedAsState().value
                LaunchedEffect(focused) {
                    if (focused) bringIntoViewRequester.bringIntoView()
                }
                OverviewText(
                    overview = overview,
                    maxLines = 3,
                    onClick = overviewOnClick,
                    textBoxHeight = Dp.Unspecified,
                    interactionSource = interactionSource,
                )
            }
            movie.data.people
                ?.filter { it.type == PersonKind.DIRECTOR && it.name.isNotNullOrBlank() }
                ?.joinToString(", ") { it.name!! }
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
