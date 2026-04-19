package com.github.damontecres.wholphin.data.model

/**
 * Represents a trailer for media
 */
sealed interface Trailer {
    val name: String
}

/**
 * A [Trailer] stored on the Jellyfin server
 */
data class LocalTrailer(
    val baseItem: BaseItem,
) : Trailer {
    override val name: String
        get() = baseItem.name ?: ""
}

/**
 * A [Trailer] available via a remote URL, such as YouTube
 */
data class RemoteTrailer(
    override val name: String,
    val url: String,
    val subtitle: String?,
) : Trailer
