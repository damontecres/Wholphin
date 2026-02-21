package com.github.damontecres.wholphin.ui.detail

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.util.Log
import android.view.Display
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.MainActivity
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.acra.util.versionCodeLong
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.clientLogApi
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

@HiltViewModel
class DebugViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val serverRepository: ServerRepository,
        val itemPlaybackDao: ItemPlaybackDao,
        val clientInfo: ClientInfo,
        val deviceInfo: DeviceInfo,
    ) : ViewModel() {
        val itemPlaybacks = MutableLiveData<List<ItemPlayback>>(listOf())
        val logcat = MutableLiveData<List<LogcatLine>>(listOf())
        val audioInfo = MutableStateFlow<AudioInfo>(AudioInfo())

        val supportedModes by lazy {
            val displayManager =
                MainActivity.instance.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val display = displayManager.getDisplay(Display.DEFAULT_DISPLAY)
            display.supportedModes.orEmpty()
        }

        init {
            viewModelScope.launchIO {
                serverRepository.currentUser.value?.rowId?.let {
                    val results = itemPlaybackDao.getItems(it)
                    withContext(Dispatchers.Main) {
                        itemPlaybacks.value = results
                    }
                }
                val logcat = getLogCatLines()
                withContext(Dispatchers.Main) {
                    this@DebugViewModel.logcat.value = logcat
                }
            }
            viewModelScope.launchDefault {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val callback =
                    object : AudioDeviceCallback() {
                        override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                            audioInfo.update {
                                it.copy(
                                    devices =
                                        it.devices
                                            .toMutableList()
                                            .apply { addAll(addedDevices.filter { it.isSink }) },
                                )
                            }
                        }

                        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                            audioInfo.update {
                                it.copy(
                                    devices =
                                        it.devices
                                            .toMutableList()
                                            .apply { removeAll(removedDevices) },
                                )
                            }
                        }
                    }
                audioManager.registerAudioDeviceCallback(callback, null)
                addCloseable { audioManager.unregisterAudioDeviceCallback(callback) }
            }
        }

        companion object {
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
                            val level = line.split(Regex("\\s+")).getOrNull(4)
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

            fun ViewModel.sendAppLogs(
                context: Context,
                api: ApiClient,
                clientInfo: ClientInfo?,
                deviceInfo: DeviceInfo?,
            ) {
                viewModelScope.launchIO(ExceptionHandler(true)) {
                    val logcat = getLogCatLines().joinToString("\n") { it.text }
                    val body =
                        """
                        Send App Logs
                        clientInfo=$clientInfo
                        deviceInfo=$deviceInfo
                        manufacturer=${Build.MANUFACTURER}
                        model=${Build.MODEL}
                        apiLevel=${Build.VERSION.SDK_INT}

                        """.trimIndent()
                    Timber.w(body)
                    val response by api.clientLogApi.logFile(body + logcat)
                    showToast(context, "Sent! Filename=${response.fileName}")
                }
            }
        }
    }

data class LogcatLine(
    val level: Int,
    val text: String,
)

data class AudioInfo(
    val devices: List<AudioDeviceInfo> = emptyList(),
)

