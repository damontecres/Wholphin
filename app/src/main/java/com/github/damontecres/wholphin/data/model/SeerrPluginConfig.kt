package com.github.damontecres.wholphin.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface SeerrPluginLogin {
    @SerialName("None")
    @Serializable
    data object None : SeerrPluginLogin

    @SerialName("ApiKey")
    @Serializable
    data class ApiKey(
        val apiKey: String,
    ) : SeerrPluginLogin

    @SerialName("Local")
    @Serializable
    data class Local(
        val local: SeerrPluginLocalLogin,
    ) : SeerrPluginLogin
}

@Serializable
data class SeerrPluginLocalLogin(
    val username: String,
    val password: String,
)

@Serializable
data class SeerrPluginSettings(
    val version: Int = 1,
    val serverUrl: String? = null,
    val login: SeerrPluginLogin? = null,
)
