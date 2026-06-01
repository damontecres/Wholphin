package com.github.damontecres.wholphin.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class PagePosition {
    @SerialName("AfterHome")
    AFTER_HOME,

    @SerialName("AfterFavorites")
    AFTER_FAVORITES,

    @SerialName("AfterDiscover")
    AFTER_DISCOVER,

    @SerialName("AfterLibraries")
    AFTER_LIBRARIES,

    @SerialName("End")
    END,
}

@Serializable
data class PageSummary(
    val id: String,
    val title: String,
    val icon: String? = null,
    val position: PagePosition = PagePosition.AFTER_HOME,
)

@Serializable
data class PageConfig(
    val id: String,
    val title: String,
    val icon: String? = null,
    val position: PagePosition = PagePosition.AFTER_HOME,
    val rows: List<HomeRowConfig> = emptyList(),
)
