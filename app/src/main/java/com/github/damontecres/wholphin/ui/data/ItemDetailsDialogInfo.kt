package com.github.damontecres.wholphin.ui.data

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.byteRateSuffixes
import com.github.damontecres.wholphin.ui.components.ScrollableDialog
import com.github.damontecres.wholphin.ui.formatBytes
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.letNotEmpty
import com.github.damontecres.wholphin.util.languageName
import org.jellyfin.sdk.model.api.AudioSpatialFormat
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.Locale

data class ItemDetailsDialogInfo(
    val title: String,
    val overview: String?,
    val genres: List<String>,
    val files: List<MediaSourceInfo>,
)

@Composable
fun ItemDetailsDialog(
    info: ItemDetailsDialogInfo,
    showFilePath: Boolean,
    onDismissRequest: () -> Unit,
) = ScrollableDialog(
    onDismissRequest = onDismissRequest,
) {
    item {
        Text(
            text = info.title,
            style = MaterialTheme.typography.titleLarge,
        )
    }
    if (info.genres.isNotEmpty()) {
        item {
            Text(
                text = info.genres.joinToString(", "),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
    if (info.overview.isNotNullOrBlank()) {
        item {
            Text(
                text = info.overview,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
    if (info.files.isNotEmpty()) {
        item {
            Spacer(Modifier.height(12.dp))
        }
    }
    items(info.files) { file ->
        ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
            MediaSourceInfo(file, showFilePath, Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
fun MediaSourceInfo(
    source: MediaSourceInfo,
    showFilePath: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.name) + ": ${source.name}",
        )
        Text(
            text = "ID: ${source.id}",
        )
        if (showFilePath) {
            Text(
                text = stringResource(R.string.path) + ": ${source.path}",
            )
        }
        source.size?.let { size ->
            Text(
                text = stringResource(R.string.file_size) + ": ${formatBytes(size)}",
            )
        }
        source.bitrate?.let { bitrate ->
            Text(
                text =
                    stringResource(R.string.bitrate) + ": ${
                        formatBytes(
                            bitrate,
                            byteRateSuffixes,
                        )
                    }",
            )
        }
        source.mediaStreams?.letNotEmpty { streams ->
            streams.filter { it.type == MediaStreamType.VIDEO }.forEach { stream ->
                val data =
                    buildList {
                        if (stream.width != null && stream.height != null) {
                            add("${stream.width}x${stream.height}")
                        }
                        stream.averageFrameRate?.let {
                            add(String.format(Locale.getDefault(), "%.3f", it) + " fps")
                        }
                        stream.bitRate?.let { add(formatBytes(it, byteRateSuffixes)) }
                        stream.codec?.let(::add)
                        stream.profile?.let(::add)
                    }
                Text(
                    text = stringResource(R.string.video) + ": " + data.joinToString(" - "),
                )
            }

            streams.filter { it.type == MediaStreamType.AUDIO }.forEachIndexed { index, stream ->
                val data =
                    buildList {
                        stream.language?.let { add(languageName(it)) }
                        stream.codec?.let(::add)
                        stream.channelLayout?.let(::add)
                        stream.bitRate?.let { add(formatBytes(it, byteRateSuffixes)) }
                        if (stream.audioSpatialFormat != AudioSpatialFormat.NONE) add(stream.audioSpatialFormat.serialName)
                        if (stream.isDefault) add(stringResource(R.string.default_track))
                    }
                Text(
                    text = stringResource(R.string.audio) + " ${index + 1}: " + data.joinToString(" - "),
                )
            }

            streams.filter { it.type == MediaStreamType.SUBTITLE }.forEachIndexed { index, stream ->
                val data =
                    buildList {
                        stream.language?.let { add(languageName(it)) }
                        stream.codec?.let(::add)
                        if (stream.isDefault) add(stringResource(R.string.default_track))
                        if (stream.isForced) add(stringResource(R.string.forced_track))
                        if (stream.isExternal) add(stringResource(R.string.external_track))
                    }
                Text(
                    text =
                        stringResource(R.string.subtitle) + " ${index + 1}: " +
                            data.joinToString(" - "),
                )
            }
        }
    }
}
