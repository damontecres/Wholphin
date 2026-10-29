package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.ExternalRating
import com.github.damontecres.wholphin.data.model.QuickDetailsData
import com.github.damontecres.wholphin.preferences.DisplayToggle
import com.github.damontecres.wholphin.ui.dot
import com.github.damontecres.wholphin.ui.formatTime
import com.github.damontecres.wholphin.ui.util.LocalClock
import com.github.damontecres.wholphin.ui.util.LocalInterfaceCustomization
import org.jellyfin.sdk.model.DateTime
import kotlin.time.Duration

@Composable
fun QuickDetails(
    details: QuickDetailsData?,
    timeRemaining: Duration?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    endsAt: DateTime? = null,
    externalRatings: List<ExternalRating> = emptyList(),
) {
    val enabled = LocalInterfaceCustomization.current.enabledDisplayToggles
    val showExternalRatings = DisplayToggle.MDBLIST_RATINGS in enabled && externalRatings.isNotEmpty()
    if (showExternalRatings) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier,
        ) {
            // External ratings replace the critic & community ratings
            QuickDetailsRow(details, timeRemaining, textStyle, endsAt, showRatings = false)
            ExternalRatingsRow(externalRatings, MaterialTheme.typography.bodyMedium)
        }
    } else {
        QuickDetailsRow(details, timeRemaining, textStyle, endsAt, showRatings = true, modifier = modifier)
    }
}

@Composable
private fun QuickDetailsRow(
    details: QuickDetailsData?,
    timeRemaining: Duration?,
    textStyle: TextStyle,
    endsAt: DateTime?,
    showRatings: Boolean,
    modifier: Modifier = Modifier,
) {
    val enabled = LocalInterfaceCustomization.current.enabledDisplayToggles
    val inlineContentMap = rememberQuickDetailsContentMap(textStyle)
    Row(modifier = modifier) {
        if (details != null) {
            QuickDetailsText(details.basic, Modifier, textStyle, inlineContentMap)
            if (DisplayToggle.OFFICIAL_RATING in enabled) {
                QuickDetailsText(details.officialRating, Modifier, textStyle, inlineContentMap)
            }
            if (showRatings && DisplayToggle.COMMUNITY_RATING in enabled) {
                QuickDetailsText(details.communityRating, Modifier, textStyle, inlineContentMap)
            }
            if (showRatings && DisplayToggle.CRITIC_RATING in enabled) {
                QuickDetailsText(details.criticRating, Modifier, textStyle, inlineContentMap)
            }
        }
        if (timeRemaining != null) {
            TimeRemaining(timeRemaining, textStyle = textStyle)
        } else if (endsAt != null) {
            EndsAt(endsAt, textStyle = textStyle)
        }
    }
}

@NonRestartableComposable
@Composable
fun QuickDetailsText(
    str: AnnotatedString?,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
    inlineContentMap: Map<String, InlineTextContent> = rememberQuickDetailsContentMap(textStyle),
) = str?.let {
    Text(
        text = str,
        color = MaterialTheme.colorScheme.onSurface,
        style = textStyle,
        inlineContent = inlineContentMap,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
fun rememberQuickDetailsContentMap(textStyle: TextStyle = MaterialTheme.typography.titleSmall) =
    remember(textStyle) {
        mapOf(
            "star" to
                InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        tint = FilledStarColor,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            "rotten" to
                InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rotten_tomatoes_rotten),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.Unspecified,
                    )
                },
            "fresh" to
                InlineTextContent(
                    Placeholder(
                        textStyle.fontSize,
                        textStyle.fontSize,
                        PlaceholderVerticalAlign.TextCenter,
                    ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_rotten_tomatoes_fresh),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        tint = Color.Unspecified,
                    )
                },
        )
    }

/**
 * Displays ratings from third party sources, each as an icon followed by its value
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExternalRatingsRow(
    ratings: List<ExternalRating>,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val iconHeight = with(LocalDensity.current) { textStyle.fontSize.toDp() }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        ratings.forEach { rating ->
            val icon = rating.icon ?: return@forEach
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = rating.iconUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(width = iconHeight * icon.aspectRatio, height = iconHeight),
                )
                Text(
                    text = rating.displayText,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = textStyle,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun TimeRemaining(
    remaining: Duration,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val now by LocalClock.current.now
    EndsAt(
        endsAt = now.plusSeconds(remaining.inWholeSeconds),
        modifier = modifier,
        textStyle = textStyle,
    )
}

@Composable
fun EndsAt(
    endsAt: DateTime,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val resources = LocalResources.current
    val context = LocalContext.current
    val remainingStr =
        remember(endsAt, resources, context) {
            val endTimeStr = formatTime(context, endsAt)
            buildAnnotatedString {
                dot()
                append(resources.getString(R.string.ends_at, endTimeStr))
            }
        }
    Text(
        text = remainingStr,
        style = textStyle,
        maxLines = 1,
        modifier = modifier,
    )
}
