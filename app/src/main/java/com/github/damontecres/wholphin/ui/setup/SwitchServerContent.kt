package com.github.damontecres.wholphin.ui.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.TextButton
import com.github.damontecres.wholphin.ui.dimAndBlur
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun SwitchServerContent(
    modifier: Modifier = Modifier,
    viewModel: SwitchServerViewModel = hiltViewModel(),
) {
    val servers by viewModel.servers.observeAsState(listOf())
    val serverStatus by viewModel.serverStatus.observeAsState(mapOf())

    var showAddServer by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<com.github.damontecres.wholphin.data.model.JellyfinServer?>(null) }

    LaunchedEffect(Unit) {
        viewModel.init()
    }

    Box(
        modifier = modifier.dimAndBlur(showAddServer || showDeleteDialog != null),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Center the content like the Select User screen
                    .align(Alignment.Center)
                    .padding(16.dp),
        ) {
            // Match SwitchUser header height (title + subtitle) to align icons vertically across screens
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.select_server),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Invisible subtitle placeholder to mirror the server name line on the Select User screen
                Text(
                    text = "Server placeholder",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.Transparent,
                )
            }

            // Horizontal scrollable list of server icons - centered
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val focusRequester = remember { FocusRequester() }
                val firstServerFocus = remember { FocusRequester() }
                if (servers.isNotEmpty()) {
                    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                    modifier =
                        Modifier
                            .wrapContentWidth()
                            .focusRestorer(firstServerFocus)
                            .focusRequester(focusRequester),
                ) {
                    itemsIndexed(servers) { index, server ->
                        val status = serverStatus[server.id] ?: ServerConnectionStatus.Pending
                        ServerIconCard(
                            server = server,
                            connectionStatus = status,
                            isCurrentServer = false, // TODO: Determine current server if needed
                            onClick = {
                                when (status) {
                                    is ServerConnectionStatus.Success -> {
                                        viewModel.switchServer(server)
                                    }

                                    ServerConnectionStatus.Pending -> {
                                        // Do nothing while pending
                                    }

                                    is ServerConnectionStatus.Error -> {
                                        viewModel.testServer(server)
                                    }
                                }
                            },
                            onLongClick = {
                                showDeleteDialog = server
                            },
                            allowDelete = true,
                            modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(firstServerFocus)),
                        )
                    }
                    // Add Server card - always rightmost
                    item {
                        AddServerCard(
                            onClick = { showAddServer = true },
                        )
                    }
                }
            }
            // Non-focusable spacer to mirror the space occupied by the "Switch Servers" button
            Spacer(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                // approximate TV button height
            )
        }

        // Delete server dialog
        showDeleteDialog?.let { server ->
            DialogPopup(
                showDialog = true,
                title = server.name ?: server.url,
                dialogItems =
                    listOf(
                        DialogItem(
                            stringResource(R.string.switch_servers),
                            R.string.fa_arrow_left_arrow_right,
                        ) {
                            viewModel.switchServer(server)
                            showDeleteDialog = null
                        },
                        DialogItem(
                            stringResource(R.string.delete),
                            Icons.Default.Delete,
                            Color.Red.copy(alpha = .8f),
                        ) {
                            viewModel.removeServer(server)
                            showDeleteDialog = null
                        },
                    ),
                onDismissRequest = { showDeleteDialog = null },
                dismissOnClick = true,
                waitToLoad = true,
                properties = DialogProperties(),
                elevation = 5.dp,
            )
        }

        if (showAddServer) {
            var showEnterAddress by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                viewModel.clearAddServerState()
                if (!showEnterAddress) {
                    viewModel.discoverServers()
                }
            }

            val discoveredServers by viewModel.discoveredServers.observeAsState(listOf())

            // Filter out duplicates within the discovered servers list (same URL appearing multiple times)
            val filteredDiscoveredServers =
                remember(discoveredServers) {
                    val seenUrls = mutableSetOf<String>()
                    discoveredServers.filter { server ->
                        val normalizedUrl = server.url.lowercase().trim()
                        if (normalizedUrl in seenUrls) {
                            false // Duplicate, filter it out
                        } else {
                            seenUrls.add(normalizedUrl)
                            true // First occurrence, keep it
                        }
                    }
                }

            val firstDiscoveredServerFocusRequester = remember { FocusRequester() }

            // Default focus to first discovered server if available
            LaunchedEffect(filteredDiscoveredServers.isNotEmpty(), showEnterAddress) {
                if (!showEnterAddress && filteredDiscoveredServers.isNotEmpty()) {
                    firstDiscoveredServerFocusRequester.tryRequestFocus()
                }
            }

            BasicDialog(
                onDismissRequest = {
                    showAddServer = false
                    showEnterAddress = false
                    viewModel.clearAddServerState()
                },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                elevation = 10.dp,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier =
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth(.4f),
                ) {
                    if (!showEnterAddress) {
                        // Show discovered servers first
                        Text(
                            text = stringResource(R.string.discovered_servers),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        if (filteredDiscoveredServers.isEmpty() && discoveredServers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.searching),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else if (filteredDiscoveredServers.isEmpty()) {
                            Text(
                                text = stringResource(R.string.no_servers_found),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        } else {
                            LazyColumn(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 300.dp),
                            ) {
                                items(
                                    filteredDiscoveredServers.size,
                                    key = { filteredDiscoveredServers[it].url },
                                ) { index ->
                                    val server = filteredDiscoveredServers[index]
                                    val focusRequester =
                                        if (index == 0) {
                                            firstDiscoveredServerFocusRequester
                                        } else {
                                            remember { FocusRequester() }
                                        }

                                    ListItem(
                                        enabled = true,
                                        selected = false,
                                        headlineContent = {
                                            Text(
                                                text = server.name?.ifBlank { null } ?: server.url,
                                                style = MaterialTheme.typography.bodyLarge,
                                            )
                                        },
                                        supportingContent = {
                                            Text(
                                                text = server.url,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        },
                                        onClick = {
                                            viewModel.addServer(server.url)
                                        },
                                        modifier = Modifier.focusRequester(focusRequester),
                                    )
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                showEnterAddress = true
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Text(text = stringResource(R.string.enter_server_address))
                        }
                    } else {
                        // Show enter server address form
                        val state by viewModel.addServerState.observeAsState(LoadingState.Pending)
                        var url by remember { mutableStateOf("") }
                        val submit = {
                            viewModel.addServer(url)
                        }
                        val textBoxFocusRequester = remember { FocusRequester() }

                        LaunchedEffect(Unit) {
                            textBoxFocusRequester.tryRequestFocus()
                        }

                        Text(
                            text = stringResource(R.string.enter_server_url),
                        )
                        EditTextBox(
                            value = url,
                            onValueChange = { url = it },
                            keyboardOptions =
                                KeyboardOptions(
                                    capitalization = KeyboardCapitalization.None,
                                    autoCorrectEnabled = false,
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go,
                                ),
                            keyboardActions =
                                KeyboardActions(
                                    onGo = { submit.invoke() },
                                ),
                            modifier =
                                Modifier
                                    .focusRequester(textBoxFocusRequester)
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
                        TextButton(
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
}
