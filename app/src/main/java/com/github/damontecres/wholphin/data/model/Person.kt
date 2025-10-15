package com.github.damontecres.wholphin.data.model

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind

data class Person(
    val id: UUID,
    val name: String?,
    val role: String?,
    val type: PersonKind,
    val imageUrl: String?,
) {
    companion object {
        fun fromDto(
            dto: BaseItemPerson,
            api: ApiClient,
        ): Person =
            Person(
                id = dto.id,
                name = dto.name,
                role = dto.role,
                type = dto.type,
                imageUrl = api.imageApi.getItemImageUrl(dto.id, ImageType.PRIMARY),
            )
    }
}
