package com.github.damontecres.dolphin.data.model

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

data class Video(
    override val id: UUID,
    override val name: String?,
    override val type: BaseItemKind,
    override val imageUrl: String?,
) : DolphinModel {
    companion object {
        fun fromDto(
            dto: BaseItemDto,
            api: ApiClient,
        ): Video =
            Video(
                id = dto.id,
                name = dto.name,
                type = dto.type,
                imageUrl = api.imageApi.getItemImageUrl(dto.id, ImageType.PRIMARY),
            )
    }
}
