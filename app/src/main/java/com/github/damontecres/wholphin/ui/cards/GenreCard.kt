package com.github.damontecres.wholphin.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Genre
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.setup.rememberIdColor
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import timber.log.Timber
import java.util.UUID

@Composable
fun GenreCard(
    genre: Genre?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val background = rememberIdColor(genre?.id).copy(alpha = .6f)
    Card(
        modifier =
        modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interactionSource,
        colors =
            CardDefaults.colors(
                containerColor = Color.Transparent,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .aspectRatio(AspectRatios.WIDE)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
        ) {
            if (genre?.imageUrl.isNotNullOrBlank()) {
                AsyncImage(
                    model =
                        ImageRequest
                            .Builder(LocalContext.current)
                            .data(genre.imageUrl)
                            .crossfade(true)
                            .build(),
                    contentScale = ContentScale.FillBounds,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .alpha(.6f)
                            .aspectRatio(AspectRatios.WIDE)
                            .fillMaxSize(),
                )
            }
            Box(
                modifier =
                    Modifier
                        .aspectRatio(AspectRatios.WIDE)
                        .fillMaxSize()
                        .background(background),
            ) {
                Text(
                    text = genre?.name ?: "",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .align(Alignment.Center),
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun GenreCardPreview() {
    WholphinTheme {
        val genre =
            Genre(
                UUID.randomUUID(),
                "Adventure",
                null,
                Color.Black,
            )
        GenreCard(
            genre = genre,
            onClick = {},
            onLongClick = {},
            modifier = Modifier.width(180.dp),
        )
    }
}
