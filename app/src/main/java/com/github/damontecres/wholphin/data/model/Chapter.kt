package com.github.damontecres.wholphin.data.model

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.extensions.ticks
import java.util.UUID
import kotlin.time.Duration

/**
 * Represents a chapter within a video
 */
data class Chapter(
    val itemId: UUID,
    val name: String?,
    val position: Duration,
    val tag: String?,
    val index: Int,
) {
    companion object {
        fun fromDto(dto: BaseItemDto): List<Chapter> =
            dto.chapters
                ?.mapIndexed { index, chapter ->
                    Chapter(
                        itemId = dto.id,
                        name = chapter.name,
                        position = chapter.startPositionTicks.ticks,
                        tag = chapter.imageTag,
                        index = index,
                    )
                }?.sortedBy { it.position }
                .orEmpty()
    }
}
