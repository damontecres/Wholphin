package com.github.damontecres.wholphin.ui.slideshow

import android.view.Gravity
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.VideoFilter
import com.github.damontecres.wholphin.ui.components.SliderBar
import com.github.damontecres.wholphin.ui.components.SliderColors
import com.github.damontecres.wholphin.ui.theme.WholphinTheme

const val DRAG_THROTTLE_DELAY = 50L

@Composable
fun ImageFilterSliders(
    filter: VideoFilter,
    showVideoOptions: Boolean,
    showSaveButton: Boolean,
    showSaveGalleryButton: Boolean,
    onChange: (VideoFilter) -> Unit,
    onClickSave: () -> Unit,
    onClickSaveGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        item {
            SliderBarRow(
                title = R.string.brightness,
                value = filter.brightness,
                min = 0,
                max = 200,
                onChange = { onChange.invoke(filter.copy(brightness = it)) },
                valueFormater = { "$it%" },
            )
        }
        item {
            SliderBarRow(
                title = R.string.contrast,
                value = filter.contrast,
                min = 0,
                max = 200,
                onChange = { onChange.invoke(filter.copy(contrast = it)) },
                valueFormater = { "$it%" },
            )
        }
        item {
            SliderBarRow(
                title = R.string.saturation,
                value = filter.saturation,
                min = 0,
                max = 200,
                onChange = { onChange.invoke(filter.copy(saturation = it)) },
                valueFormater = { "$it%" },
            )
        }
        if (showVideoOptions) {
            item {
                SliderBarRow(
                    title = R.string.hue,
                    value = filter.hue,
                    min = 0,
                    max = 360,
                    onChange = { onChange.invoke(filter.copy(hue = it)) },
                    valueFormater = { "$it\u00b0" },
                )
            }
        }
        item {
            SliderBarRow(
                title = R.string.red,
                value = filter.red,
                min = 0,
                max = 200,
                onChange = { onChange.invoke(filter.copy(red = it)) },
                valueFormater = { "$it%" },
                colors =
                    SliderColors(
                        activeFocused = Color.Red.copy(alpha = .75f),
                        activeUnfocused = Color.Red.copy(alpha = .75f),
                        inactiveFocused = Color.Red.copy(alpha = .33f),
                        inactiveUnfocused = Color.Red.copy(alpha = .33f),
                    ),
            )
        }
        item {
            SliderBarRow(
                title = R.string.green,
                value = filter.green,
                min = 0,
                max = 200,
                onChange = { onChange.invoke(filter.copy(green = it)) },
                valueFormater = { "$it%" },
                colors =
                    SliderColors(
                        activeFocused = Color.Green.copy(alpha = .75f),
                        activeUnfocused = Color.Green.copy(alpha = .75f),
                        inactiveFocused = Color.Green.copy(alpha = .33f),
                        inactiveUnfocused = Color.Green.copy(alpha = .33f),
                    ),
            )
        }
        item {
            SliderBarRow(
                title = R.string.blue,
                value = filter.blue,
                min = 0,
                max = 200,
                onChange = { onChange.invoke(filter.copy(blue = it)) },
                valueFormater = { "$it%" },
                colors =
                    SliderColors(
                        activeFocused = Color.Blue.copy(alpha = .75f),
                        activeUnfocused = Color.Blue.copy(alpha = .75f),
                        inactiveFocused = Color.Blue.copy(alpha = .33f),
                        inactiveUnfocused = Color.Blue.copy(alpha = .33f),
                    ),
            )
        }
        if (showVideoOptions) {
            item {
                SliderBarRow(
                    title = R.string.blur,
                    value = filter.blur,
                    min = 0,
                    max = 250,
                    onChange = { onChange.invoke(filter.copy(blur = it)) },
                    valueFormater = { "${it}px" },
                )
            }
        }
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier,
                ) {
                    if (showSaveButton) {
                        Button(
                            onClick = onClickSave,
                        ) {
                            Text(text = stringResource(R.string.save))
                        }
                    }
                    if (showSaveGalleryButton) {
                        Button(
                            onClick = onClickSaveGallery,
                        ) {
                            Text(text = stringResource(R.string.save_for_album))
                        }
                    }
                    Button(
                        onClick = { onChange(VideoFilter()) },
                    ) {
                        Text(text = stringResource(R.string.reset))
                    }
                }
            }
        }
    }
}

@Composable
fun SliderBarRow(
    @StringRes title: Int,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
    valueFormater: (Int) -> String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    interval: Int = 1,
    colors: SliderColors = SliderColors.default(),
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(title),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(96.dp),
        )
        SliderBar(
            value = value.toLong(),
            min = min.toLong(),
            max = max.toLong(),
            interval = interval,
            onChange = {
                onChange.invoke(it.toInt())
            },
            colors = colors,
            interactionSource = interactionSource,
            enableWrapAround = true,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueFormater(value),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
fun ImageFilterDialog(
    filter: VideoFilter,
    showVideoOptions: Boolean,
    showSaveGalleryButton: Boolean,
    onChange: (VideoFilter) -> Unit,
    onClickSave: () -> Unit,
    onClickSaveGallery: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.let { window ->
            window.setGravity(Gravity.TOP or Gravity.END)
            window.setDimAmount(0f)
        }

        Box(
            modifier =
                modifier
                    .wrapContentSize()
                    .padding(8.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .4f))
                    .fillMaxWidth(.4f),
        ) {
            ImageFilterSliders(
                filter = filter,
                showVideoOptions = showVideoOptions,
                showSaveButton = true,
                showSaveGalleryButton = showSaveGalleryButton,
                onChange = onChange,
                onClickSave = onClickSave,
                onClickSaveGallery = onClickSaveGallery,
                modifier = Modifier.padding(8.dp),
            )
        }
    }
}

@Preview
@Composable
private fun ImageFilterSlidersPreview() {
    WholphinTheme {
        ImageFilterSliders(
            filter = VideoFilter(),
            showVideoOptions = true,
            onChange = {},
            onClickSave = {},
            onClickSaveGallery = {},
            showSaveButton = true,
            showSaveGalleryButton = true,
            modifier = Modifier.padding(8.dp),
        )
    }
}
