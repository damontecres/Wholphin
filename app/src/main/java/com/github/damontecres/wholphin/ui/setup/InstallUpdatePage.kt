package com.github.damontecres.wholphin.ui.setup

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.DownloadCallback
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.Release
import com.github.damontecres.wholphin.services.UpdateChecker
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.formatBytes
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.setValueOnMain
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.Version
import com.mikepenz.markdown.m3.Markdown
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        val updater: UpdateChecker,
        val navigationManager: NavigationManager,
    ) : ViewModel(),
        DownloadCallback {
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val release = MutableLiveData<Release?>(null)

        val downloading = MutableLiveData<Boolean>(false)
        val contentLength = MutableLiveData<Long>(-1)
        val bytesDownloaded = MutableLiveData<Long>(-1)

        val currentVersion = updater.getInstalledVersion()

        fun init(updateUrl: String) {
            loading.value = LoadingState.Loading
            viewModelScope.launch(Dispatchers.IO + LoadingExceptionHandler(loading, "Failed to check for update")) {
                val release = updater.getLatestRelease(updateUrl)
                withContext(Dispatchers.Main) {
                    contentLength.value = -1
                    bytesDownloaded.value = -1
                    this@UpdateViewModel.release.value = release
                    loading.value = LoadingState.Success
                }
            }
        }

        private var downloadJob: Job? = null

        fun installRelease(release: Release) {
            downloadJob =
                viewModelScope.launch(
                    Dispatchers.IO +
                        LoadingExceptionHandler(
                            loading,
                            "Failed to install update",
                        ),
                ) {
                    downloading.setValueOnMain(true)
                    updater.installRelease(release, this@UpdateViewModel)
                    downloading.setValueOnMain(false)
                }
        }

        fun cancelDownload() {
            viewModelScope.launch(
                Dispatchers.IO +
                    LoadingExceptionHandler(
                        loading,
                        "Error",
                    ),
            ) {
                downloadJob?.cancel()
                withContext(Dispatchers.Main) {
                    downloading.value = false
                    contentLength.value = -1
                    bytesDownloaded.value = -1
                }
            }
        }

        override fun contentLength(contentLength: Long) {
            this@UpdateViewModel.contentLength.value = contentLength
        }

        override fun bytesDownloaded(bytes: Long) {
            this@UpdateViewModel.bytesDownloaded.value = bytes
        }
    }

@Composable
fun InstallUpdatePage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val loading by viewModel.loading.observeAsState(LoadingState.Pending)
    val release by viewModel.release.observeAsState(null)

    val isDownloading by viewModel.downloading.observeAsState(false)
    val contentLength by viewModel.contentLength.observeAsState(-1L)
    val bytesDownloaded by viewModel.bytesDownloaded.observeAsState(-1)

    LaunchedEffect(Unit) {
        viewModel.init(preferences.appPreferences.updateUrl)
    }
    var permissions by remember { mutableStateOf(viewModel.updater.hasPermissions()) }
    val launcher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted: Boolean ->
            if (isGranted) {
                permissions = true
            } else {
                // TODO
            }
        }
    when (val state = loading) {
        is LoadingState.Error -> ErrorMessage(state, modifier)
        LoadingState.Loading,
        LoadingState.Pending,
        -> LoadingPage(modifier)

        LoadingState.Success -> {
            release?.let {
                InstallUpdatePageContent(
                    currentVersion = viewModel.currentVersion,
                    release = it,
                    onInstallRelease = {
                        if (!permissions) {
                            launcher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        } else {
                            viewModel.installRelease(it)
                        }
                    },
                    onCancel = {
                        viewModel.navigationManager.goBack()
                    },
                    modifier =
                        modifier
                            .ifElse(
                                isDownloading,
                                Modifier
                                    .blur(8.dp)
                                    .alpha(.33f),
                            ),
                )
            }
            if (isDownloading) {
                DownloadDialog(
                    contentLength = contentLength,
                    bytesDownloaded = bytesDownloaded,
                    onDismissRequest = {
                        viewModel.cancelDownload()
                    },
                )
            }
        }
    }
}

