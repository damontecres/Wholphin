package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.data.CurrentUser
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.ibm.icu.impl.Assert
import io.mockk.MockKMatcherScope
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.UserDto
import org.junit.Assert.assertNotNull
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

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

/**
 * Create a simple [BaseItemDto] of the specified type
 */
fun item(
    type: BaseItemKind,
    id: UUID = UUID.randomUUID(),
    name: String = "Test $type",
    genres: List<NameGuidPair>? = null,
): BaseItemDto =
    BaseItemDto(
        id = id,
        type = type,
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

/**
 * Asserts that the object is not null and is type `T`
 *
 * Includes a [contract] to smart-cast the object to T
 */
@OptIn(ExperimentalContracts::class)
inline fun <reified T : Any> assertIs(obj: Any?) {
    contract {
        returns() implies (obj is T)
    }
    assertNotNull(obj)
    val result = T::class.isInstance(obj)
    if (!result) {
        Assert.fail("Expected type=${T::class.qualifiedName}, actual type= ${obj!!::class.qualifiedName} ")
    }
}
