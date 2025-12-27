package com.github.damontecres.wholphin.ui.playback

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.abbreviateNumber
import com.github.damontecres.wholphin.ui.components.DialogItem
import com.github.damontecres.wholphin.ui.components.DialogItemDivider
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.tryRequestFocus
import org.jellyfin.sdk.model.api.RemoteSubtitleInfo

@Composable
fun DownloadSubtitlesContent(
    state: SubtitleSearch,
    language: String,
    onSearch: (String) -> Unit,
    onClickDownload: (RemoteSubtitleInfo) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val s = state) {
        SubtitleSearch.Searching -> {
            Wrapper {
                Text(
                    text = stringResource(R.string.searching),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        SubtitleSearch.Downloading -> {
            Wrapper {
                Text(
                    text = stringResource(R.string.downloading),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        is SubtitleSearch.Error -> {
            Wrapper { ErrorMessage(null, s.ex, modifier) }
        }

        is SubtitleSearch.Success -> {
            val dialogItems = convertRemoteSubtitles(s.options, onClickDownload)
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                focusRequester.tryRequestFocus()
            }
            val elevatedContainerColor =
                MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    modifier
                        .graphicsLayer {
                            this.clip = true
                            this.shape = RoundedCornerShape(28.0.dp)
                        }.drawBehind { drawRect(color = elevatedContainerColor) }
                        .padding(PaddingValues(24.dp)),
            ) {
                Text(
                    text = "Search & download subtitles",
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
                        text = "Language",
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
                        //                        onKeyboardAction = {
//                            onSearch(lang.text.toString())
//                        },
                        keyboardOptions =
                            KeyboardOptions(
                                imeAction = ImeAction.Search,
                            ),
                    )
                }
                if (dialogItems.isEmpty()) {
                    Text(
                        text = "No remote subtitles were found",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier,
                    ) {
                        itemsIndexed(dialogItems) { index, item ->
                            when (item) {
                                is DialogItemDivider -> {
                                    HorizontalDivider(Modifier.height(16.dp))
                                }

                                is DialogItem -> {
                                    ListItem(
                                        selected = false,
                                        enabled = item.enabled,
                                        onClick = {
                                            item.onClick.invoke()
                                        },
                                        headlineContent = item.headlineContent,
                                        overlineContent = item.overlineContent,
                                        supportingContent = item.supportingContent,
                                        leadingContent = item.leadingContent,
                                        trailingContent = item.trailingContent,
                                        modifier =
                                            Modifier.ifElse(
                                                index == 0,
                                                Modifier.focusRequester(focusRequester),
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Wrapper(content: @Composable BoxScope.() -> Unit) {
    val elevatedContainerColor =
        MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    Box(
        modifier =
            Modifier
                .graphicsLayer {
                    this.clip = true
                    this.shape = RoundedCornerShape(28.0.dp)
                }.drawBehind { drawRect(color = elevatedContainerColor) }
                .padding(PaddingValues(24.dp)),
        content = content,
    )
}

fun convertRemoteSubtitles(
    options: List<RemoteSubtitleInfo>,
    onClick: (RemoteSubtitleInfo) -> Unit,
) = options.map { op ->
    DialogItem(
        onClick = { onClick.invoke(op) },
        headlineContent = {
            Text(
                text = op.name ?: "",
            )
        },
        supportingContent = {
            val strings =
                buildList {
                    op.providerName?.let(::add)
                    op.threeLetterIsoLanguageName?.let(::add)
                    if (op.forced == true) {
                        add(stringResource(R.string.forced_track))
                    }
                    add(
                        pluralStringResource(
                            R.plurals.downloads,
                            op.downloadCount ?: 0,
                            abbreviateNumber(op.downloadCount ?: 0),
                        ),
                    )
                }
            Text(
                text = strings.joinToString(" - "),
            )
        },
        trailingContent = {
            op.communityRating?.let { rating ->
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
    )
}
