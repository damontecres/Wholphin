package com.github.damontecres.wholphin.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ListItem
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.main.settings.TitleText
import com.github.damontecres.wholphin.ui.preferences.SwitchColors
import com.github.damontecres.wholphin.ui.titleStringRes
import org.jellyfin.sdk.model.api.BaseItemKind

@Composable
fun SearchTypeOptionsDialog(
    onDismissRequest: () -> Unit,
    searchableTypes: List<BaseItemKind>,
    excludedSearchableTypes: List<BaseItemKind>,
    discoverAvailable: Boolean,
    discoverEnabled: Boolean,
    onClick: (BaseItemKind) -> Unit,
    onClickDiscover: () -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
        elevation = 3.dp,
    ) {
        SearchTypeOptionsDialogContent(
            searchableTypes = searchableTypes,
            excludedSearchableTypes = excludedSearchableTypes,
            discoverAvailable = discoverAvailable,
            discoverEnabled = discoverEnabled,
            onClick = onClick,
            onClickDiscover = onClickDiscover,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
fun SearchTypeOptionsDialogContent(
    searchableTypes: List<BaseItemKind>,
    excludedSearchableTypes: List<BaseItemKind>,
    discoverAvailable: Boolean,
    discoverEnabled: Boolean,
    onClick: (BaseItemKind) -> Unit,
    onClickDiscover: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        TitleText(stringResource(R.string.include_types))

        LazyColumn {
            items(searchableTypes) { searchableType ->
                val checked = searchableType !in excludedSearchableTypes
                ListItem(
                    enabled = true,
                    selected = false,
                    onClick = { onClick.invoke(searchableType) },
                    headlineContent = {
                        Text(stringResource(searchableType.titleStringRes))
                    },
                    trailingContent = {
                        Switch(
                            checked = checked,
                            onCheckedChange = {},
                            colors = SwitchColors(),
                        )
                    },
                )
            }
            if (discoverAvailable) {
                item {
                    ListItem(
                        enabled = true,
                        selected = false,
                        onClick = onClickDiscover,
                        headlineContent = {
                            Text(stringResource(R.string.discover))
                        },
                        trailingContent = {
                            Switch(
                                checked = discoverEnabled,
                                onCheckedChange = {},
                                colors = SwitchColors(),
                            )
                        },
                    )
                }
            }
        }
    }
}
