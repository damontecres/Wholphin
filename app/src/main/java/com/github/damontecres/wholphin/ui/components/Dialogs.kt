package com.github.damontecres.wholphin.ui.components

import android.view.KeyEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Parameters for rendering a [DialogPopup]
 */
data class DialogParams(
    val fromLongClick: Boolean,
    val title: String,
    val items: List<DialogItem>,
)

sealed interface DialogItemEntry

data object DialogItemDivider : DialogItemEntry

data class DialogItem(
    val headlineContent: @Composable () -> Unit,
    val onClick: () -> Unit,
    val overlineContent: @Composable (() -> Unit)? = null,
    val supportingContent: @Composable (() -> Unit)? = null,
    val leadingContent: @Composable (BoxScope.() -> Unit)? = null,
    val trailingContent: @Composable (() -> Unit)? = null,
    val enabled: Boolean = true,
) : DialogItemEntry {
    constructor(
        text: String,
        @StringRes iconStringRes: Int,
        onClick: () -> Unit,
    ) : this(
        headlineContent = {
            Text(
                text = text,
//                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        leadingContent = {
            Text(
                text = stringResource(id = iconStringRes),
//                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontFamily = FontAwesome,
            )
        },
        onClick = onClick,
    )

    constructor(
        text: String,
        icon: ImageVector,
        iconColor: Color? = null,
        onClick: () -> Unit,
    ) : this(
        headlineContent = {
            Text(
                text = text,
//                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        leadingContent = {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = iconColor ?: LocalContentColor.current,
            )
        },
        onClick = onClick,
    )

    constructor(
        text: String,
        onClick: () -> Unit,
    ) : this(
        headlineContent = {
            Text(
                text = text,
//                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
        onClick = onClick,
    )

    companion object {
        fun divider(): DialogItemEntry = DialogItemDivider
    }
}

/**
 * Show a dialog with a list of entries.
 *
 * @param waitToLoad items start as disabled for about ~1s, which is useful if the dialog spawned from a long press
 */
@Composable
fun DialogPopup(
    showDialog: Boolean,
    title: String,
    dialogItems: List<DialogItemEntry>,
    onDismissRequest: () -> Unit,
    dismissOnClick: Boolean = true,
    waitToLoad: Boolean = true,
    properties: DialogProperties = DialogProperties(),
    elevation: Dp = 3.dp,
) {
    var waiting by remember { mutableStateOf(waitToLoad) }
    if (showDialog) {
        if (waitToLoad) {
            LaunchedEffect(Unit) {
                // This is a hack because a long click will propagate here and click the first list item
                // So this disables the list items assuming the user will stop pressing when the dialog appears
                // This is also bypassed in the code below if the user releases the enter/d-pad center button
                waiting = true
                delay(1000)
                waiting = false
            }
        } else {
            waiting = false
        }
        Dialog(
            onDismissRequest = onDismissRequest,
            properties = properties,
        ) {
            val elevatedContainerColor =
                MaterialTheme.colorScheme.surfaceColorAtElevation(elevation)
            LazyColumn(
                modifier =
                    Modifier
//                        .widthIn(min = 520.dp, max = 300.dp)
//                        .dialogFocusable()
                        .graphicsLayer {
                            this.clip = true
                            this.shape = RoundedCornerShape(28.0.dp)
                        }.drawBehind { drawRect(color = elevatedContainerColor) }
                        .padding(PaddingValues(24.dp))
                        .onKeyEvent { event ->
                            val code = event.nativeKeyEvent.keyCode
                            if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP &&
                                code in
                                setOf(
                                    KeyEvent.KEYCODE_ENTER,
                                    KeyEvent.KEYCODE_DPAD_CENTER,
                                    KeyEvent.KEYCODE_NUMPAD_ENTER,
                                )
                            ) {
                                waiting = false
                            }
                            false
                        },
            ) {
                stickyHeader {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                items(dialogItems) {
                    when (it) {
                        is DialogItemDivider -> HorizontalDivider(Modifier.height(16.dp))

                        is DialogItem ->
                            ListItem(
                                selected = false,
                                enabled = !waiting && it.enabled,
                                onClick = {
                                    if (dismissOnClick) {
                                        onDismissRequest.invoke()
                                    }
                                    it.onClick.invoke()
                                },
                                headlineContent = it.headlineContent,
                                overlineContent = it.overlineContent,
                                supportingContent = it.supportingContent,
                                leadingContent = it.leadingContent,
                                trailingContent = it.trailingContent,
                                modifier = Modifier,
                            )
                    }
                }
            }
        }
    }
}

@Composable
fun DialogPopup(
    params: DialogParams,
    onDismissRequest: () -> Unit,
    dismissOnClick: Boolean = true,
    properties: DialogProperties = DialogProperties(),
    elevation: Dp = 3.dp,
) = DialogPopup(
    showDialog = true,
    waitToLoad = params.fromLongClick,
    title = params.title,
    dialogItems = params.items,
    onDismissRequest = onDismissRequest,
    dismissOnClick = dismissOnClick,
    properties = properties,
    elevation = elevation,
)

/**
 * A dialog that can be scrolled, typically for longer text content
 */
@Composable
fun ScrollableDialog(
    onDismissRequest: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    val scrollAmount = 100f
    val columnState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun scroll(reverse: Boolean = false) {
        scope.launch(ExceptionHandler()) {
            columnState.scrollBy(if (reverse) -scrollAmount else scrollAmount)
        }
    }
    Dialog(
        onDismissRequest = onDismissRequest,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
            ),
    ) {
        LazyColumn(
            state = columnState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
            modifier =
                Modifier
                    .width(600.dp)
                    .heightIn(max = 380.dp)
                    .focusable()
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                        shape = RoundedCornerShape(8.dp),
                    ).onKeyEvent {
                        if (it.type == KeyEventType.KeyUp) {
                            return@onKeyEvent false
                        }
                        if (it.key == Key.DirectionDown) {
                            scroll(false)
                            return@onKeyEvent true
                        }
                        if (it.key == Key.DirectionUp) {
                            scroll(true)
                            return@onKeyEvent true
                        }
                        return@onKeyEvent false
                    },
        )
    }
}

/**
 * Shows a basic dialog
 */
@Composable
fun BasicDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    elevation: Dp = 3.dp,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties,
    ) {
        Box(
            modifier =
                Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(elevation),
                        shape = RoundedCornerShape(8.dp),
                    ),
        ) {
            content()
        }
    }
}

/**
 * Shows a confirmation dialog
 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    elevation: Dp = 3.dp,
) = BasicDialog(
    onDismissRequest = onCancel,
    properties = properties,
    elevation = elevation,
    content = {
        ConfirmDialogContent(title, body, onCancel, onConfirm, Modifier)
    },
)

/**
 * Content for a confirmation dialog
 */
@Composable
fun ConfirmDialogContent(
    title: String,
    body: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(16.dp),
        modifier = modifier,
    ) {
        item {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillParentMaxWidth(),
            )
        }
        body?.let {
            item {
                Text(
                    text = body,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        item {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = onCancel,
                ) {
                    Text(
                        text = stringResource(R.string.cancel),
                    )
                }
                Button(
                    onClick = onConfirm,
                ) {
                    Text(
                        text = stringResource(R.string.confirm),
                    )
                }
            }
        }
    }
}
