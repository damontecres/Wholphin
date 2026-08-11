package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.PreviewTvSpec
import com.github.damontecres.wholphin.ui.abbreviateNumber
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.theme.WholphinTheme
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo

@Composable
fun DownloadSubtitlesContent(
    state: SubtitleSearchStatus,
    language: String,
    onSearch: (String) -> Unit,
    onClickDownload: (RemoteSubtitleInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val s = state) {
        SubtitleSearchStatus.Inactive -> {}

        SubtitleSearchStatus.Searching -> {
            Text(
                text = stringResource(R.string.searching),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = modifier,
            )
        }

        SubtitleSearchStatus.Downloading -> {
            Text(
                text = stringResource(R.string.downloading),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = modifier,
            )
        }

        is SubtitleSearchStatus.Error -> {
            ErrorMessage(null, s.ex, modifier)
        }

        is SubtitleSearchStatus.Success -> {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.tryRequestFocus()
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = modifier,
            ) {
                Text(
                    text = stringResource(R.string.search_and_download_subtitles),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
//                val lang = rememberTextFieldState(language)
                var lang by rememberSaveable { mutableStateOf(language) }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.language),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    EditTextBox(
                        value = lang,
                        onValueChange = { lang = it },
                        keyboardActions =
                            KeyboardActions(
                                onSearch = {
                                    onSearch(lang)
                                },
                            ),
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Search,
                            ),
                    )
                }
                if (s.options.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_subtitles_found),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    val focusRequesters =
                        remember(s.options.size) { List(s.options.size) { FocusRequester() } }
                    LaunchedEffect(Unit) {
                        focusRequesters.firstOrNull()?.tryRequestFocus()
                    }
                    LazyColumn(
                        modifier = Modifier,
                    ) {
                        itemsIndexed(s.options) { index, item ->
                            SubtitleInfo(
                                subtitle = item,
                                onClick = onClickDownload,
                                modifier =
                                    Modifier
                                        .focusRequester(focusRequesters[index]),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SubtitleInfo(
    subtitle: RemoteSubtitleInfo,
    onClick: (RemoteSubtitleInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = false,
        onClick = { onClick.invoke(subtitle) },
        headlineContent = {
            Text(
                text = subtitle.name ?: "",
            )
        },
        overlineContent =
            if (subtitle.isHashMatch == true) {
                {
                    Text(stringResource(R.string.hash_matches))
                }
            } else {
                null
            },
        supportingContent = {
            val resources = LocalResources.current
            val details =
                remember(resources, subtitle) {
                    val strings =
                        buildList {
                            subtitle.providerName?.let(::add)
                            if (subtitle.forced == true) {
                                add(resources.getString(R.string.forced_track))
                            }
                            if (subtitle.hearingImpaired == true) {
                                add(resources.getString(R.string.subtitles_hearing_impaired))
                            }
                            add(
                                resources.getQuantityString(
                                    R.plurals.downloads,
                                    subtitle.downloadCount ?: 0,
                                    abbreviateNumber(subtitle.downloadCount ?: 0),
                                ),
                            )
                        }
                    strings.joinToString(" - ")
                }
            Text(
                text = details,
            )
        },
        trailingContent = {
            subtitle.communityRating?.let { rating ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = rating.toString(),
                    )
                    Icon(
                        imageVector = Icons.Default.Star,
                        tint = AppColors.GoldenYellow,
                        contentDescription = null,
                    )
                }
            }
        },
        modifier = modifier,
    )
}

@PreviewTvSpec
@Composable
fun SubtitleInfoPreview() {
    WholphinTheme {
        Column(Modifier.width(400.dp)) {
            SubtitleInfo(
                onClick = {},
                subtitle =
                    RemoteSubtitleInfo(
                        name = "Filename.mkv",
                        providerName = "OpenSubs",
                        threeLetterIsoLanguageName = "eng",
                        forced = true,
                        hearingImpaired = false,
                        isHashMatch = true,
                        communityRating = 7.5f,
                        downloadCount = 10_500,
                    ),
            )
            SubtitleInfo(
                onClick = {},
                subtitle =
                    RemoteSubtitleInfo(
                        name = "Filename.mkv",
                        providerName = "OpenSubs",
                        threeLetterIsoLanguageName = "eng",
                        forced = false,
                        hearingImpaired = true,
                        isHashMatch = false,
                        communityRating = 7.5f,
                        downloadCount = 10_500,
                    ),
            )
        }
    }
}
