package com.github.damontecres.wholphin.ui.cards

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.DiscoverItem
import com.github.damontecres.wholphin.data.model.SeerrAvailability
import com.github.damontecres.wholphin.data.model.SeerrItemType
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.Cards
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun DiscoverItemCard(
    item: DiscoverItem?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    showOverlay: Boolean = true,
    subtitleOverride: String? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    width: Dp = Cards.height2x3 * AspectRatios.TALL,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    var focusedAfterDelay by remember { mutableStateOf(false) }

    val hideOverlayDelay = 500L
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.size(width, Dp.Unspecified),
    ) {
        Card(
            modifier =
                Modifier
                    .size(width, Dp.Unspecified)
                    .aspectRatio(AspectRatios.TALL),
            onClick = onClick,
            onLongClick = onLongClick,
            interactionSource = interactionSource,
            colors =
                CardDefaults.colors(
                    containerColor = Color.Transparent,
                ),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(),
            ) {
                ItemCardImage(
                    imageUrl = item?.posterUrl,
                    name = item?.title,
                    showOverlay = false,
                    favorite = false,
                    watched = false,
                    unwatchedCount = 0,
                    watchedPercent = null,
                    numberOfVersions = -1,
                    useFallbackText = false,
                    contentScale = ContentScale.FillBounds,
                    modifier =
                        Modifier
                            .fillMaxSize(),
                )
                if (item?.isActivelyDownloading == true) {
                    DownloadingIndicator(
                        progressPercent = item.downloadProgress,
                        modifier = Modifier.align(Alignment.TopEnd),
                    )
                } else {
                    when (item?.availability) {
                        SeerrAvailability.PENDING,
                        SeerrAvailability.PROCESSING,
                        -> {
                            if (item.releaseDate?.isAfter(LocalDate.now()) == true) {
                                FutureReleaseIndicator(Modifier.align(Alignment.TopEnd))
                            } else {
                                PendingIndicator(Modifier.align(Alignment.TopEnd))
                            }
                        }

                        SeerrAvailability.PARTIALLY_AVAILABLE -> {
                            PartiallyAvailableIndicator(Modifier.align(Alignment.TopEnd))
                        }

                        SeerrAvailability.AVAILABLE,
                        -> {
                            AvailableIndicator(Modifier.align(Alignment.TopEnd))
                        }

                        SeerrAvailability.BLOCKLISTED -> {
                            // TODO handle block listed
//                        BlocklistedIndicator(Modifier.align(Alignment.TopEnd))
                        }

                        SeerrAvailability.UNKNOWN,
                        SeerrAvailability.DELETED,
                        null,
                        -> {
                        }
                    }
                }
                if (showOverlay) {
                    val color =
                        remember(item?.type) {
                            when (item?.type) {
                                SeerrItemType.MOVIE -> AppColors.Discover.Blue
                                SeerrItemType.TV -> AppColors.Discover.Purple
                                SeerrItemType.PERSON -> AppColors.Discover.Green
                                SeerrItemType.UNKNOWN -> Color.Black
                                null -> Color.Black
                            }.copy(alpha = .8f)
                        }
                    val text =
                        remember(item?.type) {
                            when (item?.type) {
                                SeerrItemType.MOVIE -> R.plurals.movies
                                SeerrItemType.TV -> R.plurals.tv_shows
                                SeerrItemType.PERSON -> R.plurals.people
                                SeerrItemType.UNKNOWN -> null
                                null -> null
                            }
                        }
                    text?.let {
                        Text(
                            text = pluralStringResource(it, 1),
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 10.sp,
                            modifier =
                                Modifier
                                    .align(Alignment.TopStart)
                                    .padding(4.dp)
                                    .background(
                                        color = color,
                                        shape = CircleShape,
                                    ).padding(4.dp),
                        )
                    }
                }
                if (item?.isActivelyDownloading == true) {
                    val fraction =
                        ((item.downloadProgress ?: 0.0) / 100.0)
                            .toFloat()
                            .coerceIn(0.05f, 1f)
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(Color.Black.copy(alpha = 0.65f))
                                .clip(RectangleShape),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction)
                                    .background(MaterialTheme.colorScheme.tertiary),
                        )
                    }
                }
            }
        }
        val secondaryText =
            subtitleOverride
                ?: if (item?.isActivelyDownloading == true && item.downloadProgress != null) {
                    val pct = "${item.downloadProgress.roundToInt().coerceAtMost(99)}%"
                    val year = item.releaseDate?.year?.toString()
                    if (year != null) "$pct \u2022 $year" else pct
                } else {
                    item?.releaseDate?.year?.toString() ?: ""
                }
        SlidingCardText(focused) {
            Text(
                text = item?.title ?: "",
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
            Text(
                text = secondaryText,
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

@Composable
fun DownloadingIndicator(
    progressPercent: Double?,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier =
            modifier
                .padding(4.dp)
                .border(
                    width = .5.dp,
                    color = Color.White,
                    shape = RoundedCornerShape(50),
                ).background(
                    color = AppColors.Discover.Blue.copy(alpha = .9f),
                    shape = RoundedCornerShape(50),
                ).padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = stringResource(R.string.fa_download),
            fontFamily = FontAwesome,
            fontSize = 9.sp,
            color = Color.White,
        )
        if (progressPercent != null && progressPercent > 0.0) {
            Text(
                text = "${progressPercent.roundToInt().coerceAtMost(99)}%",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

@Composable
fun FutureReleaseIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(4.dp)
                .border(
                    width = .5.dp,
                    color = Color.White,
                    shape = CircleShape,
                ).background(
                    color = AppColors.Discover.Purple.copy(alpha = .9f),
                    shape = CircleShape,
                ).size(16.dp),
    ) {
        Text(
            text = stringResource(R.string.fa_calendar_days),
            fontFamily = FontAwesome,
            fontSize = 9.sp,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun PendingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(4.dp)
                .border(
                    width = .5.dp,
                    color = AppColors.Discover.Yellow,
                    shape = CircleShape,
                ).background(
                    color = Color.White.copy(alpha = .85f),
                    shape = CircleShape,
                ).size(16.dp),
    ) {
        Text(
            text = stringResource(R.string.fa_bell),
            fontFamily = FontAwesome,
            fontSize = 10.sp,
            color = AppColors.Discover.Yellow,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun AvailableIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(4.dp)
                .border(
                    width = .5.dp,
                    color = Color.White,
                    shape = CircleShape,
                ).background(
                    color = AppColors.Discover.Green.copy(alpha = .85f),
                    shape = CircleShape,
                ).size(16.dp),
    ) {
        Text(
            text = stringResource(R.string.fa_check),
            fontFamily = FontAwesome,
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun PartiallyAvailableIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(4.dp)
                .border(
                    width = .5.dp,
                    color = Color.White,
                    shape = CircleShape,
                ).background(
                    color = AppColors.Discover.Green.copy(alpha = .85f),
                    shape = CircleShape,
                ).size(16.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(width = 10.dp, height = 2.dp)
                    .background(
                        color = Color.White,
                        shape = CircleShape,
                    ),
        )
    }
}

@Composable
fun BlocklistedIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .padding(4.dp)
                .border(
                    width = .5.dp,
                    color = Color.White,
                    shape = CircleShape,
                ).background(
                    color = AppColors.Discover.Red.copy(alpha = .85f),
                    shape = CircleShape,
                ).size(16.dp),
    ) {
        Text(
            text = stringResource(R.string.fa_xmark),
            fontFamily = FontAwesome,
            fontSize = 10.sp,
            color = Color.White,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@PreviewTvSpec
@Composable
private fun Preview() {
    WholphinTheme {
        Column {
            PendingIndicator()
            AvailableIndicator()
            PartiallyAvailableIndicator()
            BlocklistedIndicator()
        }
    }
}
