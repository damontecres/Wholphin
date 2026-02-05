package com.github.damontecres.wholphin.ui.slideshow

import androidx.annotation.OptIn
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.relocation.BringIntoViewRequester
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.launch
import kotlin.time.Duration

@OptIn(UnstableApi::class)
@Composable
fun ImageControlsOverlay(
    slideshowEnabled: Boolean,
    slideshowControls: SlideshowControls,
    onDismiss: () -> Unit,
    isImageClip: Boolean,
    onZoom: (Float) -> Unit,
    onRotate: (Int) -> Unit,
    onReset: () -> Unit,
    moreOnClick: () -> Unit,
    isPlaying: Boolean,
    playPauseOnClick: () -> Unit,
    onShowFilterDialogClick: () -> Unit,
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
        item {
            ExpandablePlayButton(
                title = if (slideshowEnabled) R.string.stop_slideshow else R.string.play_slideshow,
                icon =
                    painterResource(
                        if (slideshowEnabled) {
                            R.drawable.baseline_pause_24
                        } else {
                            R.drawable.baseline_play_arrow_24
                        },
                    ),
                resume = Duration.ZERO,
                onClick = {
                    if (slideshowEnabled) {
                        slideshowControls.stopSlideshow()
                    } else {
                        slideshowControls.startSlideshow()
                        onDismiss.invoke()
                    }
                },
                modifier =
                    Modifier
                        .focusRequester(focusRequester)
                        .onFocusChanged(onFocused),
            )
        }
        if (isImageClip) {
            item {
                Button(
                    onClick = playPauseOnClick,
                    modifier =
                        Modifier
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
            item {
                ExpandableFaButton(
                    title = R.string.rotate_left,
                    iconStringRes = R.string.fa_rotate_left,
                    onClick = { onRotate(-90) },
                    modifier =
                        Modifier
                            .onFocusChanged(onFocused),
                )
            }
            item {
                ExpandableFaButton(
                    title = R.string.rotate_right,
                    iconStringRes = R.string.fa_rotate_right,
                    onClick = { onRotate(90) },
                    modifier =
                        Modifier
                            .onFocusChanged(onFocused),
                )
            }
            item {
                ExpandableFaButton(
                    title = R.string.zoom_in,
                    iconStringRes = R.string.fa_magnifying_glass_plus,
                    onClick = { onZoom(.15f) },
                    modifier =
                        Modifier
                            .onFocusChanged(onFocused),
                )
            }
            item {
                ExpandableFaButton(
                    title = R.string.zoom_out,
                    iconStringRes = R.string.fa_magnifying_glass_minus,
                    onClick = { onZoom(-.15f) },
                    modifier =
                        Modifier
                            .onFocusChanged(onFocused),
                )
            }
            item {
                ExpandableFaButton(
                    title = R.string.reset,
                    iconStringRes = R.string.fa_arrows_rotate,
                    onClick = onReset,
                    modifier =
                        Modifier
                            .onFocusChanged(onFocused),
                )
            }
            item {
                ExpandableFaButton(
                    title = R.string.filter,
                    iconStringRes = R.string.fa_sliders,
                    onClick = onShowFilterDialogClick,
                    modifier =
                        Modifier
                            .onFocusChanged(onFocused),
                )
            }
        }
        // More button
//        item {
//            ExpandablePlayButton(
//                title = R.string.more,
//                resume = Duration.ZERO,
//                icon = Icons.Default.MoreVert,
//                onClick = { moreOnClick.invoke() },
//                modifier = Modifier.onFocusChanged(onFocused),
//            )
//        }
    }
}

@Preview(widthDp = 800)
@Composable
private fun ImageControlsOverlayPreview() {
    WholphinTheme {
        ImageControlsOverlay(
            slideshowEnabled = true,
            slideshowControls =
                object : SlideshowControls {
                    override fun startSlideshow() {
                    }

                    override fun stopSlideshow() {
                    }
                },
            isImageClip = false,
            onZoom = {},
            onRotate = {},
            onReset = {},
            moreOnClick = {},
            isPlaying = false,
            playPauseOnClick = {},
            bringIntoViewRequester = null,
            onShowFilterDialogClick = {},
            onDismiss = {},
            modifier = Modifier,
        )
    }
}
