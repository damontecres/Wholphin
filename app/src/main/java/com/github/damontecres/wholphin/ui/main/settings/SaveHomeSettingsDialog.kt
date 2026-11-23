package com.github.damontecres.wholphin.ui.main.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup

@Composable
fun SaveHomeSettingsDialog(
    onDismissRequest: () -> Unit,
    onSaveLocal: () -> Unit,
    onSaveServer: () -> Unit,
    onDiscard: () -> Unit,
) {
    val resources = LocalResources.current
    val params =
        remember(resources) {
            DialogParams(
                fromLongClick = false,
                title = resources.getString(R.string.save),
                items =
                    listOf(
                        DialogItem(
                            text = R.string.save_to_local,
                            iconStringRes = R.string.fa_cloud_arrow_down,
                            onClick = onSaveLocal,
                        ),
                        DialogItem(
                            text = R.string.save_to_server,
                            iconStringRes = R.string.fa_cloud_arrow_up,
                            onClick = onSaveServer,
                        ),
                        DialogItem(
                            text = "Discard",
                            icon = Icons.Default.Delete,
                            iconColor = Color.Red,
                            onClick = onDiscard,
                        ),
                    ),
            )
        }
    DialogPopup(
        params = params,
        onDismissRequest = onDismissRequest,
        elevation = 3.dp,
    )
}
