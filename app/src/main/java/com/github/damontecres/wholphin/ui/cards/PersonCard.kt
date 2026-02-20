package com.github.damontecres.wholphin.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.Person
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.PersonKind

/**
 * A Card for a [Person] such as an actor or director
 */
@Composable
fun PersonCard(
    person: Person,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) = PersonCard(
    name = person.name,
    role = person.role,
    imageUrl = person.imageUrl,
    favorite = person.favorite,
    onClick = onClick,
    onLongClick = onLongClick,
    modifier = modifier,
    interactionSource = interactionSource,
)

@Composable
fun PersonCard(
    name: String?,
    role: String?,
    imageUrl: String?,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val hideOverlayDelay = 1_000L

    val focused = interactionSource.collectIsFocusedAsState().value
    var focusedAfterDelay by remember { mutableStateOf(false) }

    if (focused) {
        LaunchedEffect(Unit) {
            delay(hideOverlayDelay)
            if (focused) {
                focusedAfterDelay = true
            } else {
                focusedAfterDelay = false
            }
        }
    } else {
        focusedAfterDelay = false
    }
    val spaceBetween by animateDpAsState(if (focused) 12.dp else 4.dp)
    val spaceBelow by animateDpAsState(if (focused) 4.dp else 12.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(spaceBetween),
        modifier = modifier,
    ) {
        Card(
            modifier = Modifier,
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            shape = CardDefaults.shape(CircleShape),
            border =
                CardDefaults.border(
                    focusedBorder =
                        Border(
                            border =
                                BorderStroke(
                                    width = 3.dp,
                                    color = MaterialTheme.colorScheme.border,
                                ),
                            shape = CircleShape,
                        ),
                ),
            colors =
                CardDefaults.colors(
                    containerColor = Color.Transparent,
                ),
        ) {
            ItemCardImage(
                imageUrl = imageUrl,
                name = name,
                showOverlay = false,
                favorite = favorite,
                watched = false,
                unwatchedCount = -1,
                numberOfVersions = -1,
                watchedPercent = null,
                useFallbackText = true,
                contentScale = ContentScale.Crop,
                fallback = {
                    Box(
                        modifier =
                            modifier
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .fillMaxSize()
                                .align(Alignment.Center),
                    ) {
                        Text(
                            text = stringResource(R.string.fa_user),
                            fontFamily = FontAwesome,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 64.sp,
                            textAlign = TextAlign.Center,
                            modifier =
                                Modifier
                                    .padding(8.dp)
                                    .fillMaxWidth()
                                    .align(Alignment.Center),
                        )
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(AspectRatios.SQUARE)
                        .clip(CircleShape),
            )
        }
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier =
                Modifier
                    .padding(bottom = spaceBelow)
                    .fillMaxWidth(),
        ) {
            Text(
                text = name ?: "",
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .enableMarquee(focusedAfterDelay),
            )
            role?.let {
                Text(
                    text = role,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Normal,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp)
                            .enableMarquee(focusedAfterDelay),
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun PersonCardPreview() {
    WholphinTheme {
        PersonCard(
            person =
                Person(
                    id = UUID.randomUUID(),
                    name = "John Smith",
                    role = "Actor",
                    type = PersonKind.ACTOR,
                    imageUrl = null,
                    favorite = false,
                ),
            onClick = {},
            onLongClick = {},
            modifier = Modifier.width(personRowCardWidth),
        )
    }
}
