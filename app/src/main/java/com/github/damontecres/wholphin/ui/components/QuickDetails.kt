package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R

@Composable
fun QuickDetails(
    details: AnnotatedString,
    modifier: Modifier = Modifier,
    textStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    val inlineContentMap =
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

    Text(
        text = details,
        color = MaterialTheme.colorScheme.onSurface,
        style = textStyle,
        inlineContent = inlineContentMap,
        maxLines = 1,
        modifier = modifier,
    )
}

// @Composable
// fun TimeRemaning() {
//    (dto?.timeRemaining ?: runtime)?.let { remaining ->
//        val endTimeStr = TimeFormatter.format(now.plusSeconds(remaining.inWholeSeconds))
//        add(context.getString(R.string.ends_at, endTimeStr))
//    }
// }
