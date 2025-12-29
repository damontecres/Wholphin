package com.github.damontecres.wholphin.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import java.time.LocalDateTime

@Composable
fun MovieQuickDetails(
    dto: BaseItemDto?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val now by LocalClock.current.now
    val details =
        remember(dto, now) {
            buildList {
                dto?.productionYear?.let { add(it.toString()) }
                addRuntimeDetails(context, now, dto)
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

fun MutableList<String>.addRuntimeDetails(
    context: Context,
    now: LocalDateTime,
    dto: BaseItemDto?,
) {
    val runtime = dto?.runTimeTicks?.ticks
    runtime?.let { duration ->
        add(duration.roundMinutes.toString())
    }
    dto?.timeRemaining?.roundMinutes?.let {
        add("$it left")
    }
    (dto?.timeRemaining ?: runtime)?.let { remaining ->
        val endTimeStr = TimeFormatter.format(now.plusSeconds(remaining.inWholeSeconds))
        add(context.getString(R.string.ends_at, endTimeStr))
    }
}