@Composable
fun InstallUpdatePageContent(
    currentVersion: Version,
    release: Release,
    onInstallRelease: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        val scrollAmount = 100f
        val columnState = rememberLazyListState()
        val scope = rememberCoroutineScope()

        fun scroll(reverse: Boolean = false) {
            scope.launch(ExceptionHandler()) {
                columnState.scrollBy(if (reverse) -scrollAmount else scrollAmount)
            }
        }
        val columnInteractionSource = remember { MutableInteractionSource() }
        val columnFocused by columnInteractionSource.collectIsFocusedAsState()
        val columnColor =
            if (columnFocused) {
                MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            } else {
                MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
            }
        LazyColumn(
            state = columnState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .focusable(interactionSource = columnInteractionSource)
                    .fillMaxHeight()
                    .fillMaxWidth(.6f)
                    .background(
                        columnColor,
                        shape = RoundedCornerShape(16.dp),
                    ).onKeyEvent {
                        if (it.type == KeyEventType.KeyUp) {
                            return@onKeyEvent false
                        }
                        if (it.key == Key.DirectionDown) {
                            scroll(false)
                            return@onKeyEvent true
                        }
                        if (it.key == Key.DirectionUp) {
                            scroll(true)
                            return@onKeyEvent true
                        }
                        return@onKeyEvent false
                    },
        ) {
            item {
                Markdown(
                    (release.notes.joinToString("\n\n") + (release.body ?: ""))
                        .replace(
                            Regex("https://github.com/damontecres/\\w+/pull/(\\d+)"),
                            "#$1",
                        )
                        // Remove the last line for full changelog since its just a link
                        .replace(Regex("\\*\\*Full Changelog\\*\\*.*"), ""),
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.CenterVertically)
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
                        shape = RoundedCornerShape(16.dp),
                    ).padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.update_available),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "$currentVersion => " + release.version.toString(),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Button(
                onClick = onInstallRelease,
            ) {
                Text(
                    text = stringResource(R.string.download_and_update),
                )
            }
            Button(
                onClick = onCancel,
            ) {
                Text(
                    text = stringResource(R.string.cancel),
                )
            }
        }
    }
}

@Composable
fun DownloadDialog(
    contentLength: Long,
    bytesDownloaded: Long,
    onDismissRequest: () -> Unit,
) {
    val progress =
        if (contentLength > 0) {
            bytesDownloaded.toFloat() / contentLength
        } else {
            null
        }
    BasicDialog(
        onDismissRequest = onDismissRequest,
        elevation = 6.dp,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier,
            ) {
                Text(
                    text = stringResource(R.string.downloading),
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.border,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .padding(8.dp),
                    )
                } else {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.border,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .padding(8.dp),
                    )
                }
            }
            if (progress != null) {
                val bytes = formatBytes(bytesDownloaded)
                val size = formatBytes(contentLength)
                Text(
                    text = "$bytes / $size",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun InstallUpdatePageContentPreview() {
    WholphinTheme {
        InstallUpdatePageContent(
            currentVersion = Version.fromString("v0.4.0"),
            release =
                Release(
                    version = Version.fromString("v0.5.3"),
                    downloadUrl = "https://url",
                    publishedAt = null,
                    body =
                        "Lorem ipsum dolor sit amet consectetur adipiscing elit. Quisque faucibus " +
                            "ex sapien vitae pellentesque sem placerat. In id cursus mi pretium " +
                            "tellus duis convallis. Tempus leo eu aenean sed diam urna tempor. " +
                            "Pulvinar vivamus fringilla lacus nec metus bibendum egestas. " +
                            "Iaculis massa nisl malesuada lacinia integer nunc posuere. " +
                            "Ut hendrerit semper vel class aptent taciti sociosqu. Ad litora " +
                            "torquent per conubia nostra inceptos himenaeos.\n\n" +
                            "Lorem ipsum dolor sit amet consectetur adipiscing elit. Quisque faucibus " +
                            "ex sapien vitae pellentesque sem placerat. In id cursus mi pretium " +
                            "tellus duis convallis. Tempus leo eu aenean sed diam urna tempor. " +
                            "Pulvinar vivamus fringilla lacus nec metus bibendum egestas. " +
                            "Iaculis massa nisl malesuada lacinia integer nunc posuere. " +
                            "Ut hendrerit semper vel class aptent taciti sociosqu. Ad litora " +
                            "torquent per conubia nostra inceptos himenaeos.",
                    notes = listOf(),
                ),
            onInstallRelease = {},
            onCancel = {},
        )
    }
}
