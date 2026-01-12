package com.github.damontecres.wholphin.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ChosenStreams
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.playback.audioStreamCount
import com.github.damontecres.wholphin.ui.playback.embeddedSubtitleCount
import com.github.damontecres.wholphin.ui.playback.externalSubtitlesCount
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.util.StreamFormatting.concatWithSpace
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatAudioCodec
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatSubtitleCodec
import com.github.damontecres.wholphin.ui.util.StreamFormatting.formatVideoRange
import com.github.damontecres.wholphin.ui.util.StreamFormatting.resolutionString
import com.github.damontecres.wholphin.util.languageName
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream

@Composable
@NonRestartableComposable
fun VideoStreamDetails(
    chosenStreams: ChosenStreams?,
    numberOfVersions: Int,
    modifier: Modifier = Modifier,
) = VideoStreamDetails(
    chosenStreams?.source,
    chosenStreams?.videoStream,
    chosenStreams?.audioStream,
    chosenStreams?.subtitleStream,
    numberOfVersions,
    modifier,
)

@Composable
fun VideoStreamDetails(
    source: MediaSourceInfo?,
    videoStream: MediaStream?,
    audioStream: MediaStream?,
    subtitleStream: MediaStream?,
    numberOfVersions: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        val video =
            remember(videoStream) {
                videoStream
                    ?.let {
                        val width = it.width
                        val height = it.height
                        val resName =
                            if (width != null && height != null) {
                                resolutionString(
                                    width,
                                    height,
                                    videoStream.isInterlaced,
                                )
                            } else {
                                null
                            }
                        val range = formatVideoRange(context, it.videoRange, it.videoRangeType, it.videoDoViTitle)
                        resName.concatWithSpace(range)
                    }
            }
        video?.let {
            StreamLabel(
                text = it,
                count = numberOfVersions,
            )
        }
        videoStream?.codec?.uppercase()?.let {
            StreamLabel(it)
        }

        val audioCount = remember(source) { source?.audioStreamCount ?: 0 }
        val audio =
            remember(audioCount, audioStream) {
                if (audioCount == 0 || audioStream == null) {
                    context.getString(R.string.none)
                } else {
                    listOfNotNull(
                        languageName(audioStream.language),
                        formatAudioCodec(context, audioStream.codec, audioStream.profile),
                        audioStream.channelLayout,
                    ).joinToString(" ")
                }
            }
        StreamLabel(
            text = audio,
            count = audioCount,
            icon = R.string.fa_volume_high,
            modifier = Modifier.widthIn(max = 200.dp),
        )

        val subtitleCount =
            remember(source) {
                (source?.embeddedSubtitleCount ?: 0) + (source?.externalSubtitlesCount ?: 0)
            }
        var disabled by remember { mutableStateOf(false) }
        val subtitle =
            remember(subtitleCount, subtitleStream) {
                if (subtitleCount > 0 && subtitleStream == null) {
                    disabled = true
                    context.getString(R.string.disabled)
                } else if (subtitleCount == 0 || subtitleStream == null) {
                    null
                } else {
                    disabled = false
                    listOfNotNull(
                        languageName(subtitleStream.language),
                        "SDH".takeIf { subtitleStream.isHearingImpaired },
                        formatSubtitleCodec(subtitleStream.codec),
                    ).joinToString(" ")
                }
            }
        subtitle?.let {
            StreamLabel(
                text = it,
                count = subtitleCount,
                icon = R.string.fa_closed_captioning,
                modifier = Modifier.widthIn(max = 160.dp),
                disabled = disabled,
            )
        }
    }
}

@Composable
fun StreamLabel(
    text: String,
    modifier: Modifier = Modifier,
    @StringRes icon: Int? = null,
    count: Int = 0,
    disabled: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier =
            modifier
                .background(
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .5f),
//                    MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                ).padding(vertical = 4.dp, horizontal = 6.dp),
    ) {
        ProvideTextStyle(
            TextStyle(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
        ) {
            if (icon != null) {
                Text(
                    text = stringResource(icon),
                    fontFamily = FontAwesome,
                    modifier = Modifier,
                )
            }
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier,
            )
            val countToUse = if (disabled) count else count - 1
            if (countToUse > 0) {
                Text(
                    text = "(+$countToUse)",
                    maxLines = 1,
                    modifier = Modifier,
                )
            }
        }
    }
}

@PreviewTvSpec
@Composable
private fun StreamLabelPreview() {
    WholphinTheme(appThemeColors = AppThemeColors.PURPLE) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(8.dp),
        ) {
            StreamLabel("1080p")
            StreamLabel("HDR")
            StreamLabel("H264")
            StreamLabel("AC3 5.1", icon = R.string.fa_volume_high, count = 2)

            StreamLabel("PGS", count = 1)
            StreamLabel("PGS", count = 1, disabled = true)
            StreamLabel("PGS", count = 2)
        }
    }
}
