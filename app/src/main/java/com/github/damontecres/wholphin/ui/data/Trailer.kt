package com.github.damontecres.wholphin.ui.data

import com.github.damontecres.wholphin.data.model.BaseItem

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
) : Trailer
