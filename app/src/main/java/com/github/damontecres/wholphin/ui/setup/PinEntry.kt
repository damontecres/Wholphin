package com.github.damontecres.wholphin.ui.setup

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.theme.WholphinTheme

@Composable
fun PinEntry(
    onTextChange: (String) -> Unit,
    onClickServerAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.enter_pin),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        PinArrowRow(Modifier.align(Alignment.CenterHorizontally))
        PinEntryDots(input.length, Modifier.align(Alignment.CenterHorizontally))

        Button(
            onClick = onClickServerAuth,
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyUp) {
                            var str = input
                            str +=
                                when (it.key) {
                                    Key.DirectionUp -> "U"
                                    Key.DirectionRight -> "R"
                                    Key.DirectionDown -> "D"
                                    Key.DirectionLeft -> "L"
                                    else -> return@onKeyEvent false
                                }
                            onTextChange.invoke(str)
                            input = str
                            return@onKeyEvent true
                        } else {
                            return@onKeyEvent false
                        }
                    },
        ) {
            Text(
                text = stringResource(R.string.use_server_credentials),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
fun PinEntryCreate(
    @StringRes title: Int,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember { mutableStateOf("") }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .onKeyEvent {
                    if (it.type == KeyEventType.KeyUp) {
                        var str = input
                        str +=
                            when (it.key) {
                                Key.DirectionUp -> "U"
                                Key.DirectionRight -> "R"
                                Key.DirectionDown -> "D"
                                Key.DirectionLeft -> "L"
                                else -> return@onKeyEvent false
                            }
                        onTextChange.invoke(str)
                        input = str
                        return@onKeyEvent true
                    } else {
                        return@onKeyEvent false
                    }
                }.focusable(),
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        PinArrowRow(Modifier.align(Alignment.CenterHorizontally))
        PinEntryDots(input.length, Modifier.align(Alignment.CenterHorizontally))
        Text(
            text = stringResource(R.string.press_enter_to_confirm),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
fun PinArrowRow(modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        val arrows =
            listOf(R.string.fa_arrow_left_long, R.string.fa_arrow_up_long, R.string.fa_arrow_right_long, R.string.fa_arrow_down_long)
        arrows.forEach {
            Text(
                text = stringResource(it),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontAwesome,
            )
        }
    }
}

@Composable
fun PinEntryDots(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier =
            modifier
                .defaultMinSize(minWidth = 180.dp, minHeight = 40.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f), shape = CircleShape)
                .padding(vertical = 16.dp),
    ) {
        repeat(count) {
            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 1f))
                        .size(8.dp),
            )
        }
    }
}

@Composable
fun PinEntryDialog(
    onDismissRequest: () -> Unit,
    onTextChange: (String) -> Unit,
    onClickServerAuth: () -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        PinEntry(
            onTextChange = onTextChange,
            onClickServerAuth = onClickServerAuth,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@PreviewTvSpec
@Composable
private fun PinEntryPreview() {
    WholphinTheme {
        PinEntry(
            onTextChange = {},
            onClickServerAuth = {},
            modifier = Modifier,
        )
    }
}
