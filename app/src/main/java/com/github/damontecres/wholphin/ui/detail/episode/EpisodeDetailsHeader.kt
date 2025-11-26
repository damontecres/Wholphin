package com.github.damontecres.wholphin.ui.detail.episode

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.components.EpisodeName
import com.github.damontecres.wholphin.ui.components.EpisodeQuickDetails
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.SeriesName
import com.github.damontecres.wholphin.ui.components.VideoStreamDetails
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.model.api.PersonKind

@Composable
fun EpisodeDetailsHeader(
    preferences: UserPreferences,
    ep: BaseItem,
    chosenStreams: ChosenStreams?,
    bringIntoViewRequester: BringIntoViewRequester,
    overviewOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dto = ep.data
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        SeriesName(dto.seriesName, Modifier.fillMaxWidth(.75f))
        EpisodeName(dto, Modifier.fillMaxWidth(.75f))

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(.60f),
        ) {
            val padding = 8.dp
            EpisodeQuickDetails(dto)

            VideoStreamDetails(
                preferences = preferences,
                dto = dto,
                itemPlayback = chosenStreams?.itemPlayback,
                modifier = Modifier.padding(bottom = padding),
            )
            dto.taglines?.firstOrNull()?.let { tagline ->
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier,
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
            ep.data.people
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
