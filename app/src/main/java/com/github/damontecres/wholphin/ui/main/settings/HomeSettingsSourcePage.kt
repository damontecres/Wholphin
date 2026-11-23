package com.github.damontecres.wholphin.ui.main.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.HomePageSettingsSource
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.tryRequestFocus

@Composable
fun HomeSettingsSourcePage(
    source: HomePageSettingsSource,
    onClickSave: () -> Unit,
    onClickLoad: () -> Unit,
    onClickLoadWeb: () -> Unit,
    onClickReset: () -> Unit,
    serverPluginActive: Boolean,
    onClickLoadPlugin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstFocus: FocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                modifier
                    .fillMaxHeight()
                    .focusRestorer(firstFocus),
        ) {
            item {
                HomeSettingsListItem(
                    selected = false,
                    headlineText = stringResource(R.string.load_from_server),
                    leadingContent = {
                        Text(
                            text = stringResource(R.string.fa_cloud_arrow_down),
                            fontFamily = FontAwesome,
                        )
                    },
                    onClick = onClickLoad,
                    modifier = Modifier,
                )
            }
            if (serverPluginActive) {
                item {
                    HomeSettingsListItem(
                        selected = false,
                        headlineText = stringResource(R.string.load_from_server_plugin),
                        leadingContent = {
                            Text(
                                text = stringResource(R.string.fa_download),
                                fontFamily = FontAwesome,
                            )
                        },
                        onClick = onClickLoadPlugin,
                        modifier = Modifier,
                    )
                }
            }
            item {
                HomeSettingsListItem(
                    selected = false,
                    headlineText = stringResource(R.string.load_from_web_client),
                    leadingContent = {
                        Text(
                            text = stringResource(R.string.fa_download),
                            fontFamily = FontAwesome,
                        )
                    },
                    onClick = onClickLoadWeb,
                    modifier = Modifier,
                )
            }
            item {
                HomeSettingsListItem(
                    selected = false,
                    headlineText = stringResource(R.string.reset),
                    leadingContent = {
                        Text(
                            text = stringResource(R.string.fa_arrows_rotate),
                            fontFamily = FontAwesome,
                        )
                    },
                    onClick = onClickReset,
                    modifier = Modifier,
                )
            }
        }
    }
}
