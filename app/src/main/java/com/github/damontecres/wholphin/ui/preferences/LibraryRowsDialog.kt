package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.recommendedRowDefaultViewOptions
import com.github.damontecres.wholphin.ui.components.recommendedRowTitles
import com.github.damontecres.wholphin.ui.main.settings.HomeRowSettings
import com.github.damontecres.wholphin.ui.main.settings.Options
import com.github.damontecres.wholphin.ui.nav.ServerNavDrawerItem
import org.jellyfin.sdk.model.api.CollectionType

/**
 * Lists the rows on a library's recommended tab. Choosing one shows the same row settings used for
 * home page rows (size, image type, showcase, etc).
 *
 * @param rowSettings the saved settings for this library's rows, by row key
 * @param rowKey gets the key for a row from its title
 * @param onSave saves a row's settings, or null to go back to the default
 */
@Composable
fun LibraryRowsDialog(
    library: ServerNavDrawerItem,
    rowSettings: Map<String, HomeRowViewOptions>,
    rowKey: (Int) -> String,
    onSave: (rowKey: String, viewOptions: HomeRowViewOptions?) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val rowTitles = remember(library.type) { recommendedRowTitles(library.type) }
    val defaultViewOptions = remember(library.type) { recommendedRowDefaultViewOptions(library.type) }
    var selectedRow by remember { mutableStateOf<Int?>(null) }

    BasicDialog(
        onDismissRequest = {
            // Back from a row's settings returns to the list of rows
            if (selectedRow != null) selectedRow = null else onDismissRequest.invoke()
        },
        elevation = 3.dp,
    ) {
        val row = selectedRow
        if (row == null) {
            Column(
                modifier =
                    Modifier
                        .padding(16.dp)
                        .width(420.dp),
            ) {
                Text(
                    text = stringResource(R.string.customize_rows_for, library.name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                LazyColumn {
                    items(rowTitles) { title ->
                        val showcase = rowSettings[rowKey(title)]?.showcase == true
                        ListItem(
                            selected = false,
                            onClick = { selectedRow = title },
                            headlineContent = { Text(stringResource(title)) },
                            supportingContent =
                                if (showcase) {
                                    { Text(stringResource(R.string.showcase)) }
                                } else {
                                    null
                                },
                        )
                    }
                }
            }
        } else {
            val key = rowKey(row)
            val isEpisodeRow =
                library.type == CollectionType.TVSHOWS &&
                    (row == R.string.continue_watching || row == R.string.next_up)
            HomeRowSettings(
                title = stringResource(row),
                config = null,
                preferenceOptions = if (isEpisodeRow) Options.OPTIONS_EPISODES else Options.OPTIONS,
                viewOptions = rowSettings[key] ?: defaultViewOptions,
                onViewOptionsChange = { newViewOptions ->
                    onSave.invoke(key, newViewOptions.takeIf { it != defaultViewOptions })
                },
                onConfigChange = {},
                onConfigAction = {},
                onApplyApplyAll = {},
                defaultViewOptions = defaultViewOptions,
                modifier =
                    Modifier
                        .padding(16.dp)
                        .width(420.dp),
            )
        }
    }
}
