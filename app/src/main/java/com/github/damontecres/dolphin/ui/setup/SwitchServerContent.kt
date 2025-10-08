package com.github.damontecres.dolphin.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.dolphin.ui.components.BasicDialog
import com.github.damontecres.dolphin.ui.components.EditTextBox
import com.github.damontecres.dolphin.ui.nav.Destination

@Composable
fun SwitchServerContent(
    modifier: Modifier = Modifier,
    viewModel: SwitchUserViewModel = hiltViewModel(),
) {
    val currentServer = viewModel.serverRepository.currentServer
    val servers by viewModel.servers.observeAsState(listOf())
    val serverStatus by viewModel.serverStatus.observeAsState(mapOf())

    var showAddServer by remember { mutableStateOf(false) }

    Box(
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth(.5f)
                    .align(Alignment.Center)
                    .padding(16.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        shape = RoundedCornerShape(16.dp),
                    ),
        ) {
            Text(
                text = "Select Server",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ServerList(
                servers = servers,
                connectionStatus = serverStatus,
                onSwitchServer = {
                    viewModel.addServer(it.url) {
                        viewModel.navigationManager.navigateTo(Destination.UserList)
                    }
                },
                onAddServer = {
                    showAddServer = true
                },
                onRemoveServer = {
                    viewModel.removeServer(it)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                            shape = RoundedCornerShape(16.dp),
                        ),
            )
        }

        if (showAddServer) {
            var url by remember { mutableStateOf("") }
            val submit = {
                showAddServer = false
                viewModel.addServer(url) {
                    viewModel.navigationManager.navigateTo(Destination.UserList)
                }
            }
            BasicDialog(
                onDismissRequest = { showAddServer = false },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(16.dp),
                ) {
                    Text(
                        text = "Enter Server URL",
                    )
                    EditTextBox(
                        value = url,
                        onValueChange = { url = it },
                        keyboardOptions =
                            KeyboardOptions(
                                capitalization = KeyboardCapitalization.None,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Uri,
                            ),
                        keyboardActions =
                            KeyboardActions(
                                onDone = { submit.invoke() },
                            ),
                        modifier = Modifier,
                    )
                    Button(
                        onClick = { submit.invoke() },
                        modifier = Modifier,
                    ) {
                        Text(text = "Submit")
                    }
                }
            }
        }
    }
}
