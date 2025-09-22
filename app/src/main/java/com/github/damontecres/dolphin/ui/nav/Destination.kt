package com.github.damontecres.dolphin.ui.nav

import androidx.navigation3.runtime.NavKey
import com.github.damontecres.dolphin.util.UuidSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed class Destination(
    val fullScreen: Boolean = false,
) : NavKey {
    @Serializable
    data object Setup : Destination(true)

    @Serializable
    data object Main : Destination()

    @Serializable
    data object Settings : Destination(true)

    @Serializable
    data object Search : Destination()

    @Serializable
    data class MediaItem(
        @Serializable(with = UuidSerializer::class) val itemId: UUID,
    ) : Destination()

    @Serializable
    data class Playback(
        val itemId: String,
        val positionMs: Long,
    ) : Destination(true)
}
