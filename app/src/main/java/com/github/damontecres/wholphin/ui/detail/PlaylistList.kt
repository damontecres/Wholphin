package com.github.damontecres.wholphin.ui.detail

import android.view.Gravity
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.PlaylistInfo
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.Button
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.SearchEditTextBox
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.MediaType
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun PlaylistList(
    playlists: List<PlaylistInfo>,
    onClick: (PlaylistInfo) -> Unit,
    createEnabled: Boolean,
    onCreatePlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(playlists) { playlist ->
            ListItem(
                selected = false,
                enabled = true,
                headlineContent = {
                    Text(
                        text = playlist.name,
                    )
                },
                supportingContent = {
                    val id =
                        when (playlist.mediaType) {
                            MediaType.UNKNOWN -> R.string.unknown
                            MediaType.VIDEO -> R.string.video
                            MediaType.AUDIO -> R.string.audio
                            MediaType.PHOTO -> R.string.photos
                            MediaType.BOOK -> R.string.unknown
                        }
                    Text(
                        text = stringResource(id),
                    )
                },
                trailingContent = {
                    Text(
                        text =
                            pluralStringResource(
                                R.plurals.items,
                                playlist.count,
                                playlist.count,
                            ),
                    )
                },
                onClick = {
                    onClick.invoke(playlist)
                },
                modifier = Modifier,
            )
        }
        if (createEnabled) {
            item {
                HorizontalDivider()
                ListItem(
                    selected = false,
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.create_playlist),
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add",
                        )
                    },
                    onClick = {
                        showCreateDialog = true
                    },
                    modifier = Modifier,
                )
            }
        }
    }

    if (showCreateDialog) {
        BasicDialog(
            onDismissRequest = {
                showCreateDialog = false
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            elevation = 10.dp,
        ) {
            var playlistName by rememberSaveable { mutableStateOf("") }
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
                    text = stringResource(R.string.name),
                )
                EditTextBox(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            autoCorrectEnabled = true,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = {
                                showCreateDialog = false
                                onCreatePlaylist.invoke(playlistName)
                            },
                        ),
                    //                    onKeyboardAction = {
//                        showCreateDialog = false
//                        onCreatePlaylist.invoke(playlistName.text.toString())
//                    },
                    modifier =
                        Modifier
                            .focusRequester(focusRequester)
                            .fillMaxWidth(),
                )
                Button(
                    onClick = {
                        showCreateDialog = false
                        onCreatePlaylist.invoke(playlistName)
                    },
                    enabled = playlistName.isNotNullOrBlank(),
                    modifier = Modifier,
                ) {
                    Text(text = stringResource(R.string.submit))
                }
            }
        }
    }
}

@Composable
fun PlaylistDialog(
    title: String,
    state: PlaylistLoadingState,
    onDismissRequest: () -> Unit,
    onClick: (PlaylistInfo) -> Unit,
    onSearch: (String) -> Unit,
    createEnabled: Boolean,
    onCreatePlaylist: (String) -> Unit,
    elevation: Dp = 3.dp,
) {
    val elevatedContainerColor =
        MaterialTheme.colorScheme.surfaceColorAtElevation(elevation)

    val outerFocusRequester = remember { FocusRequester() }
    val focusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var searchClicked by remember { mutableStateOf(false) }
    var searchHasFocus by remember { mutableStateOf(false) }
    LaunchedEffect(query) {
        val previousQuery = (state as? PlaylistLoadingState.Success)?.query ?: ""
        if (previousQuery != query) {
            delay(750.milliseconds)
            if (searchClicked) {
                searchClicked = false
                return@LaunchedEffect
            }
            onSearch.invoke(query)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        val dialogWindowProvider = LocalView.current.parent as? DialogWindowProvider
        dialogWindowProvider?.window?.setGravity(Gravity.TOP)

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .graphicsLayer {
                        this.clip = true
                        this.shape = RoundedCornerShape(24.0.dp)
                    }.drawBehind { drawRect(color = elevatedContainerColor) }
                    .padding(PaddingValues(16.dp)),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            SearchEditTextBox(
                value = query,
                onValueChange = { query = it },
                onSearchClick = {
                    if (query.isNotBlank()) {
                        searchClicked = true
                        onSearch.invoke(query)
                    }
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .onFocusChanged { searchHasFocus = it.hasFocus }
                        .focusProperties {
                            down = focusRequester
                        },
            )
            LaunchedEffect(Unit) { outerFocusRequester.tryRequestFocus() }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(0.dp)
                        .focusRequester(outerFocusRequester)
                        .focusable(),
            )
            when (val s = state) {
                PlaylistLoadingState.Pending,
                PlaylistLoadingState.Loading,
                -> {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.border,
                        modifier =
                            Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(48.dp),
                    )
                }

                is PlaylistLoadingState.Error -> {
                    ErrorMessage(
                        message = s.message,
                        exception = s.exception,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .focusProperties {
                                    up = searchFocusRequester
                                },
                    )
                }

                is PlaylistLoadingState.Success -> {
                    LaunchedEffect(Unit) { if (!searchHasFocus) focusRequester.tryRequestFocus() }
                    PlaylistList(
                        playlists = s.items,
                        onClick = onClick,
                        createEnabled = createEnabled,
                        onCreatePlaylist = onCreatePlaylist,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .focusProperties {
                                    up = searchFocusRequester
                                },
                    )
                }
            }
        }
    }
}

sealed interface PlaylistLoadingState {
    data object Pending : PlaylistLoadingState

    data object Loading : PlaylistLoadingState

    data class Success(
        val items: List<PlaylistInfo>,
        val query: String,
    ) : PlaylistLoadingState

    data class Error(
        val message: String? = null,
        val exception: Throwable? = null,
    ) : PlaylistLoadingState {
        constructor(exception: Throwable) : this(null, exception)

        val localizedMessage: String =
            listOfNotNull(message, exception?.localizedMessage).joinToString(" - ")
    }
}
