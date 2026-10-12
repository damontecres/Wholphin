package com.github.damontecres.wholphin.data.model

import org.jellyfin.sdk.model.api.UserConfiguration
import org.jellyfin.sdk.model.api.UserDto
import org.jellyfin.sdk.model.api.UserPolicy
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import java.util.UUID

/**
 * Server side configuration for a user, similar to [UserDto] but without the timestamps allowing for more reliable equals
 */
data class ServerUserConfig(
    val id: UUID,
    val name: String? = null,
    val serverId: UUID? = null,
    val configuration: UserConfiguration? = null,
    val policy: UserPolicy? = null,
) {
    constructor(userDto: UserDto) : this(
        id = userDto.id,
        name = userDto.name,
        serverId = userDto.serverId?.toUUIDOrNull(),
        configuration = userDto.configuration,
        policy = userDto.policy,
    )

    val tvAccess: Boolean get() = policy?.enableLiveTvAccess == true
}
