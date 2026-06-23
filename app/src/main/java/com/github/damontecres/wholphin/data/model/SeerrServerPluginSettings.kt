package com.github.damontecres.wholphin.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class SeerrPluginLoginType {
    @SerialName("None")
    NONE,

    @SerialName("ApiKey")
    API_KEY,

    @SerialName("Jellyfin")
    JELLYFIN,

    @SerialName("Local")
    LOCAL,
}

@Serializable
data class SeerrPluginJellyfinLogin(
    val useCurrentUser: Boolean = false,
    val username: String? = null,
    val password: String? = null,
)

@Serializable
data class SeerrPluginLocalLogin(
    val username: String? = null,
    val password: String? = null,
)

@Serializable
data class SeerrPluginLogin(
    val type: SeerrPluginLoginType = SeerrPluginLoginType.NONE,
    val apiKey: String? = null,
    val jellyfin: SeerrPluginJellyfinLogin? = null,
    val local: SeerrPluginLocalLogin? = null,
)

@Serializable
data class SeerrPluginSettings(
    val version: Int = 1,
    val serverUrl: String? = null,
    val login: SeerrPluginLogin? = null,
)
