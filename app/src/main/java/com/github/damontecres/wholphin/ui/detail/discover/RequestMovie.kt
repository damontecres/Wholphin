package com.github.damontecres.wholphin.ui.detail.discover

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.github.damontecres.wholphin.ui.components.SelectedLeadingContent
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState

@Composable
fun RequestMovieDialog(
    loading: LoadingState,
    data: SeerrRequestData,
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
            data = data,
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
    data: SeerrRequestData,
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
            if (data.profiles.isEmpty() && data.rootFolders.isEmpty() &&
                data.profiles4k.isEmpty() && data.rootFolders4k.isEmpty()
            ) {
                if (request4kEnabled) {
                    val items =
                        remember {
                            listOf(
                                DialogItem(
                                    text = requestStr,
                                    onClick = {
                                        onSubmit.invoke(MovieRequest(data, movie.id, false, null, null))
                                    },
                                ),
                                DialogItem(
                                    text = request4kStr,
                                    onClick = {
                                        onSubmit.invoke(MovieRequest(data, movie.id, true, null, null))
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
                        onSubmit.invoke(MovieRequest(data, movie.id, false, null, null))
                    }
                }
            } else {
                RequestMovieWithOptions(
                    data = data,
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
    data: SeerrRequestData,
    request4kEnabled: Boolean,
    movie: MovieDetails,
    onSubmit: (MovieRequest) -> Unit,
    modifier: Modifier = Modifier,
) {
    var is4k by remember { mutableStateOf(request4kEnabled) }
    var profile by remember(is4k) {
        mutableStateOf(
            if (is4k) {
                data.profiles4k.firstOrNull { it.default } ?: data.profiles4k.firstOrNull()
            } else {
                data.profiles.firstOrNull { it.default } ?: data.profiles.firstOrNull()
            },
        )
    }
    var folder by remember(is4k) {
        mutableStateOf(
            if (is4k) {
                data.rootFolders4k.firstOrNull { it.default } ?: data.rootFolders4k.firstOrNull()
            } else {
                data.rootFolders.firstOrNull { it.default } ?: data.rootFolders.firstOrNull()
            },
        )
    }
    val profiles = remember(is4k, data) { if (is4k) data.profiles4k else data.profiles }
    val folders = remember(is4k, data) { if (is4k) data.rootFolders4k else data.rootFolders }
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
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier,
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                ) {
                    if (request4kEnabled) {
                        ClickSwitch(
                            label = stringResource(R.string.request_4k),
                            checked = is4k,
                            onClick = { is4k = !is4k },
                        )
                    }
                    Button(
                        onClick = {
                            onSubmit.invoke(MovieRequest(data, movie.id, is4k, profile?.id, folder?.path))
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.submit),
                        )
                    }
                }
            }

            if (profiles.isNotEmpty()) {
                profile?.let {
                    item {
                        ChooseProfile(
                            selectedProfile = it,
                            profiles = profiles,
                            onClickProfile = { profile = it },
                            modifier = Modifier,
                        )
                    }
                }
            }
            if (folders.isNotEmpty()) {
                folder?.let {
                    item {
                        ChooseFolder(
                            selectedFolder = it,
                            folders = folders,
                            onClickFolder = { folder = it },
                            modifier = Modifier,
                        )
                    }
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
    val defaultStr = stringResource(R.string.is_default)
    val qualityProfileStr = stringResource(R.string.quality_profile)
    var showProfileDialog by remember { mutableStateOf(false) }
    ListItem(
        selected = false,
        headlineContent = {
            Text(qualityProfileStr)
        },
        supportingContent = {
            val text =
                remember(selectedProfile) {
                    if (selectedProfile.default) "${selectedProfile.name} ($defaultStr)" else selectedProfile.name
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
                                    Text(defaultStr)
                                }
                            },
                            leadingContent = {
                                SelectedLeadingContent(it == selectedProfile)
                            },
                            onClick = { onClickProfile.invoke(it) },
                        )
                    }
                DialogParams(
                    fromLongClick = false,
                    title = qualityProfileStr,
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
    val defaultStr = stringResource(R.string.is_default)
    val rootFolderStr = stringResource(R.string.root_folder)
    var showFolderDialog by remember { mutableStateOf(false) }
    ListItem(
        selected = false,
        headlineContent = {
            Text(rootFolderStr)
        },
        supportingContent = {
            val text =
                remember(selectedFolder) {
                    if (selectedFolder.default) "${selectedFolder.path} ($defaultStr)" else selectedFolder.path
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
                                    Text(defaultStr)
                                }
                            },
                            trailingContent = {
                                Text(it.freeSpace)
                            },
                            leadingContent = {
                                SelectedLeadingContent(it == selectedFolder)
                            },
                            onClick = { onClickFolder.invoke(it) },
                        )
                    }
                DialogParams(
                    fromLongClick = false,
                    title = rootFolderStr,
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
            data =
                SeerrRequestData(
                    profiles =
                        listOf(
                            SeerrProfile(1, "HD", true),
                            SeerrProfile(2, "Ultra HD", false),
                        ),
                    rootFolders =
                        listOf(
                            SeerrRootFolder(1, "/movies", "400GB", true),
                        ),
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
            request4kEnabled = false,
            movie = MovieDetails(id = 1, title = "Movie Name", releaseDate = "2026"),
            onSubmit = {},
            modifier =
                Modifier
                    .width(320.dp)
                    .padding(16.dp),
        )
    }
}
