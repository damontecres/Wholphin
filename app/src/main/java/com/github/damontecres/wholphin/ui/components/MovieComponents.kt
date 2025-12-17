package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.TimeFormatter
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.timeRemaining
import com.github.damontecres.wholphin.ui.util.LocalClock
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.extensions.ticks

@Composable
fun MovieQuickDetails(
    dto: BaseItemDto?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val now = LocalClock.current.now
    val details =
        remember(dto, now) {
            buildList {
                dto?.productionYear?.let { add(it.toString()) }
                dto?.runTimeTicks?.ticks?.roundMinutes?.let { duration ->
                    add(duration.toString())
                    val endTime = now.toLocalTime().plusSeconds(duration.inWholeSeconds)
                    val endTimeStr = TimeFormatter.format(endTime)
                    add(context.getString(R.string.ends_at, endTimeStr))
                }
                dto?.timeRemaining?.roundMinutes?.let {
                    add("$it left")
                }
                dto?.officialRating?.let(::add)
            }
        }

    DotSeparatedRow(
        texts = details,
        communityRating = dto?.communityRating,
        criticRating = dto?.criticRating,
        textStyle = MaterialTheme.typography.titleSmall,
        modifier = modifier,
    )
}
