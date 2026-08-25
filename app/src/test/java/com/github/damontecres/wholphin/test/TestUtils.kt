package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import io.mockk.MockKMatcherScope
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.UserDto

fun MockKMatcherScope.nonBlankString() = match<String> { it.isNotNullOrBlank() }

/**
 * Create a simple [BaseItemDto] movie
 */
fun movie(
    id: UUID = UUID.randomUUID(),
    name: String = "Test Movie",
    genres: List<NameGuidPair>? = null,
): BaseItemDto =
    BaseItemDto(
        id = id,
        type = BaseItemKind.MOVIE,
        name = name,
        seriesId = null,
        genreItems = genres,
    )

/**
 * Create a simple [BaseItemDto] tv episode
 */
fun episode(
    id: UUID = UUID.randomUUID(),
    seriesId: UUID,
    name: String = "Test Episode",
    genres: List<NameGuidPair>? = null,
): BaseItemDto =
    BaseItemDto(
        id = id,
        type = BaseItemKind.EPISODE,
        name = name,
        seriesId = seriesId,
        genreItems = genres,
    )

/**
 * Create a simple [BaseItemDto] song
 */
fun song(
    id: UUID = UUID.randomUUID(),
    albumId: UUID? = null,
    name: String = "Test Song",
    genres: List<NameGuidPair>? = null,
): BaseItemDto =
    BaseItemDto(
        id = id,
        type = BaseItemKind.AUDIO,
        name = name,
        albumId = albumId,
        seriesId = null,
        genreItems = genres,
    )

/**
 * Create a simple [BaseItemDto] playlist
 */
fun playlist(
    id: UUID = UUID.randomUUID(),
    name: String = "Test Playlist",
    genres: List<NameGuidPair>? = null,
): BaseItemDto =
    BaseItemDto(
        id = id,
        type = BaseItemKind.PLAYLIST,
        name = name,
        seriesId = null,
        genreItems = genres,
    )

fun server(serverId: UUID = UUID.randomUUID()) = JellyfinServer(serverId, "test server", "http://localhost:8096", "10.11.11")

fun user(
    serverId: UUID,
    userId: UUID = UUID.randomUUID(),
) = JellyfinUser(
    rowId = 1,
    id = userId,
    serverId = serverId,
    name = "test-user",
    accessToken = "token",
    pin = null,
)

fun userDto(userId: UUID) =
    UserDto(
        id = userId,
        name = "test-user",
        serverName = "test server",
        hasPassword = true,
        hasConfiguredPassword = true,
        hasConfiguredEasyPassword = false,
    )

fun currentUser(
    serverId: UUID = UUID.randomUUID(),
    userId: UUID = UUID.randomUUID(),
) = CurrentUser(
    server(serverId),
    user(userId),
)
