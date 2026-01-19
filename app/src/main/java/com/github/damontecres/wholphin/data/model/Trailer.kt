package com.github.damontecres.wholphin.data.model

sealed interface Trailer {
    val name: String
}

data class LocalTrailer(
    val baseItem: BaseItem,
) : Trailer {
    override val name: String
        get() = baseItem.name ?: ""
}

data class RemoteTrailer(
    override val name: String,
    val url: String,
    val subtitle: String?,
) : Trailer
