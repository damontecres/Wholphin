package com.github.damontecres.wholphin.ui.detail

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.util.ExceptionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

@HiltViewModel
class DebugViewModel
    @Inject
    constructor(
        val serverRepository: ServerRepository,
        val itemPlaybackDao: ItemPlaybackDao,
    ) : ViewModel() {
        val itemPlaybacks = MutableLiveData<List<ItemPlayback>>(listOf())
        val logcat = MutableLiveData<List<LogcatLine>>(listOf())

        init {
            viewModelScope.launchIO {
                serverRepository.currentUser?.rowId?.let {
                    val results = itemPlaybackDao.getItems(it)
                    withContext(Dispatchers.Main) {
                        itemPlaybacks.value = results
                    }
                    val logcat = getLogCatLines()
                    withContext(Dispatchers.Main) {
                        this@DebugViewModel.logcat.value = logcat
                    }
                }
            }
        }

        fun getLogCatLines(): List<LogcatLine> {
            val lineCount = 500
            val args =
                buildList {
                    add("logcat")
                    add("-d")
                    add("-t")
                    add(lineCount.toString())
                    addAll(THIRD_PARTY_TAGS)
                    add("*:V")
                }
            val process = ProcessBuilder().command(args).redirectErrorStream(true).start()
            val logLines = mutableListOf<LogcatLine>()
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var count = 0

                while (count < lineCount) {
                    val line = reader.readLine()
                    if (line != null) {
                        val level = line.split(" ").getOrNull(4)
                        val logLevel =
                            when (level?.uppercase()) {
                                "V" -> Log.VERBOSE
                                "D" -> Log.DEBUG
                                "I" -> Log.INFO
                                "W" -> Log.WARN
                                "E" -> Log.ERROR
                                else -> Log.VERBOSE
                            }
                        logLines.add(LogcatLine(logLevel, line))
                    } else {
                        break
                    }
                    count++
                }
            } finally {
                process.destroy()
            }
            return logLines
        }
    }

data class LogcatLine(
    val level: Int,
    val text: String,
)

@Composable
fun DebugPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel(),
) {
    val scrollAmount = 100f
    val columnState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun scroll(reverse: Boolean = false) {
        scope.launch(ExceptionHandler()) {
            columnState.scrollBy(if (reverse) -scrollAmount else scrollAmount)
        }
    }

    val itemPlaybacks by viewModel.itemPlaybacks.observeAsState(listOf())
    val logcat by viewModel.logcat.observeAsState(listOf())

    LazyColumn(
        state = columnState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(32.dp),
        modifier =
            modifier
                .focusable()
                .background(
                    MaterialTheme.colorScheme.surface,
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
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "AppPreferences",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = preferences.appPreferences.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "App Information",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Build type: ${BuildConfig.BUILD_TYPE}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Debug enabled: ${BuildConfig.DEBUG}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "User Information",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Current server: ${viewModel.serverRepository.currentServer}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Current user: ${viewModel.serverRepository.currentUser}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "User server settings: ${preferences.userConfig}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Database",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "ItemPlayback",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                itemPlaybacks.forEach {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        item {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Logcat",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                logcat.forEach { (level, line) ->
                    val color =
                        when (level) {
                            Log.VERBOSE -> MaterialTheme.colorScheme.onSurface
                            Log.DEBUG -> Color(0xff2bc4cf)
                            Log.INFO -> Color(0xff2bcf8b)
                            Log.WARN -> Color(0xffdde663)
                            Log.ERROR -> Color(0xffe67063)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                    )
                }
            }
        }
    }
}

private val THIRD_PARTY_TAGS =
    listOf(
        "libc:F",
        "ExoPlayerImpl:W",
        // FireTV
        "Codec2Client:E",
        "CCodecBuffers:E",
        "CCodecConfig:E",
        "okhttp.Http2:W",
        "okhttp.TaskRunner:W",
        "LruBitmapPool:W",
        "FragmentManager:W",
        "ConfigStore:W",
        "GlideRequest:W",
        "FactoryPools:W",
        "ViewTarget:W",
        "Engine:W",
        "Downsampler:W",
        "TransformationUtils:W",
        "DecodeJob:W",
        "BufferPoolAccessor2.0:W",
        "ExifInterface:W",
        "MediaCodec:W",
        "SurfaceUtils:W",
        "ByteArrayPool:W",
        "HardwareConfig:W",
        "DfltImageHeaderParser:W",
    )
