package com.github.damontecres.dolphin.data.model

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.ImageType
import java.util.UUID

data class Library(
    override val id: UUID,
    override val name: String?,
    override val type: BaseItemKind,
    override val imageUrl: String?,
    val collectionType: CollectionType,
) : DolphinModel {
    companion object {
        fun fromDto(
            dto: BaseItemDto,
            api: ApiClient,
        ): Library =
            Library(
                id = dto.id,
                name = dto.name,
                type = dto.type,
                imageUrl = api.imageApi.getItemImageUrl(dto.id, ImageType.PRIMARY),
                collectionType = dto.collectionType ?: CollectionType.UNKNOWN,
            )
    }
}
