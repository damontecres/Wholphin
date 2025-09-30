package com.github.damontecres.dolphin.ui.data

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.dolphin.ui.components.ScrollableDialog
import com.github.damontecres.dolphin.ui.isNotNullOrBlank

data class ItemDetailsDialogInfo(
    val title: String,
    val overview: String?,
    val files: List<String>,
)

@Composable
fun ItemDetailsDialog(
    info: ItemDetailsDialogInfo,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollableDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
    ) {
        item {
            Text(
                text = info.title,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (info.overview.isNotNullOrBlank()) {
            item {
                Text(
                    text = info.overview,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (info.files.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
            }
        }
        items(info.files) { file ->
            Text(
                text = "- $file",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