val AudioDeviceInfo.details: String
    get() {
        val typeName =
            when (type) {
                AudioDeviceInfo.TYPE_HDMI -> "HDMI"
                AudioDeviceInfo.TYPE_HDMI_ARC -> "ARC"
                AudioDeviceInfo.TYPE_HDMI_EARC -> "eARC"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Speaker"
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "Speaker Safe"
                else -> "N/A"
            }
        val addressStr =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                address
            } else {
                null
            }
        val encodings =
            encodings.map { enc ->
                when (enc) {
                    AudioFormat.ENCODING_INVALID -> "INVALID"

                    AudioFormat.ENCODING_PCM_16BIT -> "PCM_16BIT"

                    AudioFormat.ENCODING_PCM_8BIT -> "PCM_8BIT"

                    AudioFormat.ENCODING_PCM_FLOAT -> "PCM_FLOAT"

                    AudioFormat.ENCODING_AC3 -> "AC3"

                    AudioFormat.ENCODING_E_AC3 -> "E_AC3"

                    AudioFormat.ENCODING_DTS -> "DTS"

                    AudioFormat.ENCODING_DTS_HD -> "DTS_HD"

                    AudioFormat.ENCODING_MP3 -> "MP3"

                    AudioFormat.ENCODING_AAC_LC -> "AAC_LC"

                    AudioFormat.ENCODING_AAC_HE_V1 -> "AAC_HE_V1"

                    AudioFormat.ENCODING_AAC_HE_V2 -> "AAC_HE_V2"

                    AudioFormat.ENCODING_IEC61937 -> "IEC61937"

                    AudioFormat.ENCODING_DOLBY_TRUEHD -> "DOLBY_TRUEHD"

                    AudioFormat.ENCODING_AAC_ELD -> "AAC_ELD"

                    AudioFormat.ENCODING_AAC_XHE -> "AAC_XHE"

                    AudioFormat.ENCODING_AC4 -> "AC4"

                    // AudioFormat.ENCODING_AC4_L4->"AC4_L4"

                    AudioFormat.ENCODING_E_AC3_JOC -> "E_AC3_JOC"

                    AudioFormat.ENCODING_DOLBY_MAT -> "DOLBY_MAT"

                    AudioFormat.ENCODING_OPUS -> "OPUS"

                    AudioFormat.ENCODING_PCM_24BIT_PACKED -> "PCM_24BIT_PACKED"

                    AudioFormat.ENCODING_PCM_32BIT -> "PCM_32BIT"

                    AudioFormat.ENCODING_MPEGH_BL_L3 -> "MPEGH_BL_L3"

                    AudioFormat.ENCODING_MPEGH_BL_L4 -> "MPEGH_BL_L4"

                    AudioFormat.ENCODING_MPEGH_LC_L3 -> "MPEGH_LC_L3"

                    AudioFormat.ENCODING_MPEGH_LC_L4 -> "MPEGH_LC_L4"

                    AudioFormat.ENCODING_DTS_UHD_P1 -> "DTS_UHD_P1"

                    AudioFormat.ENCODING_DRA -> "DRA"

                    AudioFormat.ENCODING_DTS_HD_MA -> "DTS_HD_MA"

                    AudioFormat.ENCODING_DTS_UHD_P2 -> "DTS_UHD_P2"

                    AudioFormat.ENCODING_DSD -> "DSD"

                    AudioFormat.ENCODING_IAMF_BASE_ENHANCED_PROFILE_AAC -> "IAMF_BASE_ENHANCED_PROFILE_AAC"

                    AudioFormat.ENCODING_IAMF_BASE_ENHANCED_PROFILE_FLAC -> "IAMF_BASE_ENHANCED_PROFILE_FLAC"

                    AudioFormat.ENCODING_IAMF_BASE_ENHANCED_PROFILE_OPUS -> "IAMF_BASE_ENHANCED_PROFILE_OPUS"

                    AudioFormat.ENCODING_IAMF_BASE_ENHANCED_PROFILE_PCM -> "IAMF_BASE_ENHANCED_PROFILE_PCM"

                    AudioFormat.ENCODING_IAMF_BASE_PROFILE_AAC -> "IAMF_BASE_PROFILE_AAC"

                    AudioFormat.ENCODING_IAMF_BASE_PROFILE_FLAC -> "IAMF_BASE_PROFILE_FLAC"

                    AudioFormat.ENCODING_IAMF_BASE_PROFILE_OPUS -> "IAMF_BASE_PROFILE_OPUS"

                    AudioFormat.ENCODING_IAMF_BASE_PROFILE_PCM -> "IAMF_BASE_PROFILE_PCM"

                    AudioFormat.ENCODING_IAMF_SIMPLE_PROFILE_AAC -> "IAMF_SIMPLE_PROFILE_AAC"

                    AudioFormat.ENCODING_IAMF_SIMPLE_PROFILE_FLAC -> "IAMF_SIMPLE_PROFILE_FLAC"

                    AudioFormat.ENCODING_IAMF_SIMPLE_PROFILE_OPUS -> "IAMF_SIMPLE_PROFILE_OPUS"

                    AudioFormat.ENCODING_IAMF_SIMPLE_PROFILE_PCM -> "IAMF_SIMPLE_PROFILE_PCM"

                    else -> "invalid encoding $enc"
                }
            }
        return "AudioDeviceInfo(id=$id, type=$type ($typeName), " +
            "channelCounts=${channelCounts.contentToString()}, " +
            "encodings=$encodings, " +
            "productName=$productName, address=$addressStr)"
    }

@Composable
fun DebugPage(
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
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
                    text = "App Information",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val installInfo =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val installSource =
                            context.packageManager.getInstallSourceInfo(context.packageName)
                        buildList {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add("Install source: ${installSource.packageSource}")
                            }
                            add("Installer: ${installSource.installingPackageName}")
                            add("Initiator: ${installSource.initiatingPackageName}")
                        }
                    } else {
                        listOf(context.packageManager.getInstallerPackageName(context.packageName))
                    }
                (
                    listOf(
                        "Version Name: ${pkgInfo.versionName}",
                        "Version Code: ${pkgInfo.versionCodeLong}",
                        "ClientInfo:  ${viewModel.clientInfo}",
                        "Build type: ${BuildConfig.BUILD_TYPE}",
                        "Debug enabled: ${BuildConfig.DEBUG}",
                        "ABIs: ${Build.SUPPORTED_ABIS.toList()}",
                    ) + installInfo
                ).forEach {
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
                    text = "Device Information",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val details =
                    listOf(
                        "DeviceInfo:  ${viewModel.deviceInfo}",
                        "Manufacturer: ${Build.MANUFACTURER}",
                        "Model: ${Build.MODEL}",
                        "API Level: ${Build.VERSION.SDK_INT}",
                        "",
                        "Display Modes:",
                        *viewModel.supportedModes,
                        "",
                        "Audio Devices:",
                    )
                val audioInfo by viewModel.audioInfo.collectAsState()
                val audio = remember(audioInfo) { audioInfo.devices.map { it.details } }
                (details + audio).forEach {
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
                    text = "User Information",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Current server: ${viewModel.serverRepository.currentServer.value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Current user: ${viewModel.serverRepository.currentUser.value}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "User server settings: ${viewModel.serverRepository.currentUserDto.value?.configuration}",
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
