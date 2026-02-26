package com.github.damontecres.wholphin.data.model

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PersonKind

@Stable
data class Person(
    val id: UUID,
    val name: String?,
    val role: String?,
    val type: PersonKind,
    val imageUrl: String?,
    val favorite: Boolean,
) {
    companion object {
        fun fromDto(
            context: Context,
            dto: BaseItemPerson,
            api: ApiClient,
        ): Person =
            Person(
                id = dto.id,
                name = dto.name,
                role = personRole(context, dto.role, dto.type),
                type = dto.type,
                imageUrl = api.imageApi.getItemImageUrl(dto.id, ImageType.PRIMARY),
                favorite = false,
            )

        fun fromDto(
            context: Context,
            dto: BaseItemPerson,
            favorite: Boolean,
            api: ApiClient,
        ): Person =
            Person(
                id = dto.id,
                name = dto.name,
                role = personRole(context, dto.role, dto.type),
                type = dto.type,
                imageUrl = api.imageApi.getItemImageUrl(dto.id, ImageType.PRIMARY),
                favorite = favorite,
            )
    }
}

private fun personRole(
    context: Context,
    role: String?,
    type: PersonKind,
): String? =
    if (type == PersonKind.ACTOR || type == PersonKind.GUEST_STAR || type == PersonKind.UNKNOWN) {
        role
    } else if (role.equals(type.name, ignoreCase = true)) {
        type.stringRes?.let { context.getString(it) }
    } else if (role.isNotNullOrBlank() && role.lowercase().contains(type.name.lowercase())) {
        role
    } else {
        listOfNotNull(
            role?.takeIf { it.isNotNullOrBlank() },
            type.stringRes?.let { context.getString(it) },
        ).takeIf { it.isNotEmpty() }?.joinToString(" - ")
    }

@get:StringRes
private val PersonKind.stringRes: Int?
    get() =
        when (this) {
            PersonKind.UNKNOWN -> R.string.unknown
            PersonKind.ACTOR -> R.string.actor
            PersonKind.DIRECTOR -> R.string.director
            PersonKind.COMPOSER -> R.string.composer
            PersonKind.WRITER -> R.string.writer
            PersonKind.GUEST_STAR -> R.string.guest_star
            PersonKind.PRODUCER -> R.string.producer
            PersonKind.CONDUCTOR -> R.string.conductor
            PersonKind.LYRICIST -> R.string.lyricist
            PersonKind.ARRANGER -> R.string.arranger
            PersonKind.ENGINEER -> R.string.engineer
            PersonKind.MIXER -> R.string.mixer
            PersonKind.REMIXER -> R.string.mixer
            PersonKind.CREATOR -> R.string.creator
            PersonKind.ARTIST -> R.string.artist
            PersonKind.ALBUM_ARTIST -> R.string.artist
            else -> null
        }
