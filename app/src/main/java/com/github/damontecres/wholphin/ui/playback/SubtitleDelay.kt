package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val delayIncrements = listOf(50.milliseconds, 250.milliseconds, 1.seconds)

@Composable
fun SubtitleDelay(
    delay: Duration,
    onChangeDelay: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.subtitle_delay) + ": " + delay.toString(),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            delayIncrements.reversed().forEach {
                SubtitleDelayButton(
                    text = "-$it",
                    onClick = { onChangeDelay.invoke(-it + delay) },
                    modifier = Modifier,
                )
            }
            SubtitleDelayButton(
                text = stringResource(R.string.reset),
                onClick = { onChangeDelay.invoke(Duration.ZERO) },
                modifier = Modifier,
            )
            delayIncrements.forEach {
                SubtitleDelayButton(
                    text = "+$it",
                    onClick = { onChangeDelay.invoke(it + delay) },
                    modifier = Modifier,
                )
            }
        }
    }
}

@Composable
fun SubtitleDelayButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.width(64.dp),
        shape =
            ClickableSurfaceDefaults.shape(
                shape = RoundedCornerShape(33),
            ),
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@PreviewTvSpec
@Composable
private fun SubtitleDelayPreview() {
    WholphinTheme {
        SubtitleDelay(
            delay = 1.5.seconds,
            onChangeDelay = {},
            modifier = Modifier,
        )
    }
}
