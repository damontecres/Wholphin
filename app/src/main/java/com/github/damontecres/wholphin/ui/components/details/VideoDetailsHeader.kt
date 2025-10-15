package com.github.damontecres.wholphin.ui.components.details

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.DotSeparatedRow
import com.github.damontecres.wholphin.ui.components.PlayButtons
import com.github.damontecres.wholphin.ui.components.StarRating
import com.github.damontecres.wholphin.ui.components.StarRatingPrecision
import com.github.damontecres.wholphin.ui.components.TitleValueText
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.playOnClickSound
import com.github.damontecres.wholphin.ui.playSoundOnFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import java.util.SortedMap
import kotlin.time.Duration

@Composable
fun VideoDetailsHeader(
    title: String,
    subtitle: String?,
    description: String?,
    details: List<String>,
    moreDetails: SortedMap<String, String>,
    rating: Float?,
    resumeTime: Duration?,
    watched: Boolean,
    favorite: Boolean,
    bringIntoViewRequester: BringIntoViewRequester,
    descriptionOnClick: () -> Unit,
    moreOnClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    Column(
        modifier = modifier,
    ) {
        // Title
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            style =
                MaterialTheme.typography.displayMedium.copy(
                    shadow =
                        Shadow(
                            color = Color.DarkGray,
                            offset = Offset(5f, 2f),
                            blurRadius = 2f,
                        ),
                ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Subtitle
        if (subtitle.isNotNullOrBlank()) {
            // Title
            Text(
                text = subtitle,
                color = MaterialTheme.colorScheme.onSurface,
                style =
                    MaterialTheme.typography.displaySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            modifier = Modifier.alpha(0.75f),
        ) {
            // Rating
            if (rating != null) {
                StarRating(
                    rating100 = (rating * 100).toInt(),
                    precision = StarRatingPrecision.HALF,
                    onRatingChange = {
                        // TODO
                    },
                    enabled = false,
                    modifier =
                        Modifier
                            .height(40.dp)
                            .padding(start = 12.dp),
                    playSoundOnFocus = false,
                )
            }

            // Quick info
            if (details.isNotEmpty()) {
                DotSeparatedRow(
                    modifier = Modifier.padding(top = 6.dp, start = 8.dp),
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    texts = details,
                )
            }
            // Description
            if (description.isNotNullOrBlank()) {
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused = interactionSource.collectIsFocusedAsState().value
                val bgColor =
                    if (isFocused) {
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = .75f)
                    } else {
                        Color.Unspecified
                    }
                Box(
                    modifier =
                        Modifier
                            .background(bgColor, shape = RoundedCornerShape(8.dp))
                            .onFocusChanged {
                                if (it.isFocused) {
                                    scope.launch(ExceptionHandler()) { bringIntoViewRequester.bringIntoView() }
                                }
                            }.playSoundOnFocus(true)
                            .clickable(
                                enabled = true,
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                            ) {
                                playOnClickSound(context)
                                descriptionOnClick.invoke()
                            },
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            // Key-Values
            if (moreDetails.isNotEmpty()) {
                Row(
                    modifier =
                        Modifier
                            .padding(top = 8.dp, start = 16.dp)
                            .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    moreDetails.forEach { (key, value) ->
                        TitleValueText(key, value)
                    }
                }
            }
        }
        PlayButtons(
            resumePosition = resumeTime ?: Duration.ZERO,
            playOnClick = { position ->
                // TODO
            },
            moreOnClick = { moreOnClick.invoke() },
            buttonOnFocusChanged = {
                scope.launch(ExceptionHandler()) { bringIntoViewRequester.bringIntoView() }
            },
            focusRequester = focusRequester,
            modifier = Modifier,
        )
    }
}
