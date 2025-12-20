package com.github.damontecres.wholphin.ui.detail.livetv

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.components.DotSeparatedRow
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.components.StreamLabel
import com.github.damontecres.wholphin.ui.roundMinutes
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.time.toKotlinDuration

@Composable
fun TvGuideHeader(
    program: TvProgram?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val now = LocalDateTime.now()
    val details =
        remember(program) {
            buildList {
                program?.let {
                    val differentDay = it.start.toLocalDate() != now.toLocalDate()
                    val time =
                        DateUtils.formatDateRange(
                            context,
                            it.start
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .epochSecond * 1000,
                            it.end
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .epochSecond * 1000,
                            DateUtils.FORMAT_SHOW_TIME or if (differentDay) DateUtils.FORMAT_SHOW_WEEKDAY else 0,
                        )
                    add(time)
                }
                if (program?.isFake == false) {
                    program
                        .duration
                        .roundMinutes
                        .toString()
                        .let(::add)
                    if (now.isAfter(program.start) && now.isBefore(program.end)) {
                        java.time.Duration
                            .between(now, program.end)
                            .toKotlinDuration()
                            .roundMinutes
                            .let { add("$it left") }
                    }
                    program.seasonEpisode?.let { "S${it.season} E${it.episode}" }?.let(::add)
                    program.officialRating?.let(::add)
                }
            }
        }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start,
        modifier = modifier,
    ) {
        Text(
            text = program?.name ?: program?.id.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(.75f),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(.6f),
        ) {
            program?.subtitle?.let {
                Text(
                    text = program.subtitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DotSeparatedRow(
                    texts = details,
                    communityRating = null,
                    criticRating = null,
                    textStyle = MaterialTheme.typography.titleSmall,
                    modifier = Modifier,
                )
                if (program?.isRepeat == true) {
                    StreamLabel(stringResource(R.string.live_tv_repeat))
                }
            }
            OverviewText(
                overview = program?.overview ?: "",
                maxLines = 3,
                onClick = {},
                enabled = false,
            )
        }
    }
}
