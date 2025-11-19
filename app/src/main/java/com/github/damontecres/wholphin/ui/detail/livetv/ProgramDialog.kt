package com.github.damontecres.wholphin.ui.detail.livetv

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.surfaceColorAtElevation
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.seasonEpisode
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.LoadingState
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun ProgramDialog(
    item: BaseItem?,
    canRecord: Boolean,
    loading: LoadingState,
    onDismissRequest: () -> Unit,
    onWatch: () -> Unit,
    onRecord: (series: Boolean) -> Unit,
    onCancelRecord: (series: Boolean) -> Unit,
) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismissRequest,
    ) {
        val focusRequester = remember { FocusRequester() }
        Box(
            modifier =
                Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceColorAtElevation(10.dp),
                        shape = RoundedCornerShape(16.dp),
                    ).focusRequester(focusRequester),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier
                        .padding(16.dp),
            ) {
                when (val st = loading) {
                    is LoadingState.Error -> ErrorMessage(st)
                    LoadingState.Loading,
                    LoadingState.Pending,
                    ->
                        CircularProgress(
                            Modifier
                                .padding(8.dp)
                                .size(48.dp),
                        )

                    LoadingState.Success ->
                        item?.let { item ->
                            val now = LocalDateTime.now()
                            val dto = item.data
                            val isRecording = dto.timerId.isNotNullOrBlank()
                            val isSeriesRecording = dto.seriesTimerId.isNotNullOrBlank()
                            LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
                            Text(
                                text = item.name ?: "",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleLarge,
                            )
                            if (dto.isSeries ?: false) {
                                listOfNotNull(dto.seasonEpisode, dto.episodeTitle)
                                    .joinToString(" - ")
                                    .ifBlank { null }
                                    ?.let {
                                        Text(
                                            text = it,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                    }
                            }
                            val time =
                                DateUtils.formatDateRange(
                                    context,
                                    dto.startDate!!
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant()
                                        .epochSecond * 1000,
                                    dto.endDate!!
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant()
                                        .epochSecond * 1000,
                                    DateUtils.FORMAT_SHOW_TIME,
                                )
                            Text(
                                text = time,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            dto.overview?.let { overview ->
                                Text(
                                    text = overview,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyMedium,
                                    overflow = TextOverflow.Ellipsis,
                                    maxLines = 3,
                                )
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier =
                                    Modifier
                                        .padding(top = 8.dp)
                                        .fillMaxWidth(),
                            ) {
                                if (now.isAfter(dto.startDate!!) && now.isBefore(dto.endDate!!)) {
                                    Button(
                                        onClick = onWatch,
                                        modifier = Modifier,
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = stringResource(R.string.delete),
                                                tint = Color.Green.copy(alpha = .75f),
                                            )
                                            Text(
                                                text = stringResource(R.string.watch_live),
                                            )
                                        }
                                    }
                                }
                                if (canRecord) {
                                    val recordFocusRequester = remember { FocusRequester() }
                                    LazyRow(
                                        horizontalArrangement =
                                            Arrangement.spacedBy(
                                                16.dp,
                                                Alignment.CenterHorizontally,
                                            ),
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .focusRestorer(recordFocusRequester),
                                    ) {
                                        if (dto.isSeries ?: false) {
                                            item {
                                                Button(
                                                    onClick = {
                                                        if (isSeriesRecording) {
                                                            onCancelRecord.invoke(true)
                                                        } else {
                                                            onRecord.invoke(true)
                                                        }
                                                    },
                                                    modifier =
                                                        Modifier.focusRequester(recordFocusRequester),
                                                ) {
                                                    Row(
                                                        horizontalArrangement =
                                                            Arrangement.spacedBy(
                                                                4.dp,
                                                            ),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        if (isSeriesRecording) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = null,
                                                                tint = Color.Red,
                                                            )
                                                        }
                                                        Text(
                                                            text =
                                                                if (isSeriesRecording) {
                                                                    stringResource(
                                                                        R.string.cancel_series_recording,
                                                                    )
                                                                } else {
                                                                    stringResource(R.string.record_series)
                                                                },
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        if (dto.endDate?.isAfter(LocalDateTime.now()) ?: true) {
                                            // Only show program specific recording button if it hasn't finished yet
                                            item {
                                                Button(
                                                    onClick = {
                                                        if (isRecording) {
                                                            onCancelRecord.invoke(false)
                                                        } else {
                                                            onRecord.invoke(false)
                                                        }
                                                    },
                                                    modifier =
                                                        Modifier.ifElse(
                                                            !(dto.isSeries ?: false),
                                                            Modifier.focusRequester(
                                                                recordFocusRequester,
                                                            ),
                                                        ),
                                                ) {
                                                    Row(
                                                        horizontalArrangement =
                                                            Arrangement.spacedBy(
                                                                4.dp,
                                                            ),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                    ) {
                                                        if (isRecording) {
                                                            Icon(
                                                                imageVector = Icons.Default.Close,
                                                                contentDescription = null,
                                                                tint = Color.Red,
                                                            )
                                                        }
                                                        Text(
                                                            text =
                                                                if (isRecording) {
                                                                    stringResource(
                                                                        R.string.cancel_recording,
                                                                    )
                                                                } else {
                                                                    stringResource(
                                                                        R.string.record_program,
                                                                    )
                                                                },
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
            }
        }
    }
}
