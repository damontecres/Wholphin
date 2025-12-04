package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.ui.roundMinutes
import com.github.damontecres.wholphin.ui.timeRemaining
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.extensions.ticks

@Composable
fun MovieQuickDetails(
    dto: BaseItemDto?,
    modifier: Modifier = Modifier,
) {
    val details =
        remember(dto) {
            buildList {
                dto?.productionYear?.let { add(it.toString()) }
                dto?.runTimeTicks?.ticks?.roundMinutes?.let {
                    add(it.toString())
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
