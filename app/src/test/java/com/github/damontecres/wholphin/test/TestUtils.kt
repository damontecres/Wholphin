package com.github.damontecres.wholphin.test

import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import io.mockk.MockKMatcherScope
import org.jellyfin.sdk.model.UUID
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.NameGuidPair

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
