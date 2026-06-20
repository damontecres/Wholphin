package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Button
import androidx.tv.material3.ListItem
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.api.seerr.model.MovieDetails
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogParams
import com.github.damontecres.wholphin.ui.components.DialogPopup
import com.github.damontecres.wholphin.ui.components.DialogPopupContent
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.main.settings.TitleText
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun RequestMovieDialog(
    state: SeerrRequestState,
    request4kEnabled: Boolean,
    movie: MovieDetails,
    onSubmit: (MovieRequest) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
    ) {
        RequestMovie(
            state = state,
            request4kEnabled = request4kEnabled,
            movie = movie,
            onSubmit = onSubmit,
            modifier = Modifier,
        )
    }
}

@Composable
fun RequestMovie(
    state: SeerrRequestState,
    request4kEnabled: Boolean,
    movie: MovieDetails,
    onSubmit: (MovieRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.loading) {
        is LoadingState.Error -> {
            ErrorMessage(state.loading, modifier)
        }

        LoadingState.Loading,
        LoadingState.Pending,
        -> {
            LoadingPage(modifier)
        }

        LoadingState.Success -> {
            val requestStr = stringResource(R.string.request)
            val request4kStr = stringResource(R.string.request_4k)
            if (state.profiles.isEmpty() && state.rootFolders.isEmpty()) {
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
                    modifier = Modifier,
                )
            }
        }
    }
}

@Composable
private fun RequestMovieWithOptions(
    state: SeerrRequestState,
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
        modifier = modifier,
    ) {
        TitleText(movie.title + " (${movie.releaseDate ?: ""})")
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
                                onSubmit.invoke(MovieRequest(movie.id, is4k, profile.id, folder.id))
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
                            onSubmit.invoke(MovieRequest(movie.id, is4k, profile.id, folder.id))
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
            Text("Profile")
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
            Text("Directory")
        },
        supportingContent = {
            val text =
                remember(selectedFolder) {
                    if (selectedFolder.default) "${selectedFolder.path} (Default)" else selectedFolder.path
                }
            Text(text)
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
        )
    }
}
