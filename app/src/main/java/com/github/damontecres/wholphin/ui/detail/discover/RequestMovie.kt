package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.DialogPopupContent
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun RequestMovieDialog(
    loading: LoadingState,
    state: SeerrRequestData,
    request4kEnabled: Boolean,
    movie: MovieDetails,
    onSubmit: (MovieRequest) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        RequestMovie(
            loading = loading,
            state = state,
            request4kEnabled = request4kEnabled,
            movie = movie,
            onSubmit = onSubmit,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
fun RequestMovie(
    loading: LoadingState,
    state: SeerrRequestData,
    request4kEnabled: Boolean,
    movie: MovieDetails,
    onSubmit: (MovieRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (loading) {
        is LoadingState.Error -> {
            ErrorMessage(loading, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
            val requestStr = stringResource(R.string.request)
            val request4kStr = stringResource(R.string.request_4k)
            if (state.profiles.isEmpty() && state.rootFolders.isEmpty() &&
                state.profiles4k.isEmpty() && state.rootFolders4k.isEmpty()
            ) {
                if (request4kEnabled) {
                    val items =
                        remember {
                            listOf(
                                DialogItem(
                                    text = requestStr,
                                    onClick = {
                                        onSubmit.invoke(MovieRequest(movie.id, false, null, null))
                                    },
                                ),
                                DialogItem(
                                    text = request4kStr,
                                    onClick = {
                                        onSubmit.invoke(MovieRequest(movie.id, true, null, null))
                                    },
                                ),
                            )
                        }
                    DialogPopupContent(
                        title = movie.title + " (${movie.releaseDate ?: ""})",
                        dialogItems = items,
                        waiting = false,
                        onDismissRequest = {},
                        dismissOnClick = false,
                        modifier = modifier.focusRequester(focusRequester),
                    )
                } else {
                    LaunchedEffect(Unit) {
                        onSubmit.invoke(MovieRequest(movie.id, false, null, null))
                    }
                }
            } else {
                RequestMovieWithOptions(
                    state = state,
                    request4kEnabled = request4kEnabled,
                    movie = movie,
                    onSubmit = onSubmit,
                    modifier = modifier.focusRequester(focusRequester),
                )
            }
        }
    }
}

@Composable
private fun RequestMovieWithOptions(
    state: SeerrRequestData,
    request4kEnabled: Boolean,
    movie: MovieDetails,
    onSubmit: (MovieRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    var is4k by remember { mutableStateOf(request4kEnabled) }
    var profile by remember(is4k) {
        mutableStateOf(
            if (is4k) {
                state.profiles4k.firstOrNull { it.default } ?: state.profiles4k.first()
            } else {
                state.profiles.firstOrNull { it.default } ?: state.profiles.first()
            },
        )
    }
    var folder by remember(is4k) {
        mutableStateOf(
            if (is4k) {
                state.rootFolders4k.firstOrNull { it.default } ?: state.rootFolders4k.first()
            } else {
                state.rootFolders.firstOrNull { it.default } ?: state.rootFolders.first()
            },
        )
    }
    val profiles = remember(is4k, state) { if (is4k) state.profiles4k else state.profiles }
    val folders = remember(is4k, state) { if (is4k) state.rootFolders4k else state.rootFolders }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = movie.title + " (${movie.releaseDate ?: ""})",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyColumn(
            modifier = Modifier,
        ) {
            item {
                if (request4kEnabled) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ClickSwitch(
                            label = stringResource(R.string.request_4k),
                            checked = is4k,
                            onClick = { is4k = !is4k },
                        )
                        Button(
                            onClick = {
                                onSubmit.invoke(MovieRequest(movie.id, is4k, profile.id, folder.path))
                            },
                        ) {
                            Text(
                                text = stringResource(R.string.submit),
                            )
                        }
                    }
                } else {
                    Button(
                        onClick = {
                            onSubmit.invoke(MovieRequest(movie.id, is4k, profile.id, folder.path))
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.submit),
                        )
                    }
                }
            }

            if (profiles.isNotEmpty()) {
                item {
                    ChooseProfile(
                        selectedProfile = profile,
                        profiles = profiles,
                        onClickProfile = { profile = it },
                        modifier = Modifier,
                    )
                }
            }
            if (folders.isNotEmpty()) {
                item {
                    ChooseFolder(
                        selectedFolder = folder,
                        folders = folders,
                        onClickFolder = { folder = it },
                        modifier = Modifier,
                    )
                }
            }
        }
    }
}

@Composable
fun ChooseProfile(
    selectedProfile: SeerrProfile,
    profiles: List<SeerrProfile>,
    onClickProfile: (SeerrProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showProfileDialog by remember { mutableStateOf(false) }
    ListItem(
        selected = false,
        headlineContent = {
            Text("Quality profile")
        },
        supportingContent = {
            val text =
                remember(selectedProfile) {
                    if (selectedProfile.default) "${selectedProfile.name} (Default)" else selectedProfile.name
                }
            Text(text)
        },
        onClick = { showProfileDialog = true },
        modifier = modifier,
    )
    if (showProfileDialog) {
        val params =
            remember {
                val items =
                    profiles.map {
                        DialogItem(
                            headlineContent = {
                                Text(it.name)
                            },
                            supportingContent = {
                                if (it.default) {
                                    Text("Default")
                                }
                            },
                            onClick = { onClickProfile.invoke(it) },
                            selected = it == selectedProfile,
                        )
                    }
                DialogParams(
                    fromLongClick = false,
                    title = "Profiles",
                    items = items,
                )
            }
        DialogPopup(
            params = params,
            onDismissRequest = { showProfileDialog = false },
            dismissOnClick = true,
            elevation = 3.dp,
        )
    }
}

@Composable
fun ChooseFolder(
    selectedFolder: SeerrRootFolder,
    folders: List<SeerrRootFolder>,
    onClickFolder: (SeerrRootFolder) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFolderDialog by remember { mutableStateOf(false) }
    ListItem(
        selected = false,
        headlineContent = {
            Text("Root folder")
        },
        supportingContent = {
            val text =
                remember(selectedFolder) {
                    if (selectedFolder.default) "${selectedFolder.path} (Default)" else selectedFolder.path
                }
            Text(text)
        },
        trailingContent = {
            Text(selectedFolder.freeSpace)
        },
        onClick = { showFolderDialog = true },
        modifier = modifier,
    )
    if (showFolderDialog) {
        val params =
            remember {
                val items =
                    folders.map {
                        DialogItem(
                            headlineContent = {
                                Text(it.path)
                            },
                            supportingContent = {
                                if (it.default) {
                                    Text("Default")
                                }
                            },
                            trailingContent = {
                                Text(it.freeSpace)
                            },
                            onClick = { onClickFolder.invoke(it) },
                            selected = it == selectedFolder,
                        )
                    }
                DialogParams(
                    fromLongClick = false,
                    title = "Directories",
                    items = items,
                )
            }
        DialogPopup(
            params = params,
            onDismissRequest = { showFolderDialog = false },
            dismissOnClick = true,
            elevation = 3.dp,
        )
    }
}

@PreviewTvSpec
@Composable
fun MovieRequestPreview() {
    WholphinTheme {
        RequestMovie(
            loading = LoadingState.Success,
            state =
                SeerrRequestData(
                    profiles4k =
                        listOf(
                            SeerrProfile(1, "HD", true),
                            SeerrProfile(2, "Ultra HD", false),
                        ),
                    rootFolders4k =
                        listOf(
                            SeerrRootFolder(1, "/movies", "400GB", true),
                        ),
                ),
            request4kEnabled = true,
            movie = MovieDetails(id = 1, title = "Movie Name", releaseDate = "2026"),
            onSubmit = {},
            modifier =
                Modifier
                    .width(320.dp)
                    .padding(16.dp),
        )
    }
}
