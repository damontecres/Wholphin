package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import org.jellyfin.sdk.model.api.PublicSystemInfo

@Composable
fun SwitchServerContent(
    modifier: Modifier = Modifier,
    viewModel: SwitchServerViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.observeAsState(listOf())
    val serverStatus by viewModel.serverStatus.observeAsState(mapOf())

    val discoveredServers by viewModel.discoveredServers.observeAsState(listOf())

    var showAddServer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.init()
        viewModel.discoverServers()
    }

    Box(
        modifier = modifier,
    ) {
        Row(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .padding(32.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                            shape = RoundedCornerShape(16.dp),
                        ),
            ) {
                Text(
                    text = stringResource(R.string.select_server),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                ServerList(
                    servers = servers,
                    connectionStatus = serverStatus,
                    onSwitchServer = {
                        viewModel.switchServer(it)
                    },
                    onTestServer = {
                        viewModel.testServer(it)
                    },
                    onAddServer = {
                        showAddServer = true
                    },
                    onRemoveServer = {
                        viewModel.removeServer(it)
                    },
                    allowAdd = true,
                    allowDelete = true,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                                shape = RoundedCornerShape(16.dp),
                            ),
                )
            }
            // Discover
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                            shape = RoundedCornerShape(16.dp),
                        ),
            ) {
                Text(
                    text = stringResource(R.string.discovered_servers),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (discoveredServers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.searching),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    ServerList(
                        servers = discoveredServers,
                        connectionStatus =
                            discoveredServers
                                .map { it.id }
                                .associateWith { ServerConnectionStatus.Success(PublicSystemInfo()) },
                        onSwitchServer = {
                            viewModel.addServer(it.url)
                        },
                        onTestServer = {
                            viewModel.testServer(it)
                        },
                        onAddServer = {},
                        onRemoveServer = {},
                        allowAdd = false,
                        allowDelete = false,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                                    shape = RoundedCornerShape(16.dp),
                                ),
                    )
                }
            }
        }

        if (showAddServer) {
            LaunchedEffect(Unit) {
                viewModel.clearAddServerState()
            }
            val state by viewModel.addServerState.observeAsState(LoadingState.Pending)
            var url by remember { mutableStateOf("") }
            val submit = {
                viewModel.addServer(url)
            }
            BasicDialog(
                onDismissRequest = {
                    showAddServer = false
                    viewModel.clearAddServerState()
                },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                elevation = 10.dp,
            ) {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth(.4f),
                ) {
                    Text(
                        text = stringResource(R.string.enter_server_url),
                    )
                    EditTextBox(
                        value = url,
                        onValueChange = {
                            url = it
                            viewModel.clearAddServerState()
                        },
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
                        modifier =
                            Modifier
                                .focusRequester(focusRequester)
                                .fillMaxWidth(),
                    )
                    when (val st = state) {
                        is LoadingState.Error -> {
                            Text(
                                text =
                                    st.message ?: st.exception?.localizedMessage
                                        ?: "An error occurred",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }

                        else -> {}
                    }
                    Button(
                        onClick = { submit.invoke() },
                        enabled = url.isNotNullOrBlank() && state == LoadingState.Pending,
                        modifier = Modifier,
                    ) {
                        if (state == LoadingState.Loading) {
                            CircularProgress(Modifier.size(32.dp))
                        } else {
                            Text(text = stringResource(R.string.submit))
                        }
                    }
                }
            }
        }
    }
}
