package com.github.damontecres.wholphin.data.model

import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.UUID

class BaseItemSubtitleTest {
    private fun album(
        albumArtist: String? = null,
        artists: List<String>? = null,
        productionYear: Int? = null,
    ) = BaseItem(
        BaseItemDto(
            id = UUID.randomUUID(),
            type = BaseItemKind.MUSIC_ALBUM,
            name = "Test Album",
            albumArtist = albumArtist,
            artists = artists,
            productionYear = productionYear,
        ),
    )

    @Test
    fun albumPrefersAlbumArtist() {
        val item = album(albumArtist = "Album Artist", artists = listOf("A", "B"), productionYear = 2021)
        assertEquals("Album Artist - 2021", item.subtitle)
    }

    @Test
    fun albumFallsBackToArtists() {
        val item = album(artists = listOf("A", "B"), productionYear = 2021)
        assertEquals("A, B - 2021", item.subtitle)
    }

    @Test
    fun albumWithoutYear() {
        assertEquals("Album Artist", album(albumArtist = "Album Artist").subtitle)
    }

    @Test
    fun albumWithoutArtist() {
        assertEquals("2021", album(productionYear = 2021).subtitle)
        assertNull(album(artists = emptyList()).subtitle)
    }
}
