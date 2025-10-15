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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.nav.NavigationManager
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.Release
import com.github.damontecres.wholphin.util.UpdateChecker
import com.github.damontecres.wholphin.util.Version
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        val updater: UpdateChecker,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        val loading = MutableLiveData<LoadingState>(LoadingState.Pending)
        val release = MutableLiveData<Release?>(null)

        val currentVersion = updater.getInstalledVersion()

        fun init(updateUrl: String) {
            loading.value = LoadingState.Loading
            viewModelScope.launch(Dispatchers.IO + LoadingExceptionHandler(loading, "Failed to check for update")) {
                val release = updater.getLatestRelease(updateUrl)
                withContext(Dispatchers.Main) {
                    this@UpdateViewModel.release.value = release
                    loading.value = LoadingState.Success
                }
            }
        }

        fun installRelease(release: Release) {
            viewModelScope.launch(Dispatchers.IO + LoadingExceptionHandler(loading, "Failed to install update")) {
                updater.installRelease(release)
            }
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

        LoadingState.Success ->
            release?.let {
                if (it.version.isGreaterThan(viewModel.currentVersion)) {
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
                        modifier = modifier,
                    )
                } else {
                    Text(
                        text = "No update available",
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
                Text(
                    // TODO render markdown
                    text = release.notes.joinToString("\n\n") + (release.body ?: ""),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
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
                text = "Update available",
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
                    text = "Download & Update",
                )
            }
            Button(
                onClick = onCancel,
            ) {
                Text(
                    text = "Cancel",
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
