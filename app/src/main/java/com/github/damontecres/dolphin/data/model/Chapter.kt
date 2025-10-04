package com.github.damontecres.dolphin.data.model

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.extensions.ticks
import kotlin.time.Duration

data class Chapter(
    val name: String?,
    val position: Duration,
    val imageUrl: String?,
) {
    companion object {
        fun fromDto(
            dto: BaseItemDto,
            api: ApiClient,
        ): List<Chapter> =
            dto.chapters
                ?.mapIndexed { index, chapter ->
                    Chapter(
                        chapter.name,
                        chapter.startPositionTicks.ticks,
                        chapter.imageTag?.let {
                            api.imageApi.getItemImageUrl(
                                itemId = dto.id,
                                imageType = ImageType.CHAPTER,
                                tag = it,
                                imageIndex = index,
                            )
                        },
                    )
                }.orEmpty()
    }
}
