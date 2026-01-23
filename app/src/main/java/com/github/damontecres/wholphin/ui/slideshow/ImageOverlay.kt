package com.github.damontecres.wholphin.ui.slideshow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun ImageOverlay(
    onDismiss: () -> Unit,
    player: Player,
    slideshowControls: SlideshowControls,
    slideshowEnabled: Boolean,
    position: Int,
    count: Int,
    image: ImageState,
    onClickItem: (BaseItem) -> Unit,
    onLongClickItem: (BaseItem) -> Unit,
    onZoom: (Float) -> Unit,
    onRotate: (Int) -> Unit,
    onReset: () -> Unit,
    onShowFilterDialogClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf<DialogParams?>(null) }

    val moreDialogParams =
        remember {
            DialogParams(
                fromLongClick = false,
                title = "TODO",
                items =
                    listOf(
                        DialogItem(
                            headlineContent = {
                                Text(
                                    text =
                                        if (slideshowEnabled) {
                                            stringResource(R.string.stop_slideshow)
                                        } else {
                                            stringResource(R.string.play_slideshow)
                                        },
                                )
                            },
                            leadingContent = {
                                val icon =
                                    if (slideshowEnabled) {
                                        R.drawable.baseline_pause_24
                                    } else {
                                        R.drawable.baseline_play_arrow_24
                                    }
                                Icon(
                                    painter = painterResource(icon),
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                if (slideshowEnabled) {
                                    slideshowControls.stopSlideshow()
                                } else {
                                    slideshowControls.startSlideshow()
                                }
                            },
                        ),
                        DialogItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.filter),
                                )
                            },
                            leadingContent = {
                                Text(
                                    text = stringResource(R.string.fa_sliders),
                                    fontFamily = FontAwesome,
                                )
                            },
                            onClick = onShowFilterDialogClick,
                        ),
                    ),
            )
        }

    val horizontalPadding = 16.dp
    LazyColumn(
        contentPadding =
            PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 16.dp,
                bottom = 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        item {
            ImageDetailsHeader(
                onDismiss = onDismiss,
                slideshowEnabled = slideshowEnabled,
                slideshowControls = slideshowControls,
                player = player,
                image = image,
                position = position,
                count = count,
                moreOnClick = {
                    showDialog = moreDialogParams
                },
                onZoom = onZoom,
                onRotate = onRotate,
                onReset = onReset,
                onShowFilterDialogClick = onShowFilterDialogClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    showDialog?.let { params ->
        DialogPopup(
            showDialog = true,
            title = params.title,
            dialogItems = params.items,
            onDismissRequest = { showDialog = null },
            waitToLoad = params.fromLongClick,
        )
    }
}
