package com.github.damontecres.wholphin.ui.slideshow

import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun ImageControlsOverlay(
    isImageClip: Boolean,
    onZoom: (Float) -> Unit,
    onRotate: (Int) -> Unit,
    onReset: () -> Unit,
    moreOnClick: () -> Unit,
    isPlaying: Boolean,
    playPauseOnClick: () -> Unit,
    bringIntoViewRequester: BringIntoViewRequester?,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        focusRequester.tryRequestFocus()
    }
    val onFocused = { focusState: FocusState ->
        if (focusState.isFocused && bringIntoViewRequester != null) {
            scope.launch(ExceptionHandler()) { bringIntoViewRequester.bringIntoView() }
        }
    }

    LazyRow(
        modifier =
            modifier
                .focusGroup(),
        contentPadding = PaddingValues(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isImageClip) {
            item {
                Button(
                    onClick = playPauseOnClick,
                    modifier =
                        Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged(onFocused),
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isPlaying) R.drawable.baseline_play_arrow_24 else R.drawable.baseline_pause_24,
                            ),
                        contentDescription = null,
                    )
                }
            }
        } else {
            // Regular image
            // TODO might be able to apply to the player surface?
            // If enabling these, make sure the focusRequester is updated!
            item {
                ImageControlButton(
                    stringRes = R.string.fa_rotate_left,
                    onClick = { onRotate(-90) },
                    modifier =
                        Modifier
                            .focusRequester(focusRequester)
                            .onFocusChanged(onFocused),
                )
            }
            item {
                ImageControlButton(
                    stringRes = R.string.fa_rotate_right,
                    onClick = { onRotate(90) },
                    modifier =
                        Modifier
                            .onFocusChanged(onFocused),
                )
            }
            item {
                ImageControlButton(
                    stringRes = R.string.fa_magnifying_glass_plus,
                    onClick = { onZoom(.15f) },
                    modifier =
                        Modifier
                            .onFocusChanged(onFocused),
                )
            }
            item {
                ImageControlButton(
                    stringRes = R.string.fa_magnifying_glass_minus,
                    onClick = { onZoom(-.15f) },
                    modifier =
                        Modifier
                            .onFocusChanged(onFocused),
                )
            }
            item {
                ImageControlButton(
                    drawableRes = R.drawable.baseline_undo_24,
                    onClick = onReset,
                    modifier =
                        Modifier
                            .onFocusChanged(onFocused),
                )
            }
        }
        // More button
        item {
            Button(
                onClick = moreOnClick,
                onLongClick = {},
                modifier =
                    Modifier
                        .onFocusChanged(onFocused),
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.more),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        }
    }
}

@Composable
fun ImageControlButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @StringRes stringRes: Int = 0,
    @DrawableRes drawableRes: Int = 0,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
    ) {
        if (stringRes != 0) {
            Text(
                text = stringResource(stringRes),
                fontFamily = FontAwesome,
                style = MaterialTheme.typography.titleLarge,
            )
        } else {
            Icon(
                painter = painterResource(drawableRes),
                contentDescription = "",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Preview(widthDp = 800)
@Composable
private fun ImageControlsOverlayPreview() {
    WholphinTheme {
        ImageControlsOverlay(
            isImageClip = false,
            onZoom = {},
            onRotate = {},
            onReset = {},
            moreOnClick = {},
            isPlaying = false,
            playPauseOnClick = {},
            bringIntoViewRequester = null,
            modifier = Modifier,
        )
    }
}
