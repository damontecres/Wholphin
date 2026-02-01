package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
fun SeriesName(
    seriesName: String?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = seriesName ?: "",
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun EpisodeName(
    episodeName: String?,
    modifier: Modifier = Modifier,
    isHidden: Boolean = false,
) {
    Text(
        text = if (isHidden) stringResource(com.github.damontecres.wholphin.R.string.title_hidden) else (episodeName ?: ""),
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.headlineSmall,
        fontStyle = if (isHidden) FontStyle.Italic else FontStyle.Normal,
        fontSize = 20.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun EpisodeName(
    episode: BaseItemDto?,
    modifier: Modifier = Modifier,
    isHidden: Boolean = false,
) = EpisodeName(episode?.episodeTitle ?: episode?.name, modifier, isHidden)
