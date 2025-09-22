package com.github.damontecres.dolphin.data.model

import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import java.util.UUID

sealed interface DolphinModel {
    val id: UUID
    val name: String?
    val imageUrl: String?
}

fun convertModel(
    dto: BaseItemDto,
    api: ApiClient,
): DolphinModel =
    when (dto.type) {
        BaseItemKind.COLLECTION_FOLDER -> Library.fromDto(dto, api)

        // TODO
        BaseItemKind.VIDEO -> Video.fromDto(dto, api)
        BaseItemKind.SERIES -> Video.fromDto(dto, api)
        BaseItemKind.MOVIE -> Video.fromDto(dto, api)
        BaseItemKind.SEASON -> Video.fromDto(dto, api)
        BaseItemKind.EPISODE -> Video.fromDto(dto, api)
        else -> throw IllegalArgumentException("Unsupported item type: ${dto.type}")
    }
